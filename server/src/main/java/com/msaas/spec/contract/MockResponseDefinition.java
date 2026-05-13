package com.msaas.spec.contract;

import java.util.LinkedHashMap;
import java.util.Map;

public class MockResponseDefinition {
    private int statusCode;
    private String contentType;
    private Object body;
    private long delayMs;
    private Map<String, String> headers = new LinkedHashMap<>();
    private Map<String, Object> examples = new LinkedHashMap<>();
    private Map<String, ResponseContent> contents = new LinkedHashMap<>();

    public MockResponseDefinition() {
    }

    public MockResponseDefinition(int statusCode, String contentType, Object body, long delayMs) {
        this.statusCode = statusCode;
        this.contentType = contentType;
        this.body = body;
        this.delayMs = delayMs;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public long getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers == null ? new LinkedHashMap<>() : headers;
    }

    public Map<String, Object> getExamples() {
        if (examples == null) {
            examples = new LinkedHashMap<>();
        }
        return examples;
    }

    public void setExamples(Map<String, Object> examples) {
        this.examples = examples == null ? new LinkedHashMap<>() : examples;
    }

    public Map<String, ResponseContent> getContents() {
        if (contents == null) {
            contents = new LinkedHashMap<>();
        }
        if (contents.isEmpty()) {
            contents.put(contentType == null || contentType.isBlank() ? "application/json" : contentType, new ResponseContent(body, getExamples()));
        }
        return contents;
    }

    public void setContents(Map<String, ResponseContent> contents) {
        this.contents = contents == null ? new LinkedHashMap<>() : contents;
    }

    public MockResponseDefinition withBody(Object nextBody) {
        MockResponseDefinition copy = new MockResponseDefinition(statusCode, contentType, nextBody, delayMs);
        copy.setHeaders(new LinkedHashMap<>(headers));
        copy.setExamples(new LinkedHashMap<>(getExamples()));
        copy.setContents(new LinkedHashMap<>(getContents()));
        copy.getContents().put(copy.getContentType(), new ResponseContent(nextBody, copy.getExamples()));
        return copy;
    }

    public MockResponseDefinition withContent(String nextContentType, ResponseContent content) {
        MockResponseDefinition copy = new MockResponseDefinition(statusCode, nextContentType, content.getBody(), delayMs);
        copy.setHeaders(new LinkedHashMap<>(headers));
        copy.setExamples(new LinkedHashMap<>(content.getExamples()));
        copy.setContents(new LinkedHashMap<>(getContents()));
        return copy;
    }

    public static class ResponseContent {
        private Object body;
        private Map<String, Object> examples = new LinkedHashMap<>();

        public ResponseContent() {
        }

        public ResponseContent(Object body, Map<String, Object> examples) {
            this.body = body;
            this.examples = examples == null ? new LinkedHashMap<>() : examples;
        }

        public Object getBody() {
            return body;
        }

        public void setBody(Object body) {
            this.body = body;
        }

        public Map<String, Object> getExamples() {
            if (examples == null) {
                examples = new LinkedHashMap<>();
            }
            return examples;
        }

        public void setExamples(Map<String, Object> examples) {
            this.examples = examples == null ? new LinkedHashMap<>() : examples;
        }
    }
}
