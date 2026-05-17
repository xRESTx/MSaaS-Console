package com.msaas.runtime;

import com.msaas.instance.ResponseRule;
import com.msaas.spec.contract.MockRoute;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

@Component
public class ResponseRuleApplier {
    private final TemplateRenderer templateRenderer;

    public ResponseRuleApplier(TemplateRenderer templateRenderer) {
        this.templateRenderer = templateRenderer;
    }

    public Object apply(
            Object body,
            List<ResponseRule> rules,
            RouteMatch match,
            String requestBody,
            Map<String, List<String>> queryParameters,
            Map<String, String> headers,
            String seed
    ) {
        return applyWithMetadata(body, rules, match, requestBody, queryParameters, headers, seed).body();
    }

    public AppliedResponse applyWithMetadata(
            Object body,
            List<ResponseRule> rules,
            RouteMatch match,
            String requestBody,
            Map<String, List<String>> queryParameters,
            Map<String, String> headers,
            String seed
    ) {
        if (rules == null || rules.isEmpty() || body == null) {
            return new AppliedResponse(body, List.of());
        }
        Object next = deepCopy(body);
        List<String> appliedRuleIds = new ArrayList<>();
        rules.stream()
                .filter(ResponseRule::isEnabled)
                .filter(rule -> ruleMatches(rule, match.route()))
                .filter(rule -> rule.getFieldPath() != null && !rule.getFieldPath().isBlank())
                .sorted(Comparator.comparingInt(ResponseRule::getPriority).reversed())
                .forEach(rule -> {
                    setValue(next, rule.getFieldPath(), ruleValue(rule, match, requestBody, queryParameters, headers, seed));
                    appliedRuleIds.add(rule.getId());
                });
        return new AppliedResponse(next, appliedRuleIds);
    }

    private boolean ruleMatches(ResponseRule rule, MockRoute route) {
        if (rule.getOperationId() != null && !rule.getOperationId().isBlank() && route.getOperationId() != null) {
            return rule.getOperationId().equals(route.getOperationId());
        }
        boolean methodMatches = rule.getMethod() == null || rule.getMethod().isBlank() || rule.getMethod().equalsIgnoreCase(route.getMethod());
        boolean pathMatches = rule.getPathTemplate() == null || rule.getPathTemplate().isBlank() || rule.getPathTemplate().equals(route.getPathTemplate());
        return methodMatches && pathMatches;
    }

    private Object ruleValue(
            ResponseRule rule,
            RouteMatch match,
            String requestBody,
            Map<String, List<String>> queryParameters,
            Map<String, String> headers,
            String seed
    ) {
        String type = rule.getType() == null ? "FIXED" : rule.getType().toUpperCase(Locale.ROOT);
        if ("RANDOM_INT".equals(type)) {
            int min = rule.getMinValue() == null ? 0 : rule.getMinValue();
            int max = rule.getMaxValue() == null ? 10_000 : rule.getMaxValue();
            if (max < min) {
                int swap = min;
                min = max;
                max = swap;
            }
            return min + new Random(seed(seed + ":" + rule.getId() + ":" + rule.getFieldPath())).nextInt(max - min + 1);
        }
        if ("ENUM".equals(type)) {
            List<Object> values = rule.getEnumValues();
            if (values.isEmpty()) {
                return null;
            }
            return values.get(new Random(seed(seed + ":" + rule.getId() + ":" + rule.getFieldPath())).nextInt(values.size()));
        }
        if ("TEMPLATE".equals(type)) {
            return templateRenderer.render(rule.getTemplate() == null ? "" : rule.getTemplate(), match.variables(), queryParameters, headers, requestBody);
        }
        return rule.getFixedValue();
    }

    @SuppressWarnings("unchecked")
    private void setValue(Object body, String path, Object value) {
        String[] parts = path.split("\\.");
        Object current = body;
        for (int i = 0; i < parts.length; i++) {
            PathPart part = PathPart.parse(parts[i]);
            boolean last = i == parts.length - 1;
            if (!(current instanceof Map<?, ?> map)) {
                return;
            }
            Map<String, Object> currentMap = (Map<String, Object>) map;
            if (last) {
                if (part.index() == null) {
                    currentMap.put(part.name(), value);
                } else {
                    List<Object> list = listAt(currentMap, part.name());
                    ensureSize(list, part.index() + 1);
                    list.set(part.index(), value);
                }
                return;
            }
            current = nextContainer(currentMap, part);
        }
    }

    private Object nextContainer(Map<String, Object> map, PathPart part) {
        if (part.index() == null) {
            Object child = map.get(part.name());
            if (!(child instanceof Map<?, ?>)) {
                child = new LinkedHashMap<String, Object>();
                map.put(part.name(), child);
            }
            return child;
        }
        List<Object> list = listAt(map, part.name());
        ensureSize(list, part.index() + 1);
        Object child = list.get(part.index());
        if (!(child instanceof Map<?, ?>)) {
            child = new LinkedHashMap<String, Object>();
            list.set(part.index(), child);
        }
        return child;
    }

    @SuppressWarnings("unchecked")
    private List<Object> listAt(Map<String, Object> map, String name) {
        Object value = map.get(name);
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        List<Object> list = new ArrayList<>();
        map.put(name, list);
        return list;
    }

    private void ensureSize(List<Object> list, int size) {
        while (list.size() < size) {
            list.add(new LinkedHashMap<String, Object>());
        }
    }

    @SuppressWarnings("unchecked")
    private Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(String.valueOf(key), deepCopy(item)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            list.forEach(item -> copy.add(deepCopy(item)));
            return copy;
        }
        return value;
    }

    private long seed(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException ex) {
            return value.hashCode();
        }
    }

    public record AppliedResponse(Object body, List<String> appliedRuleIds) {
    }

    private record PathPart(String name, Integer index) {
        static PathPart parse(String text) {
            int bracket = text.indexOf('[');
            if (bracket < 0 || !text.endsWith("]")) {
                return new PathPart(text, null);
            }
            String name = text.substring(0, bracket);
            try {
                return new PathPart(name, Math.max(0, Integer.parseInt(text.substring(bracket + 1, text.length() - 1))));
            } catch (NumberFormatException ignored) {
                return new PathPart(text, null);
            }
        }
    }
}
