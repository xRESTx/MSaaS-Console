package com.msaas.runtime;

import com.msaas.spec.contract.MockRoute;
import com.msaas.spec.contract.NormalizedContract;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class RouteMatcher {

    public Optional<RouteMatch> match(NormalizedContract contract, String method, String path) {
        if (contract == null || contract.getRoutes() == null) {
            return Optional.empty();
        }
        return contract.getRoutes().stream()
                .filter(route -> route.getMethod().equalsIgnoreCase(method))
                .map(route -> tryMatch(route, path))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private Optional<RouteMatch> tryMatch(MockRoute route, String path) {
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
        return Optional.of(new RouteMatch(route, variables));
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
