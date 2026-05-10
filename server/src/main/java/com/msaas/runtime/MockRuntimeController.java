package com.msaas.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msaas.instance.InstanceMode;
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

    public MockRuntimeController(
            MockRuntimeRegistry registry,
            RouteMatcher routeMatcher,
            RequestLogRepository requestLogRepository,
            ObjectMapper objectMapper,
            SecretHashService secretHashService,
            LogRedactor logRedactor
    ) {
        this.registry = registry;
        this.routeMatcher = routeMatcher;
        this.requestLogRepository = requestLogRepository;
        this.objectMapper = objectMapper;
        this.secretHashService = secretHashService;
        this.logRedactor = logRedactor;
    }

    @RequestMapping("/mock/{token}/**")
    public ResponseEntity<Object> handle(
            @PathVariable String token,
            HttpServletRequest request,
            @RequestBody(required = false) String requestBody
    ) {
        Optional<RuntimeSlot> maybeSlot = registry.findByToken(token);
        if (maybeSlot.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Mock instance not found"));
        }

        RuntimeSlot slot = maybeSlot.get();
        String mockPath = mockPath(token, request);
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

            Optional<RouteMatch> match = routeMatcher.match(
                    slot.getContract(),
                    request.getMethod(),
                    mockPath,
                    queryParameters(request),
                    headers(request),
                    requestBody
            );
            if (match.isEmpty()) {
                response = ResponseEntity.status(404).body(Map.of("error", "No matching route", "path", mockPath));
                log.setMatched(false);
                log.setError("No matching route");
            } else {
                log.setMatched(true);
                response = respond(slot, match.get(), request.getMethod(), requestBody, responseDirective(request));
            }
            return response;
        } finally {
            int status = response == null ? 500 : response.getStatusCode().value();
            log.setResponseStatus(status);
            log.setLatencyMs(Duration.between(startedAt, Instant.now()).toMillis());
            requestLogRepository.save(log);
        }
    }

    private boolean validApiKey(RuntimeSlot slot, HttpServletRequest request) {
        if (!slot.isRequireApiKey()) {
            return true;
        }
        return secretHashService.matches(request.getHeader("X-Mock-Api-Key"), slot.getApiKeyHash());
    }

    private ResponseEntity<Object> respond(RuntimeSlot slot, RouteMatch match, String method, String requestBody, ResponseDirective directive) {
        if (slot.getMode() == InstanceMode.STATEFUL) {
            Optional<ResponseEntity<Object>> stateful = statefulResponse(slot, match, method, requestBody, directive);
            if (stateful.isPresent()) {
                return stateful.get();
            }
        }
        return responseFromDefinition(selectResponse(match.route(), directive.statusCode()), directive);
    }

    private Optional<ResponseEntity<Object>> statefulResponse(RuntimeSlot slot, RouteMatch match, String method, String requestBody, ResponseDirective directive) {
        MockRoute route = match.route();
        String upperMethod = method.toUpperCase(Locale.ROOT);
        String collectionKey = collectionKey(route.getPathTemplate());
        ConcurrentMap<String, Object> collection = slot.collection(collectionKey);
        Optional<String> maybeId = resourceId(match);

        if ("POST".equals(upperMethod) && maybeId.isEmpty()) {
            Map<String, Object> item = objectBodyOrDefault(requestBody, route.defaultResponse().getBody());
            String id = String.valueOf(item.getOrDefault("id", UUID.randomUUID().toString()));
            item.putIfAbsent("id", id);
            collection.put(id, item);
            return Optional.of(responseWithBody(route, directive.statusCode().orElse(201), item, directive));
        }

        if ("GET".equals(upperMethod) && maybeId.isPresent()) {
            Object item = collection.get(maybeId.get());
            if (item != null) {
                return Optional.of(responseWithBody(route, directive.statusCode().orElse(200), item, directive));
            }
            return Optional.empty();
        }

        if ("GET".equals(upperMethod) && maybeId.isEmpty() && !collection.isEmpty()) {
            return Optional.of(responseWithBody(route, directive.statusCode().orElse(200), new ArrayList<>(collection.values()), directive));
        }

        if (("PUT".equals(upperMethod) || "PATCH".equals(upperMethod)) && maybeId.isPresent()) {
            Map<String, Object> item = objectBodyOrDefault(requestBody, route.defaultResponse().getBody());
            item.putIfAbsent("id", maybeId.get());
            collection.put(maybeId.get(), item);
            return Optional.of(responseWithBody(route, directive.statusCode().orElse(200), item, directive));
        }

        if ("DELETE".equals(upperMethod) && maybeId.isPresent()) {
            collection.remove(maybeId.get());
            MockResponseDefinition definition = preferredResponse(route, directive.statusCode().orElse(204));
            if (definition.getStatusCode() == 204 || definition.getBody() == null) {
                delay(effectiveDelayMs(definition, directive));
                return Optional.of(ResponseEntity.status(204).build());
            }
            return Optional.of(responseFromDefinition(definition, directive));
        }

        return Optional.empty();
    }

    private ResponseEntity<Object> responseWithBody(MockRoute route, int preferredStatus, Object body, ResponseDirective directive) {
        MockResponseDefinition definition = preferredResponse(route, preferredStatus);
        delay(effectiveDelayMs(definition, directive));
        HttpHeaders headers = headers(definition);
        return ResponseEntity.status(HttpStatusCode.valueOf(definition.getStatusCode()))
                .headers(headers)
                .body(body);
    }

    private ResponseEntity<Object> responseFromDefinition(MockResponseDefinition definition, ResponseDirective directive) {
        delay(effectiveDelayMs(definition, directive));
        HttpHeaders headers = headers(definition);
        return ResponseEntity.status(HttpStatusCode.valueOf(definition.getStatusCode()))
                .headers(headers)
                .body(definition.getBody());
    }

    private MockResponseDefinition selectResponse(MockRoute route, Optional<Integer> statusCode) {
        return statusCode.map(status -> preferredResponse(route, status)).orElseGet(route::defaultResponse);
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

    private String mockPath(String token, HttpServletRequest request) {
        String uri = request.getRequestURI();
        String prefix = "/mock/" + token;
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
        return new ResponseDirective(status, delay);
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

    private record ResponseDirective(Optional<Integer> statusCode, Optional<Long> delayMs) {
    }
}
