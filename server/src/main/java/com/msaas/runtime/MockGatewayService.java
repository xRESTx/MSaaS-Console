package com.msaas.runtime;

import com.msaas.common.ApiException;
import com.msaas.config.AppProperties;
import com.msaas.instance.InstanceStatus;
import com.msaas.instance.MockInstance;
import com.msaas.instance.MockInstanceRepository;
import com.msaas.security.SecretHashService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class MockGatewayService {
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "content-length",
            "expect",
            "host",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade"
    );

    private final MockInstanceRepository instanceRepository;
    private final SecretHashService secretHashService;
    private final RuntimePlaneService runtimePlaneService;
    private final AppProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public MockGatewayService(
            MockInstanceRepository instanceRepository,
            SecretHashService secretHashService,
            RuntimePlaneService runtimePlaneService,
            AppProperties properties
    ) {
        this.instanceRepository = instanceRepository;
        this.secretHashService = secretHashService;
        this.runtimePlaneService = runtimePlaneService;
        this.properties = properties;
    }

    public ResponseEntity<byte[]> proxy(String token, HttpServletRequest request, String requestBody) {
        String tokenHash = secretHashService.hash(token);
        MockInstance instance = instanceRepository.findByPublicTokenHashAndStatus(tokenHash, InstanceStatus.RUNNING)
                .orElseThrow(() -> ApiException.notFound("Mock instance not found"));
        RuntimeWorker worker = runtimePlaneService.workerFor(instance);
        try {
            return forward(worker, token, request, requestBody);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            runtimePlaneService.markDown(worker.getWorkerKey());
            RuntimeWorker reassigned = runtimePlaneService.reassign(instance);
            try {
                return forward(reassigned, token, request, requestBody);
            } catch (IOException | InterruptedException retryEx) {
                if (retryEx instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw ApiException.serviceUnavailable("Runtime worker is unavailable");
            }
        }
    }

    private ResponseEntity<byte[]> forward(RuntimeWorker worker, String token, HttpServletRequest request, String requestBody) throws IOException, InterruptedException {
        URI uri = URI.create(worker.getBaseUrl().replaceAll("/+$", "") + "/internal/runtime/mock/" + token + pathSuffix(token, request));
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .method(request.getMethod(), requestBody == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(requestBody));
        copyHeaders(request, builder);
        builder.header("X-MSaaS-Internal-Secret", properties.getRuntime().getInternalSecret());
        builder.header("X-Forwarded-For", clientIp(request));
        HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        HttpHeaders headers = new HttpHeaders();
        response.headers().map().forEach((name, values) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                headers.put(name, values);
            }
        });
        return ResponseEntity.status(HttpStatusCode.valueOf(response.statusCode())).headers(headers).body(response.body());
    }

    private void copyHeaders(HttpServletRequest request, HttpRequest.Builder builder) {
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return;
        }
        Collections.list(names).forEach(name -> {
            if (HOP_BY_HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT)) || "x-msaas-internal-secret".equalsIgnoreCase(name)) {
                return;
            }
            Collections.list(request.getHeaders(name)).forEach(value -> builder.header(name, value));
        });
    }

    private String pathSuffix(String token, HttpServletRequest request) {
        String uri = request.getRequestURI();
        String prefix = "/mock/" + token;
        String path = uri.length() <= prefix.length() ? "" : uri.substring(prefix.length());
        String query = request.getQueryString();
        return (path.isBlank() ? "" : path) + (query == null || query.isBlank() ? "" : "?" + query);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
