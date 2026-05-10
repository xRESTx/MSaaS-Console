package com.msaas.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class LogRedactor {
    private static final String MASK = "***";
    private static final int MAX_BODY_LENGTH = 20_000;
    private static final Set<String> SENSITIVE_EXACT = Set.of(
            "authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "x-mock-api-key",
            "password",
            "secret",
            "token",
            "api_key",
            "apikey",
            "access_token",
            "refresh_token"
    );
    private static final Set<String> LOGGED_HEADERS = Set.of(
            "accept",
            "content-type",
            "user-agent",
            "x-request-id",
            "x-mock-api-key",
            "authorization"
    );

    private final ObjectMapper objectMapper;

    public LogRedactor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String redactBody(String body) {
        if (body == null || body.isBlank()) {
            return body;
        }
        String bounded = body.length() > MAX_BODY_LENGTH ? body.substring(0, MAX_BODY_LENGTH) : body;
        try {
            JsonNode node = objectMapper.readTree(bounded);
            return objectMapper.writeValueAsString(redactJson(node));
        } catch (Exception ignored) {
            return bounded;
        }
    }

    public String redactQueryString(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return queryString;
        }
        return Arrays.stream(queryString.split("&"))
                .map(this::redactQueryPair)
                .reduce((left, right) -> left + "&" + right)
                .orElse(queryString);
    }

    public Map<String, String> redactHeaders(HttpServletRequest request) {
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return Map.of();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        Collections.list(names).forEach(name -> {
            String normalized = name.toLowerCase(Locale.ROOT);
            if (LOGGED_HEADERS.contains(normalized)) {
                headers.put(name, sensitive(normalized) ? MASK : request.getHeader(name));
            }
        });
        return headers;
    }

    private JsonNode redactJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode copy = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if (sensitive(key)) {
                    copy.put(key, MASK);
                } else {
                    copy.set(key, redactJson(entry.getValue()));
                }
            });
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = objectMapper.createArrayNode();
            node.forEach(item -> copy.add(redactJson(item)));
            return copy;
        }
        return node;
    }

    private String redactQueryPair(String pair) {
        String[] parts = pair.split("=", 2);
        String key = decode(parts[0]);
        if (parts.length == 2 && sensitive(key)) {
            return URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + MASK;
        }
        return pair;
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (RuntimeException ignored) {
            return value;
        }
    }

    private boolean sensitive(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return SENSITIVE_EXACT.contains(normalized)
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("apikey")
                || normalized.contains("api-key");
    }
}
