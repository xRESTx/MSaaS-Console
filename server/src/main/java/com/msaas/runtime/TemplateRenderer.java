package com.msaas.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TemplateRenderer {
    private static final Pattern TEMPLATE = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");
    private final ObjectMapper objectMapper;

    public TemplateRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Object render(
            Object value,
            Map<String, String> pathVariables,
            Map<String, List<String>> queryParameters,
            Map<String, String> headers,
            String requestBody
    ) {
        Map<String, Object> body = parseBody(requestBody);
        Map<String, String> normalizedHeaders = new LinkedHashMap<>();
        headers.forEach((name, headerValue) -> normalizedHeaders.put(name.toLowerCase(Locale.ROOT), headerValue));
        return renderValue(value, pathVariables, queryParameters, normalizedHeaders, body);
    }

    @SuppressWarnings("unchecked")
    private Object renderValue(
            Object value,
            Map<String, String> pathVariables,
            Map<String, List<String>> queryParameters,
            Map<String, String> headers,
            Map<String, Object> body
    ) {
        if (value instanceof String text) {
            return renderString(text, pathVariables, queryParameters, headers, body);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> rendered = new LinkedHashMap<>();
            map.forEach((key, item) -> rendered.put(String.valueOf(key), renderValue(item, pathVariables, queryParameters, headers, body)));
            return rendered;
        }
        if (value instanceof List<?> list) {
            List<Object> rendered = new ArrayList<>();
            list.forEach(item -> rendered.add(renderValue(item, pathVariables, queryParameters, headers, body)));
            return rendered;
        }
        return value;
    }

    private String renderString(
            String text,
            Map<String, String> pathVariables,
            Map<String, List<String>> queryParameters,
            Map<String, String> headers,
            Map<String, Object> body
    ) {
        Matcher matcher = TEMPLATE.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(resolve(matcher.group(1), pathVariables, queryParameters, headers, body)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String resolve(
            String expression,
            Map<String, String> pathVariables,
            Map<String, List<String>> queryParameters,
            Map<String, String> headers,
            Map<String, Object> body
    ) {
        if ("uuid".equals(expression)) {
            return UUID.randomUUID().toString();
        }
        if ("now".equals(expression)) {
            return OffsetDateTime.now().toString();
        }
        if ("date".equals(expression)) {
            return LocalDate.now().toString();
        }
        if ("randomInt".equals(expression)) {
            return String.valueOf(ThreadLocalRandom.current().nextInt(0, 10_000));
        }
        if (expression.startsWith("path.")) {
            return pathVariables.getOrDefault(expression.substring("path.".length()), "");
        }
        if (expression.startsWith("query.")) {
            List<String> values = queryParameters.get(expression.substring("query.".length()));
            return values == null || values.isEmpty() ? "" : values.getFirst();
        }
        if (expression.startsWith("header.")) {
            return headers.getOrDefault(expression.substring("header.".length()).toLowerCase(Locale.ROOT), "");
        }
        if (expression.startsWith("body.")) {
            Object value = resolveBody(body, expression.substring("body.".length()));
            return value == null ? "" : String.valueOf(value);
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private Object resolveBody(Map<String, Object> body, String path) {
        Object current = body;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    private Map<String, Object> parseBody(String requestBody) {
        if (requestBody == null || requestBody.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(requestBody, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}
