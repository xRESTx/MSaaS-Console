package com.msaas.runtime;

import com.msaas.spec.contract.MockRoute;
import com.msaas.spec.contract.NormalizedContract;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.ArrayList;
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
                .sorted(Comparator.comparingInt(this::specificityScore).reversed())
                .map(route -> tryMatch(route, path, queryParameters, normalizedHeaders, requestBody))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    public List<String> mismatchHints(
            NormalizedContract contract,
            String method,
            String path,
            Map<String, List<String>> queryParameters,
            Map<String, String> headers,
            String requestBody
    ) {
        if (contract == null || contract.getRoutes() == null) {
            return List.of("Contract is not loaded");
        }
        Map<String, String> normalizedHeaders = normalizeHeaders(headers);
        List<String> hints = new ArrayList<>();
        contract.getRoutes().stream()
                .filter(route -> route.getMethod().equalsIgnoreCase(method))
                .sorted(Comparator.comparingInt(this::specificityScore).reversed())
                .forEach(route -> explain(route, path, queryParameters, normalizedHeaders, requestBody).ifPresent(hints::add));
        if (hints.isEmpty()) {
            return List.of("No routes with method " + method.toUpperCase(Locale.ROOT));
        }
        return hints.stream().limit(5).toList();
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

    private Optional<String> explain(
            MockRoute route,
            String path,
            Map<String, List<String>> queryParameters,
            Map<String, String> headers,
            String requestBody
    ) {
        List<String> templateSegments = segments(route.getPathTemplate());
        List<String> pathSegments = segments(path);
        String routeName = route.getMethod() + " " + route.getPathTemplate();
        if (templateSegments.size() != pathSegments.size()) {
            return Optional.of(routeName + ": path segment count differs");
        }
        for (int i = 0; i < templateSegments.size(); i++) {
            String template = templateSegments.get(i);
            String actual = pathSegments.get(i);
            if (!template.startsWith("{") && !template.endsWith("}") && !template.equals(actual)) {
                return Optional.of(routeName + ": segment " + (i + 1) + " expected '" + template + "'");
            }
        }
        List<String> missingQuery = route.getRequiredQueryParameters().stream()
                .filter(name -> !queryParameters.containsKey(name) || queryParameters.get(name) == null || queryParameters.get(name).isEmpty())
                .toList();
        if (!missingQuery.isEmpty()) {
            return Optional.of(routeName + ": missing query " + String.join(", ", missingQuery));
        }
        List<String> missingHeaders = route.getRequiredHeaderParameters().stream()
                .filter(name -> !headers.containsKey(name.toLowerCase(Locale.ROOT)))
                .toList();
        if (!missingHeaders.isEmpty()) {
            return Optional.of(routeName + ": missing header " + String.join(", ", missingHeaders));
        }
        if (route.isRequestBodyRequired() && (requestBody == null || requestBody.isBlank())) {
            return Optional.of(routeName + ": request body is required");
        }
        return Optional.empty();
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

    private int specificityScore(MockRoute route) {
        List<String> segments = segments(route.getPathTemplate());
        int score = segments.size();
        for (String segment : segments) {
            score += segment.startsWith("{") && segment.endsWith("}") ? 1 : 10;
        }
        score += route.getRequiredQueryParameters().size() * 2;
        score += route.getRequiredHeaderParameters().size() * 2;
        return score;
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
