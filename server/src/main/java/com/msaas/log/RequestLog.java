package com.msaas.log;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Document("request_logs")
public class RequestLog {
    @Id
    private String id;

    @Indexed
    private String projectId;

    @Indexed
    private String instanceId;

    private String method;
    private String path;
    private String queryString;
    private Map<String, String> requestHeaders = new LinkedHashMap<>();
    private String requestBody;
    private int responseStatus;
    private boolean matched;
    private String error;
    private String responseSource;
    private String profileName;
    private List<String> appliedRuleIds = new ArrayList<>();
    private long latencyMs;
    private Instant receivedAt;

    public RequestLog() {
    }

    public RequestLog(String projectId, String instanceId, String method, String path, String queryString) {
        this.projectId = projectId;
        this.instanceId = instanceId;
        this.method = method;
        this.path = path;
        this.queryString = queryString;
        this.receivedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(Map<String, String> requestHeaders) {
        this.requestHeaders = requestHeaders == null ? new LinkedHashMap<>() : requestHeaders;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(int responseStatus) {
        this.responseStatus = responseStatus;
    }

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getResponseSource() {
        return responseSource == null || responseSource.isBlank() ? ResponseSource.FALLBACK.name() : responseSource;
    }

    public void setResponseSource(String responseSource) {
        this.responseSource = responseSource;
    }

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public List<String> getAppliedRuleIds() {
        if (appliedRuleIds == null) {
            appliedRuleIds = new ArrayList<>();
        }
        return appliedRuleIds;
    }

    public void setAppliedRuleIds(List<String> appliedRuleIds) {
        this.appliedRuleIds = appliedRuleIds == null ? new ArrayList<>() : appliedRuleIds;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }
}
