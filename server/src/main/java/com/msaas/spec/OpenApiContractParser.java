package com.msaas.spec;

import com.fasterxml.jackson.databind.JsonNode;
import com.msaas.spec.contract.MockResponseDefinition;
import com.msaas.spec.contract.MockRoute;
import com.msaas.spec.contract.NormalizedContract;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Component
public class OpenApiContractParser {

    public ParsedContract parse(String source) {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        options.setFlatten(false);

        SwaggerParseResult result = new OpenAPIParser().readContents(source, null, options);
        List<String> messages = result.getMessages() == null ? List.of() : result.getMessages();
        OpenAPI openAPI = result.getOpenAPI();
        if (openAPI == null) {
            return new ParsedContract(null, messages.isEmpty() ? List.of("Specification could not be parsed") : messages);
        }

        NormalizedContract contract = normalize(openAPI);
        List<String> errors = new ArrayList<>();
        if (contract.getRoutes().isEmpty()) {
            errors.add("Specification does not contain any HTTP operations");
        }
        return new ParsedContract(contract, errors);
    }

    private NormalizedContract normalize(OpenAPI openAPI) {
        String title = openAPI.getInfo() == null ? "Untitled API" : openAPI.getInfo().getTitle();
        String version = openAPI.getInfo() == null ? "0.0.0" : openAPI.getInfo().getVersion();
        List<MockRoute> routes = new ArrayList<>();

        if (openAPI.getPaths() != null) {
            openAPI.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                MockRoute route = toRoute(path, method, item, operation);
                routes.add(route);
            }));
        }

        return new NormalizedContract(title, version, routes);
    }

    private MockRoute toRoute(String path, PathItem.HttpMethod method, PathItem item, Operation operation) {
        Map<String, MockResponseDefinition> responses = extractResponses(operation.getResponses());
        int defaultStatus = responses.values().stream()
                .map(MockResponseDefinition::getStatusCode)
                .filter(status -> status >= 200 && status < 300)
                .findFirst()
                .orElseGet(() -> responses.values().stream().map(MockResponseDefinition::getStatusCode).findFirst().orElse(200));

        MockRoute route = new MockRoute(method.name(), path, operation.getOperationId(), defaultStatus);
        route.setRequiredQueryParameters(requiredParameters(item, operation, "query"));
        route.setRequiredHeaderParameters(requiredParameters(item, operation, "header"));
        route.setRequestBodyRequired(operation.getRequestBody() != null && Boolean.TRUE.equals(operation.getRequestBody().getRequired()));
        route.setResponses(responses.isEmpty()
                ? Map.of("200", new MockResponseDefinition(200, "application/json", Map.of("ok", true), 0))
                : responses);
        return route;
    }

    private List<String> requiredParameters(PathItem item, Operation operation, String location) {
        Stream<Parameter> pathParameters = item.getParameters() == null ? Stream.empty() : item.getParameters().stream();
        Stream<Parameter> operationParameters = operation.getParameters() == null ? Stream.empty() : operation.getParameters().stream();
        return Stream.concat(pathParameters, operationParameters)
                .filter(parameter -> location.equals(parameter.getIn()))
                .filter(parameter -> Boolean.TRUE.equals(parameter.getRequired()))
                .map(Parameter::getName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
    }

    private Map<String, MockResponseDefinition> extractResponses(ApiResponses apiResponses) {
        Map<String, MockResponseDefinition> responses = new LinkedHashMap<>();
        if (apiResponses == null) {
            return responses;
        }
        apiResponses.forEach((code, response) -> {
            int status = parseStatus(code);
            ResponseBody body = responseBody(response);
            responses.put(String.valueOf(status), new MockResponseDefinition(status, body.contentType(), body.body(), 0));
        });
        return responses;
    }

    private int parseStatus(String code) {
        if ("default".equalsIgnoreCase(code)) {
            return 500;
        }
        try {
            return Integer.parseInt(code);
        } catch (NumberFormatException ignored) {
            return 200;
        }
    }

    private ResponseBody responseBody(ApiResponse response) {
        if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
            return new ResponseBody("application/json", Map.of("ok", true));
        }

        Content content = response.getContent();
        String contentType = content.containsKey("application/json")
                ? "application/json"
                : content.keySet().stream().findFirst().orElse("application/json");
        MediaType mediaType = content.get(contentType);
        if (mediaType == null) {
            return new ResponseBody(contentType, Map.of("ok", true));
        }

        Object example = extractExample(mediaType);
        if (example != null) {
            return new ResponseBody(contentType, example);
        }
        return new ResponseBody(contentType, exampleFromSchema(mediaType.getSchema()));
    }

    private Object extractExample(MediaType mediaType) {
        if (mediaType.getExample() != null) {
            return normalizeExample(mediaType.getExample());
        }
        if (mediaType.getExamples() != null && !mediaType.getExamples().isEmpty()) {
            return normalizeExample(mediaType.getExamples().values().iterator().next().getValue());
        }
        return null;
    }

    private Object normalizeExample(Object value) {
        if (value instanceof JsonNode node) {
            if (node.isObject()) {
                Map<String, Object> map = new LinkedHashMap<>();
                node.fields().forEachRemaining(entry -> map.put(entry.getKey(), normalizeExample(entry.getValue())));
                return map;
            }
            if (node.isArray()) {
                List<Object> list = new ArrayList<>();
                node.forEach(item -> list.add(normalizeExample(item)));
                return list;
            }
            if (node.isNumber()) {
                return node.numberValue();
            }
            if (node.isBoolean()) {
                return node.booleanValue();
            }
            if (node.isNull()) {
                return null;
            }
            return node.asText();
        }
        return value;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object exampleFromSchema(Schema schema) {
        if (schema == null) {
            return Map.of("ok", true);
        }
        if (schema.getExample() != null) {
            return normalizeExample(schema.getExample());
        }
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            return schema.getEnum().getFirst();
        }
        if (schema.getOneOf() != null && !schema.getOneOf().isEmpty()) {
            return exampleFromSchema((Schema) schema.getOneOf().getFirst());
        }
        if (schema.getAnyOf() != null && !schema.getAnyOf().isEmpty()) {
            return exampleFromSchema((Schema) schema.getAnyOf().getFirst());
        }
        if (schema.getAllOf() != null && !schema.getAllOf().isEmpty()) {
            Map<String, Object> merged = new LinkedHashMap<>();
            for (Object child : schema.getAllOf()) {
                Object value = exampleFromSchema((Schema) child);
                if (value instanceof Map<?, ?> map) {
                    map.forEach((key, item) -> merged.put(String.valueOf(key), item));
                }
            }
            return merged.isEmpty() ? Map.of("ok", true) : merged;
        }

        String type = schema.getType();
        if ("object".equals(type) || schema.getProperties() != null) {
            Map<String, Object> object = new LinkedHashMap<>();
            Map<String, Schema> properties = schema.getProperties();
            if (properties != null) {
                properties.forEach((name, property) -> object.put(name, exampleFromSchema(property)));
            }
            return object.isEmpty() ? Map.of("ok", true) : object;
        }
        if ("array".equals(type)) {
            return List.of(exampleFromSchema(schema.getItems()));
        }
        if ("integer".equals(type)) {
            return 1;
        }
        if ("number".equals(type)) {
            return 1.0;
        }
        if ("boolean".equals(type)) {
            return true;
        }
        if ("string".equals(type)) {
            return stringExample(schema.getFormat());
        }
        return Map.of("ok", true);
    }

    private String stringExample(String format) {
        if ("date".equals(format)) {
            return LocalDate.now().toString();
        }
        if ("date-time".equals(format)) {
            return OffsetDateTime.now().toString();
        }
        if ("uuid".equals(format)) {
            return UUID.randomUUID().toString();
        }
        if ("email".equals(format)) {
            return "user@example.com";
        }
        return "string";
    }

    public record ParsedContract(NormalizedContract contract, List<String> errors) {
        public boolean valid() {
            return contract != null && errors.isEmpty();
        }
    }

    private record ResponseBody(String contentType, Object body) {
    }
}
