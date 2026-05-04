package com.msaas.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msaas.instance.InstanceMode;
import com.msaas.log.RequestLog;
import com.msaas.log.RequestLogRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

@RestController
public class MockRuntimeController {
    private final MockRuntimeRegistry registry;
    private final RouteMatcher routeMatcher;
    private final RequestLogRepository requestLogRepository;
    private final ObjectMapper objectMapper;

    public MockRuntimeController(
            MockRuntimeRegistry registry,
            RouteMatcher routeMatcher,
            RequestLogRepository requestLogRepository,
            ObjectMapper objectMapper
    ) {
        this.registry = registry;
        this.routeMatcher = routeMatcher;
        this.requestLogRepository = requestLogRepository;
        this.objectMapper = objectMapper;
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
        RequestLog log = new RequestLog(slot.getProjectId(), slot.getInstanceId(), request.getMethod(), mockPath, request.getQueryString());
        log.setRequestBody(requestBody);

        ResponseEntity<Object> response = null;
        try {
            Optional<RouteMatch> match = routeMatcher.match(slot.getContract(), request.getMethod(), mockPath);
            if (match.isEmpty()) {
                response = ResponseEntity.status(404).body(Map.of("error", "No matching route", "path", mockPath));
                log.setMatched(false);
                log.setError("No matching route");
            } else {
                log.setMatched(true);
                response = respond(slot, match.get(), request.getMethod(), requestBody);
            }
            return response;
        } finally {
            int status = response == null ? 500 : response.getStatusCode().value();
            log.setResponseStatus(status);
            log.setLatencyMs(Duration.between(startedAt, Instant.now()).toMillis());
            requestLogRepository.save(log);
        }
    }

    private ResponseEntity<Object> respond(RuntimeSlot slot, RouteMatch match, String method, String requestBody) {
        if (slot.getMode() == InstanceMode.STATEFUL) {
            Optional<ResponseEntity<Object>> stateful = statefulResponse(slot, match, method, requestBody);
            if (stateful.isPresent()) {
                return stateful.get();
            }
        }
        return responseFromDefinition(match.route().defaultResponse());
    }

    private Optional<ResponseEntity<Object>> statefulResponse(RuntimeSlot slot, RouteMatch match, String method, String requestBody) {
        MockRoute route = match.route();
        String upperMethod = method.toUpperCase();
        String collectionKey = collectionKey(route.getPathTemplate());
        ConcurrentMap<String, Object> collection = slot.collection(collectionKey);
        Optional<String> maybeId = resourceId(match);

        if ("POST".equals(upperMethod) && maybeId.isEmpty()) {
            Map<String, Object> item = objectBodyOrDefault(requestBody, route.defaultResponse().getBody());
            String id = String.valueOf(item.getOrDefault("id", UUID.randomUUID().toString()));
            item.putIfAbsent("id", id);
            collection.put(id, item);
            return Optional.of(responseWithBody(route, 201, item));
        }

        if ("GET".equals(upperMethod) && maybeId.isPresent()) {
            Object item = collection.get(maybeId.get());
            if (item != null) {
                return Optional.of(responseWithBody(route, 200, item));
            }
            return Optional.empty();
        }

        if ("GET".equals(upperMethod) && maybeId.isEmpty() && !collection.isEmpty()) {
            return Optional.of(responseWithBody(route, 200, new ArrayList<>(collection.values())));
        }

        if (("PUT".equals(upperMethod) || "PATCH".equals(upperMethod)) && maybeId.isPresent()) {
            Map<String, Object> item = objectBodyOrDefault(requestBody, route.defaultResponse().getBody());
            item.putIfAbsent("id", maybeId.get());
            collection.put(maybeId.get(), item);
            return Optional.of(responseWithBody(route, 200, item));
        }

        if ("DELETE".equals(upperMethod) && maybeId.isPresent()) {
            collection.remove(maybeId.get());
            MockResponseDefinition definition = preferredResponse(route, 204);
            if (definition.getStatusCode() == 204 || definition.getBody() == null) {
                delay(definition.getDelayMs());
                ResponseEntity<Object> noContent = ResponseEntity.status(204).build();
                return Optional.of(noContent);
            }
            return Optional.of(responseFromDefinition(definition));
        }

        return Optional.empty();
    }

    private ResponseEntity<Object> responseWithBody(MockRoute route, int preferredStatus, Object body) {
        MockResponseDefinition definition = preferredResponse(route, preferredStatus);
        delay(definition.getDelayMs());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(parseMediaType(definition.getContentType()));
        return ResponseEntity.status(HttpStatusCode.valueOf(definition.getStatusCode()))
                .headers(headers)
                .body(body);
    }

    private ResponseEntity<Object> responseFromDefinition(MockResponseDefinition definition) {
        delay(definition.getDelayMs());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(parseMediaType(definition.getContentType()));
        return ResponseEntity.status(HttpStatusCode.valueOf(definition.getStatusCode()))
                .headers(headers)
                .body(definition.getBody());
    }

    private MockResponseDefinition preferredResponse(MockRoute route, int preferredStatus) {
        MockResponseDefinition exact = route.getResponses().get(String.valueOf(preferredStatus));
        if (exact != null) {
            return exact;
        }
        if (preferredStatus == 201) {
            MockResponseDefinition created = route.getResponses().get("200");
            if (created != null) {
                return created;
            }
        }
        return route.defaultResponse();
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

}
