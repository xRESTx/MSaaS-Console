package com.msaas.spec;

import com.msaas.common.ApiException;
import com.msaas.runtime.SchemaResponseGenerator;
import com.msaas.spec.contract.MockResponseDefinition;
import com.msaas.spec.contract.MockRoute;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class SpecResponsePreviewService {
    private final SchemaResponseGenerator schemaResponseGenerator;

    public SpecResponsePreviewService(SchemaResponseGenerator schemaResponseGenerator) {
        this.schemaResponseGenerator = schemaResponseGenerator;
    }

    public Preview preview(SpecVersion version, PreviewRequest request) {
        MockRoute route = route(version, request);
        MockResponseDefinition definition = response(route, request.statusCode());
        definition = content(definition, request.contentType());

        if (request.exampleName() != null && !request.exampleName().isBlank()) {
            Object example = definition.getExamples().get(request.exampleName());
            if (example != null) {
                return new Preview(definition.getStatusCode(), definition.getContentType(), example, false, seed(version, route, definition, request));
            }
        }

        MockResponseDefinition.ResponseContent responseContent = definition.getContents().get(definition.getContentType());
        String seed = seed(version, route, definition, request);
        if (definition.getBody() == null && responseContent != null && responseContent.getSchema() != null && !responseContent.isExplicitExample()) {
            return new Preview(definition.getStatusCode(), definition.getContentType(), schemaResponseGenerator.generate(responseContent.getSchema(), seed), true, seed);
        }
        return new Preview(definition.getStatusCode(), definition.getContentType(), definition.getBody() == null ? Map.of("ok", true) : definition.getBody(), false, seed);
    }

    private MockRoute route(SpecVersion version, PreviewRequest request) {
        if (version.getNormalizedContract() == null || version.getNormalizedContract().getRoutes() == null) {
            throw ApiException.badRequest("Specification contract is not loaded");
        }
        return version.getNormalizedContract().getRoutes().stream()
                .filter(route -> {
                    if (request.operationId() != null && !request.operationId().isBlank() && route.getOperationId() != null) {
                        return request.operationId().equals(route.getOperationId());
                    }
                    return request.method() != null
                            && request.pathTemplate() != null
                            && request.method().equalsIgnoreCase(route.getMethod())
                            && request.pathTemplate().equals(route.getPathTemplate());
                })
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Route not found"));
    }

    private MockResponseDefinition response(MockRoute route, Integer statusCode) {
        if (statusCode != null) {
            MockResponseDefinition exact = route.getResponses().get(String.valueOf(statusCode));
            if (exact != null) {
                return exact;
            }
        }
        return route.defaultResponse();
    }

    private MockResponseDefinition content(MockResponseDefinition definition, String contentType) {
        if (contentType == null || contentType.isBlank() || !definition.getContents().containsKey(contentType)) {
            return definition;
        }
        return definition.withContent(contentType, definition.getContents().get(contentType));
    }

    private String seed(SpecVersion version, MockRoute route, MockResponseDefinition definition, PreviewRequest request) {
        return Optional.ofNullable(request.seed())
                .filter(value -> !value.isBlank())
                .orElse(String.join(":",
                        version.getId(),
                        Optional.ofNullable(route.getOperationId()).orElse(route.getMethod() + " " + route.getPathTemplate()),
                        String.valueOf(definition.getStatusCode()),
                        route.getPathTemplate()
                ));
    }

    public record PreviewRequest(
            String operationId,
            String method,
            String pathTemplate,
            Integer statusCode,
            String contentType,
            String exampleName,
            String seed
    ) {
    }

    public record Preview(int statusCode, String contentType, Object body, boolean generated, String seed) {
    }
}
