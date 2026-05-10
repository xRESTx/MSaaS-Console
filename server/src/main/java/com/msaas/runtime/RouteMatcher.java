package com.msaas.runtime;

import com.msaas.spec.contract.MockRoute;
import com.msaas.spec.contract.NormalizedContract;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class RouteMatcher {

    public Optional<RouteMatch> match(NormalizedContract contract, String method, String path) {
        return match(contract, method, path, Map.of(), Map.of(), null);
    }

    public Optional<RouteMatch> match(
            NormalizedContract contract,
            String method,
            String path,
            Map<String, List<String>> queryParameters,
            Map<String, String> headers,
            String requestBody
    ) {
        if (contract == null || contract.getRoutes() == null) {
            return Optional.empty();
        }
        Map<String, String> normalizedHeaders = normalizeHeaders(headers);
        return contract.getRoutes().stream()
                .filter(route -> route.getMethod().equalsIgnoreCase(method))
                .map(route -> tryMatch(route, path, queryParameters, normalizedHeaders, requestBody))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private Optional<RouteMatch> tryMatch(
            MockRoute route,
            String path,
            Map<String, List<String>> queryParameters,
            Map<String, String> headers,
            String requestBody
    ) {
        List<String> templateSegments = segments(route.getPathTemplate());
        List<String> pathSegments = segments(path);
        if (templateSegments.size() != pathSegments.size()) {
            return Optional.empty();
        }

        Map<String, String> variables = new LinkedHashMap<>();
        for (int i = 0; i < templateSegments.size(); i++) {
            String template = templateSegments.get(i);
            String actual = pathSegments.get(i);
            if (template.startsWith("{") && template.endsWith("}")) {
                variables.put(template.substring(1, template.length() - 1), decode(actual));
            } else if (!template.equals(actual)) {
                return Optional.empty();
            }
        }
        if (!hasRequiredQueryParameters(route, queryParameters)
                || !hasRequiredHeaders(route, headers)
                || !hasRequiredBody(route, requestBody)) {
            return Optional.empty();
        }
        return Optional.of(new RouteMatch(route, variables));
    }

    private boolean hasRequiredQueryParameters(MockRoute route, Map<String, List<String>> queryParameters) {
        return route.getRequiredQueryParameters().stream()
                .allMatch(name -> queryParameters.containsKey(name)
                        && queryParameters.get(name) != null
                        && !queryParameters.get(name).isEmpty());
    }

    private boolean hasRequiredHeaders(MockRoute route, Map<String, String> headers) {
        return route.getRequiredHeaderParameters().stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .allMatch(headers::containsKey);
    }

    private boolean hasRequiredBody(MockRoute route, String requestBody) {
        return !route.isRequestBodyRequired() || (requestBody != null && !requestBody.isBlank());
    }

    private Map<String, String> normalizeHeaders(Map<String, String> headers) {
        Map<String, String> normalized = new LinkedHashMap<>();
        headers.forEach((name, value) -> normalized.put(name.toLowerCase(Locale.ROOT), value));
        return normalized;
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
        return List.of(normalized.split("/"));
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
