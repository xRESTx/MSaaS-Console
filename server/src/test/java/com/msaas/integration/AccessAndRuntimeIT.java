package com.msaas.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "server.port=18081",
                "app.public-base-url=http://localhost:18081"
        }
)
class AccessAndRuntimeIT {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:8");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", mongo::getConnectionString);
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void privateProjectsAndPublicMockRuntimeFlow() throws Exception {
        String ownerToken = register("owner@example.com");
        String otherToken = register("other@example.com");

        Map<String, Object> project = requestMap(HttpMethod.POST, "/api/projects", ownerToken, Map.of("name", "Orders", "description", "Demo"));
        String projectId = String.valueOf(project.get("id"));

        HttpResponse<String> forbiddenProject = request(HttpMethod.GET, "/api/projects/" + projectId, otherToken, null);
        assertThat(forbiddenProject.statusCode()).isEqualTo(404);

        Map<String, Object> version = requestMap(HttpMethod.POST, "/api/projects/" + projectId + "/spec-versions", ownerToken, Map.of(
                "name", "orders.yaml",
                "source", sampleSpec()
        ));
        assertThat(version.get("status")).isEqualTo("VALID");

        Map<String, Object> instance = requestMap(HttpMethod.POST, "/api/spec-versions/" + version.get("id") + "/publish", ownerToken, Map.of(
                "mode", "STATEFUL",
                "requireApiKey", false
        ));
        String publicUrl = String.valueOf(instance.get("publicUrl"));
        assertThat(publicUrl).contains("/mock/");

        HttpResponse<String> forbiddenInstance = request(HttpMethod.GET, "/api/instances/" + instance.get("id"), otherToken, null);
        assertThat(forbiddenInstance.statusCode()).isEqualTo(404);

        requestMap(HttpMethod.PATCH, "/api/instances/" + instance.get("id") + "/settings", ownerToken, Map.of(
                "rateLimitEnabled", true,
                "rateLimitRequests", 2,
                "rateLimitWindowSeconds", 60
        ));

        requestMap(HttpMethod.POST, "/api/instances/" + instance.get("id") + "/scenarios", ownerToken, Map.of(
                "name", "Scenario response",
                "enabled", true,
                "priority", 200,
                "method", "GET",
                "pathTemplate", "/orders",
                "statusCode", 202,
                "contentType", "application/json",
                "body", Map.of("scenario", "{{query.name}}", "requestId", "{{uuid}}"),
                "delayMs", 0
        ));

        HttpResponse<String> mockResponse = get(publicUrl + "/orders?name=alice");
        assertThat(mockResponse.statusCode()).isEqualTo(202);
        assertThat(mockResponse.body()).contains("alice").contains("requestId");

        HttpResponse<String> secondMockResponse = get(publicUrl + "/orders?name=bob");
        assertThat(secondMockResponse.statusCode()).isEqualTo(202);

        HttpResponse<String> limitedResponse = get(publicUrl + "/orders?name=carol");
        assertThat(limitedResponse.statusCode()).isEqualTo(429);
        assertThat(limitedResponse.headers().firstValue("Retry-After")).isPresent();

        List<Map<String, Object>> logs = requestList(HttpMethod.GET, "/api/instances/" + instance.get("id") + "/logs", ownerToken, null);
        assertThat(logs).isNotEmpty();
        assertThat(logs.stream().map(log -> String.valueOf(log.get("error")))).contains("Rate limit exceeded");
    }

    private String register(String email) throws Exception {
        Map<String, Object> body = requestMap(HttpMethod.POST, "/api/auth/register", null, Map.of("email", email, "password", "password"));
        return String.valueOf(body.get("token"));
    }

    private Map<String, Object> requestMap(HttpMethod method, String path, String token, Map<String, Object> body) throws Exception {
        HttpResponse<String> response = request(method, path, token, body);
        assertThat(response.statusCode()).isBetween(200, 299);
        return objectMapper.readValue(response.body(), new TypeReference<>() {
        });
    }

    private List<Map<String, Object>> requestList(HttpMethod method, String path, String token, Map<String, Object> body) throws Exception {
        HttpResponse<String> response = request(method, path, token, body);
        assertThat(response.statusCode()).isBetween(200, 299);
        return objectMapper.readValue(response.body(), new TypeReference<>() {
        });
    }

    private HttpResponse<String> request(HttpMethod method, String path, String token, Map<String, Object> body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:18081" + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body == null) {
            builder.method(method.name(), HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method.name(), HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String sampleSpec() {
        return """
                openapi: 3.0.3
                info:
                  title: Orders
                  version: 1.0.0
                paths:
                  /orders:
                    get:
                      responses:
                        "200":
                          description: OK
                          content:
                            application/json:
                              schema:
                                type: array
                                items:
                                  type: object
                                  properties:
                                    id:
                                      type: string
                                    paid:
                                      type: boolean
                """;
    }
}
