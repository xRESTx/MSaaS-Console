package com.msaas.spec.contract;

public class MockResponseDefinition {
    private int statusCode;
    private String contentType;
    private Object body;
    private long delayMs;

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
}
