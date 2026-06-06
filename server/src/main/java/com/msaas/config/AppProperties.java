package com.msaas.config;

import com.msaas.runtime.RuntimeRole;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String publicBaseUrl;
    private Cors cors = new Cors();
    private Jwt jwt = new Jwt();
    private Admin admin = new Admin();
    private Runtime runtime = new Runtime();

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }

    public Runtime getRuntime() {
        return runtime;
    }

    public void setRuntime(Runtime runtime) {
        this.runtime = runtime;
    }

    public static class Cors {
        private String allowedOrigins;

        public String getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Jwt {
        private String secret;
        private long ttlMinutes;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getTtlMinutes() {
            return ttlMinutes;
        }

        public void setTtlMinutes(long ttlMinutes) {
            this.ttlMinutes = ttlMinutes;
        }
    }

    public static class Admin {
        private String bootstrapEmail;

        public String getBootstrapEmail() {
            return bootstrapEmail;
        }

        public void setBootstrapEmail(String bootstrapEmail) {
            this.bootstrapEmail = bootstrapEmail;
        }
    }

    public static class Runtime {
        private RuntimeRole role = RuntimeRole.EMBEDDED;
        private String workerKey;
        private String baseUrl = "local";
        private String internalSecret = "local-runtime-secret";
        private long staleAfterSeconds = 30;
        private long heartbeatIntervalSeconds = 5;
        private long workerCleanupAfterSeconds = 86400;
        private int maxSlotsPerWorker = 250;
        private long slotIdleTtlSeconds = 1800;
        private long slotCleanupDelayMs = 60000;

        public RuntimeRole getRole() {
            return role == null ? RuntimeRole.EMBEDDED : role;
        }

        public void setRole(RuntimeRole role) {
            this.role = role;
        }

        public String getWorkerKey() {
            return workerKey;
        }

        public void setWorkerKey(String workerKey) {
            this.workerKey = workerKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getInternalSecret() {
            return internalSecret;
        }

        public void setInternalSecret(String internalSecret) {
            this.internalSecret = internalSecret;
        }

        public long getStaleAfterSeconds() {
            return staleAfterSeconds <= 0 ? 30 : staleAfterSeconds;
        }

        public void setStaleAfterSeconds(long staleAfterSeconds) {
            this.staleAfterSeconds = staleAfterSeconds;
        }

        public long getHeartbeatIntervalSeconds() {
            return heartbeatIntervalSeconds <= 0 ? 5 : heartbeatIntervalSeconds;
        }

        public void setHeartbeatIntervalSeconds(long heartbeatIntervalSeconds) {
            this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        }

        public long getWorkerCleanupAfterSeconds() {
            return workerCleanupAfterSeconds <= 0 ? 86400 : workerCleanupAfterSeconds;
        }

        public void setWorkerCleanupAfterSeconds(long workerCleanupAfterSeconds) {
            this.workerCleanupAfterSeconds = workerCleanupAfterSeconds;
        }

        public int getMaxSlotsPerWorker() {
            return maxSlotsPerWorker <= 0 ? 250 : maxSlotsPerWorker;
        }

        public void setMaxSlotsPerWorker(int maxSlotsPerWorker) {
            this.maxSlotsPerWorker = maxSlotsPerWorker;
        }

        public long getSlotIdleTtlSeconds() {
            return slotIdleTtlSeconds <= 0 ? 1800 : slotIdleTtlSeconds;
        }

        public void setSlotIdleTtlSeconds(long slotIdleTtlSeconds) {
            this.slotIdleTtlSeconds = slotIdleTtlSeconds;
        }

        public long getSlotCleanupDelayMs() {
            return slotCleanupDelayMs <= 0 ? 60000 : slotCleanupDelayMs;
        }

        public void setSlotCleanupDelayMs(long slotCleanupDelayMs) {
            this.slotCleanupDelayMs = slotCleanupDelayMs;
        }

        public boolean isEmbedded() {
            return getRole() == RuntimeRole.EMBEDDED;
        }

        public boolean isControl() {
            return getRole() == RuntimeRole.CONTROL;
        }

        public boolean isRuntime() {
            return getRole() == RuntimeRole.RUNTIME;
        }
    }
}
