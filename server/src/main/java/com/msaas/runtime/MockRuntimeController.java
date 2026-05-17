package com.msaas.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msaas.instance.InstanceMode;
import com.msaas.instance.MockScenario;
import com.msaas.log.LogRedactor;
import com.msaas.log.RequestLog;
import com.msaas.log.RequestLogRepository;
import com.msaas.security.SecretHashService;
import com.msaas.spec.contract.MockResponseDefinition;
import com.msaas.spec.contract.MockRoute;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

@RestController
public class MockRuntimeController {
    private static final int MAX_REQUEST_DELAY_MS = 10_000;

    private final MockRuntimeRegistry registry;
    private final RouteMatcher routeMatcher;
    private final RequestLogRepository requestLogRepository;
    private final ObjectMapper objectMapper;
    private final SecretHashService secretHashService;
    private final LogRedactor logRedactor;
    private final MockRateLimiter rateLimiter;
    private final TemplateRenderer templateRenderer;
    private final MockStateStore stateStore;
    private final RuntimePlaneService runtimePlaneService;
    private final MockGatewayService gatewayService;

    public MockRuntimeController(
            MockRuntimeRegistry registry,
            RouteMatcher routeMatcher,
            RequestLogRepository requestLogRepository,
            ObjectMapper objectMapper,
            SecretHashService secretHashService,
            LogRedactor logRedactor,
            MockRateLimiter rateLimiter,
            TemplateRenderer templateRenderer,
            MockStateStore stateStore,
            RuntimePlaneService runtimePlaneService,
            MockGatewayService gatewayService
    ) {
        this.registry = registry;
        this.routeMatcher = routeMatcher;
        this.requestLogRepository = requestLogRepository;
        this.objectMapper = objectMapper;
        this.secretHashService = secretHashService;
        this.logRedactor = logRedactor;
        this.rateLimiter = rateLimiter;
        this.templateRenderer = templateRenderer;
        this.stateStore = stateStore;
        this.runtimePlaneService = runtimePlaneService;
        this.gatewayService = gatewayService;
    }

    @RequestMapping("/mock/{token}/**")
    public ResponseEntity<?> handle(
            @PathVariable String token,
            HttpServletRequest request,
            @RequestBody(required = false) String requestBody
    ) {
        if (runtimePlaneService.gatewayEnabled()) {
            return gatewayService.proxy(token, request, requestBody);
        }
        if (!runtimePlaneService.localExecutionEnabled()) {
            return ResponseEntity.status(404).body(Map.of("error", "Mock runtime is not available on this node"));
        }
        return execute(token, request, requestBody, "/mock/" + token);
    }

    @RequestMapping("/internal/runtime/mock/{token}/**")
    public ResponseEntity<?> handleInternal(
            @PathVariable String token,
            HttpServletRequest request,
            @RequestBody(required = false) String requestBody
    ) {
        if (!runtimePlaneService.internalSecretMatches(request.getHeader("X-MSaaS-Internal-Secret"))) {
            return ResponseEntity.status(403).body(Map.of("error", "Invalid internal runtime secret"));
        }
        if (!runtimePlaneService.localExecutionEnabled()) {
            return ResponseEntity.status(404).body(Map.of("error", "Mock runtime is not available on this node"));
        }
        return execute(token, request, requestBody, "/internal/runtime/mock/" + token);
    }

    private ResponseEntity<Object> execute(String token, HttpServletRequest request, String requestBody, String pathPrefix) {
        Optional<RuntimeSlot> maybeSlot = registry.findByToken(token);
        if (maybeSlot.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Mock instance not found"));
        }

        RuntimeSlot slot = maybeSlot.get();
        slot.beginRequest();
        String mockPath = mockPath(pathPrefix, request);
        Instant startedAt = Instant.now();
        RequestLog log = new RequestLog(slot.getProjectId(), slot.getInstanceId(), request.getMethod(), mockPath, logRedactor.redactQueryString(request.getQueryString()));
        log.setRequestHeaders(logRedactor.redactHeaders(request));
        log.setRequestBody(logRedactor.redactBody(requestBody));

        ResponseEntity<Object> response = null;
        try {
            if (!validApiKey(slot, request)) {
                response = ResponseEntity.status(401).body(Map.of("error", "Mock API key is required"));
                log.setMatched(false);
                log.setError("Invalid mock API key");
                return response;
            }

            MockRateLimiter.RateLimitResult rateLimit = rateLimiter.tryAcquire(slot, token, clientIp(request));
            if (!rateLimit.allowed()) {
                response = ResponseEntity.status(429)
                        .header("Retry-After", String.valueOf(rateLimit.retryAfterSeconds()))
                        .body(Map.of("error", "Rate limit exceeded", "retryAfterSeconds", rateLimit.retryAfterSeconds()));
                log.setMatched(false);
                log.setError("Rate limit exceeded");
                return response;
            }

            ResponseDirective directive = responseDirective(request);
            Map<String, List<String>> queryParameters = queryParameters(request);
            Map<String, String> headers = headers(request);
            Optional<RouteMatch> match = routeMatcher.match(
                    slot.getContract(),
                    request.getMethod(),
                    mockPath,
                    queryParameters,
                    headers,
                    requestBody
            );
            if (match.isEmpty()) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("error", "No matching route");
                body.put("path", mockPath);
                if (directive.debug()) {
                    body.put("hints", routeMatcher.mismatchHints(slot.getContract(), request.getMethod(), mockPath, queryParameters, headers, requestBody));
                }
                response = ResponseEntity.status(404).body(body);
                log.setMatched(false);
                log.setError("No matching route");
            } else {
                log.setMatched(true);
                response = respond(slot, match.get(), request.getMethod(), requestBody, queryParameters, headers, directive);
            }
            return response;
        } finally {
            try {
                int status = response == null ? 500 : response.getStatusCode().value();
                log.setResponseStatus(status);
                log.setLatencyMs(Duration.between(startedAt, Instant.now()).toMillis());
                requestLogRepository.save(log);
            } finally {
                slot.endRequest();
            }
        }
    }

    private boolean validApiKey(RuntimeSlot slot, HttpServletRequest request) {
        if (!slot.isRequireApiKey()) {
            return true;
        }
        return secretHashService.matches(request.getHeader("X-Mock-Api-Key"), slot.getApiKeyHash());
    }

    private ResponseEntity<Object> respond(
            RuntimeSlot slot,
            RouteMatch match,
            String method,
            String requestBody,
            Map<String, List<String>> queryParameters,
            Map<String, String> headers,
            ResponseDirective directive
    ) {
        Optional<ResponseEntity<Object>> scenario = scenarioResponse(slot, match, requestBody, queryParameters, headers, directive);
        if (scenario.isPresent()) {
            return scenario.get();
        }
        if (slot.getMode() == InstanceMode.STATEFUL) {
            Optional<ResponseEntity<Object>> stateful = statefulResponse(slot, match, method, requestBody, queryParameters, headers, directive);
            if (stateful.isPresent()) {
                return stateful.get();
            }
        }
        return responseFromDefinition(selectResponse(match.route(), directive), match, requestBody, queryParameters, headers, directive);
    }

    private Optional<ResponseEntity<Object>> scenarioResponse(
            RuntimeSlot slot,
            RouteMatch match,
            String requestBody,
            Map<String, List<String>> queryParameters,
            Map<String, String> headers,
            ResponseDirective directive
    ) {
        return slot.getScenarios().stream()
                .filter(MockScenario::isEnabled)
                .filter(scenario -> scenarioMatches(scenario, match))
                .sorted(java.util.Comparator.comparingInt(MockScenario::getPriority).reversed())
                .findFirst()
                .map(scenario -> {
                    Object body = templateRenderer.render(scenario.getBody(), match.variables(), queryParameters, headers, requestBody);
                    long delayMs = directive.delayMs().orElse(scenario.getDelayMs());
                    delay(delayMs);
                    HttpHeaders responseHeaders = new HttpHeaders();
                    responseHeaders.setContentType(parseMediaType(scenario.getContentType()));
                    scenario.getHeaders().forEach((name, value) -> responseHeaders.set(name, String.valueOf(templateRenderer.render(value, match.variables(), queryParameters, headers, requestBody))));
                    int status = directive.statusCode().orElse(scenario.getStatusCode() == null ? match.route().getDefaultStatusCode() : scenario.getStatusCode());
                    return ResponseEntity.status(HttpStatusCode.valueOf(status)).headers(responseHeaders).body(body);
                });
    }

    private boolean scenarioMatches(MockScenario scenario, RouteMatch match) {
        MockRoute route = match.route();
        if (scenario.getOperationId() != null && route.getOperationId() != null) {
            return scenario.getOperationId().equals(route.getOperationId());
        }
        boolean methodMatches = scenario.getMethod() == null || scenario.getMethod().equalsIgnoreCase(route.getMethod());
        boolean pathMatches = scenario.getPathTemplate() == null || scenario.getPathTemplate().equals(route.getPathTemplate());
        return methodMatches && pathMatches;
    }

    private Optional<ResponseEntity<Object>> statefulResponse(
            RuntimeSlot slot,
            RouteMatch match,
            String method,
            String requestBody,
            Map<String, List<String>> queryParameters,
            Map<String, String> headers,
            ResponseDirective directive
    ) {
        MockRoute route = match.route();
        String upperMethod = method.toUpperCase(Locale.ROOT);
        String collectionKey = collectionKey(route.getPathTemplate());
        Optional<String> maybeId = resourceId(match);

        if ("POST".equals(upperMethod) && maybeId.isEmpty()) {
            Map<String, Object> item = objectBodyOrDefault(requestBody, route.defaultResponse().getBody());
            String id = String.valueOf(item.getOrDefault("id", UUID.randomUUID().toString()));
            item.putIfAbsent("id", id);
            stateStore.put(slot, collectionKey, id, item);
            return Optional.of(responseWithBody(route, directive.statusCode().orElse(201), item, match, requestBody, queryParameters, headers, directive));
        }

        if ("GET".equals(upperMethod) && maybeId.isPresent()) {
            Object item = stateStore.get(slot, collectionKey, maybeId.get());
            if (item != null) {
                return Optional.of(responseWithBody(route, directive.statusCode().orElse(200), item, match, requestBody, queryParameters, headers, directive));
            }
            return Optional.empty();
        }

        if ("GET".equals(upperMethod) && maybeId.isEmpty() && !stateStore.isEmpty(slot, collectionKey)) {
            return Optional.of(responseWithBody(route, directive.statusCode().orElse(200), stateStore.values(slot, collectionKey), match, requestBody, queryParameters, headers, directive));
        }

        if (("PUT".equals(upperMethod) || "PATCH".equals(upperMethod)) && maybeId.isPresent()) {
            Map<String, Object> item = objectBodyOrDefault(requestBody, route.defaultResponse().getBody());
            item.putIfAbsent("id", maybeId.get());
            stateStore.put(slot, collectionKey, maybeId.get(), item);
            return Optional.of(responseWithBody(route, directive.statusCode().orElse(200), item, match, requestBody, queryParameters, headers, directive));
        }

        if ("DELETE".equals(upperMethod) && maybeId.isPresent()) {
            stateStore.remove(slot, collectionKey, maybeId.get());
            MockResponseDefinition definition = preferredResponse(route, directive.statusCode().orElse(204));
            if (definition.getStatusCode() == 204 || definition.getBody() == null) {
                delay(effectiveDelayMs(definition, directive));
                return Optional.of(ResponseEntity.status(204).build());
            }
            return Optional.of(responseFromDefinition(definition, match, requestBody, queryParameters, headers, directive));
        }

        return Optional.empty();
    }

    private ResponseEntity<Object> responseWithBody(
            MockRoute route,
            int preferredStatus,
            Object body,
            RouteMatch match,
            String requestBody,
            Map<String, List<String>> queryParameters,
            Map<String, String> requestHeaders,
            ResponseDirective directive
    ) {
        MockResponseDefinition definition = preferredResponse(route, preferredStatus);
        delay(effectiveDelayMs(definition, directive));
        HttpHeaders responseHeaders = headers(definition);
        return ResponseEntity.status(HttpStatusCode.valueOf(definition.getStatusCode()))
                .headers(responseHeaders)
                .body(templateRenderer.render(body, match.variables(), queryParameters, requestHeaders, requestBody));
    }

    private ResponseEntity<Object> responseFromDefinition(
            MockResponseDefinition definition,
            RouteMatch match,
            String requestBody,
            Map<String, List<String>> queryParameters,
            Map<String, String> requestHeaders,
            ResponseDirective directive
    ) {
        delay(effectiveDelayMs(definition, directive));
        HttpHeaders responseHeaders = headers(definition);
        return ResponseEntity.status(HttpStatusCode.valueOf(definition.getStatusCode()))
                .headers(responseHeaders)
                .body(templateRenderer.render(definition.getBody(), match.variables(), queryParameters, requestHeaders, requestBody));
    }

    private MockResponseDefinition selectResponse(MockRoute route, ResponseDirective directive) {
        MockResponseDefinition definition = directive.statusCode().map(status -> preferredResponse(route, status)).orElseGet(route::defaultResponse);
        definition = selectContent(definition, directive.acceptHeader());
        if (directive.exampleName().isPresent()) {
            Object example = definition.getExamples().get(directive.exampleName().get());
            if (example != null) {
                return definition.withBody(example);
            }
        }
        return definition;
    }

    private MockResponseDefinition selectContent(MockResponseDefinition definition, Optional<String> acceptHeader) {
        Map<String, MockResponseDefinition.ResponseContent> contents = definition.getContents();
        if (contents.size() <= 1 || acceptHeader.isEmpty()) {
            return definition;
        }
        try {
            List<MediaType> accepted = MediaType.parseMediaTypes(acceptHeader.get());
            accepted.sort(this::compareAcceptedMediaTypes);
            for (MediaType accept : accepted) {
                for (Map.Entry<String, MockResponseDefinition.ResponseContent> entry : contents.entrySet()) {
                    MediaType candidate = parseMediaType(entry.getKey());
                    if (accept.includes(candidate) || accept.isCompatibleWith(candidate)) {
                        return definition.withContent(entry.getKey(), entry.getValue());
                    }
                }
            }
        } catch (RuntimeException ignored) {
            return definition;
        }
        return definition;
    }

    private int compareAcceptedMediaTypes(MediaType left, MediaType right) {
        int quality = Double.compare(right.getQualityValue(), left.getQualityValue());
        if (quality != 0) {
            return quality;
        }
        return Integer.compare(mediaTypeSpecificity(right), mediaTypeSpecificity(left));
    }

    private int mediaTypeSpecificity(MediaType mediaType) {
        int score = 0;
        if (!mediaType.isWildcardType()) {
            score += 2;
        }
        if (!mediaType.isWildcardSubtype()) {
            score += 1;
        }
        return score;
    }

    private MockResponseDefinition preferredResponse(MockRoute route, int preferredStatus) {
        MockResponseDefinition exact = route.getResponses().get(String.valueOf(preferredStatus));
        if (exact != null) {
            return exact;
        }
        MockResponseDefinition copy = route.defaultResponse();
        return new MockResponseDefinition(preferredStatus, copy.getContentType(), copy.getBody(), copy.getDelayMs());
    }

    private HttpHeaders headers(MockResponseDefinition definition) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(parseMediaType(definition.getContentType()));
        definition.getHeaders().forEach(headers::set);
        return headers;
    }

    private Map<String, Object> objectBodyOrDefault(String requestBody, Object fallback) {
        if (requestBody != null && !requestBody.isBlank()) {
            try {
                return objectMapper.readValue(requestBody, new TypeReference<LinkedHashMap<String, Object>>() {
                });
            } catch (JsonProcessingException ignored) {
                return new LinkedHashMap<>(Map.of("value", requestBody));
            }
        }
        if (fallback instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, value) -> copy.put(String.valueOf(key), value));
            return copy;
        }
        return new LinkedHashMap<>();
    }

    private Optional<String> resourceId(RouteMatch match) {
        if (match.variables().isEmpty()) {
            return Optional.empty();
        }
        String lastValue = null;
        for (String value : match.variables().values()) {
            lastValue = value;
        }
        return Optional.ofNullable(lastValue);
    }

    private String collectionKey(String pathTemplate) {
        List<String> segments = segments(pathTemplate);
        if (!segments.isEmpty()) {
            String last = segments.getLast();
            if (last.startsWith("{") && last.endsWith("}")) {
                segments = segments.subList(0, segments.size() - 1);
            }
        }
        return "/" + String.join("/", segments);
    }

    private List<String> segments(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return List.of();
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        normalized = normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        if (normalized.isBlank()) {
            return List.of();
        }
        return new ArrayList<>(List.of(normalized.split("/")));
    }

    private String mockPath(String prefix, HttpServletRequest request) {
        String uri = request.getRequestURI();
        String path = uri.length() <= prefix.length() ? "/" : uri.substring(prefix.length());
        return path.isBlank() ? "/" : path;
    }

    private Map<String, List<String>> queryParameters(HttpServletRequest request) {
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        request.getParameterMap().forEach((name, values) -> parameters.put(name, List.of(values)));
        return parameters;
    }

    private Map<String, String> headers(HttpServletRequest request) {
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return Map.of();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        Collections.list(names).forEach(name -> headers.put(name, request.getHeader(name)));
        return headers;
    }

    private ResponseDirective responseDirective(HttpServletRequest request) {
        Optional<Integer> status = parseInt(firstPresent(request.getParameter("__status"), request.getHeader("X-Mock-Status")))
                .filter(value -> value >= 100 && value <= 599);
        Optional<Long> delay = parseLong(firstPresent(request.getParameter("__delay"), request.getHeader("X-Mock-Delay-Ms")))
                .map(value -> Math.max(0, Math.min(value, MAX_REQUEST_DELAY_MS)));
        Optional<String> example = Optional.ofNullable(firstPresent(request.getParameter("__example"), request.getHeader("X-Mock-Example")))
                .filter(value -> !value.isBlank());
        boolean debug = "true".equalsIgnoreCase(firstPresent(request.getParameter("__debug"), request.getHeader("X-Mock-Debug")));
        Optional<String> acceptHeader = Optional.ofNullable(request.getHeader("Accept")).filter(value -> !value.isBlank());
        return new ResponseDirective(status, delay, example, debug, acceptHeader);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private long effectiveDelayMs(MockResponseDefinition definition, ResponseDirective directive) {
        return directive.delayMs().orElse(definition.getDelayMs());
    }

    private Optional<Integer> parseInt(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private Optional<Long> parseLong(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private String firstPresent(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private MediaType parseMediaType(String value) {
        if (value == null || value.isBlank()) {
            return MediaType.APPLICATION_JSON;
        }
        try {
            return MediaType.parseMediaType(value);
        } catch (RuntimeException ignored) {
            return MediaType.APPLICATION_JSON;
        }
    }

    private void delay(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private record ResponseDirective(
            Optional<Integer> statusCode,
            Optional<Long> delayMs,
            Optional<String> exampleName,
            boolean debug,
            Optional<String> acceptHeader
    ) {
    }
}
