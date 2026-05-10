package com.msaas.spec.contract;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MockRoute {
    private String method;
    private String pathTemplate;
    private String operationId;
    private int defaultStatusCode;
    private List<String> requiredQueryParameters = List.of();
    private List<String> requiredHeaderParameters = List.of();
    private boolean requestBodyRequired;
    private Map<String, MockResponseDefinition> responses = new LinkedHashMap<>();

    public MockRoute() {
    }

    public MockRoute(String method, String pathTemplate, String operationId, int defaultStatusCode) {
        this.method = method;
        this.pathTemplate = pathTemplate;
        this.operationId = operationId;
        this.defaultStatusCode = defaultStatusCode;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPathTemplate() {
        return pathTemplate;
    }

    public void setPathTemplate(String pathTemplate) {
        this.pathTemplate = pathTemplate;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public int getDefaultStatusCode() {
        return defaultStatusCode;
    }

    public void setDefaultStatusCode(int defaultStatusCode) {
        this.defaultStatusCode = defaultStatusCode;
    }

    public List<String> getRequiredQueryParameters() {
        return requiredQueryParameters;
    }

    public void setRequiredQueryParameters(List<String> requiredQueryParameters) {
        this.requiredQueryParameters = requiredQueryParameters == null ? List.of() : requiredQueryParameters;
    }

    public List<String> getRequiredHeaderParameters() {
        return requiredHeaderParameters;
    }

    public void setRequiredHeaderParameters(List<String> requiredHeaderParameters) {
        this.requiredHeaderParameters = requiredHeaderParameters == null ? List.of() : requiredHeaderParameters;
    }

    public boolean isRequestBodyRequired() {
        return requestBodyRequired;
    }

    public void setRequestBodyRequired(boolean requestBodyRequired) {
        this.requestBodyRequired = requestBodyRequired;
    }

    public Map<String, MockResponseDefinition> getResponses() {
        return responses;
    }

    public void setResponses(Map<String, MockResponseDefinition> responses) {
        this.responses = responses;
    }

    public MockResponseDefinition defaultResponse() {
        MockResponseDefinition response = responses.get(String.valueOf(defaultStatusCode));
        if (response != null) {
            return response;
        }
        return responses.values().stream().findFirst().orElse(new MockResponseDefinition(200, "application/json", Map.of(), 0));
    }
}
