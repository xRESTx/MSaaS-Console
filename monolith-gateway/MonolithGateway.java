import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;

public class MonolithGateway {
    private static final Set<String> PROXY_PREFIXES = Set.of(
            "/api/",
            "/mock/",
            "/internal/",
            "/actuator/",
            "/v3/",
            "/swagger-ui"
    );
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

    private final Path staticRoot;
    private final URI backendBase;
    private final HttpClient client;

    public MonolithGateway(Path staticRoot, URI backendBase) {
        this.staticRoot = staticRoot.toAbsolutePath().normalize();
        this.backendBase = backendBase;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("GATEWAY_PORT", "8081"));
        URI backendBase = URI.create(System.getenv().getOrDefault("BACKEND_URL", "http://127.0.0.1:8082"));
        Path staticRoot = Path.of(System.getenv().getOrDefault("STATIC_ROOT", "/app/static"));

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        MonolithGateway gateway = new MonolithGateway(staticRoot, backendBase);
        server.createContext("/", gateway::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.printf("Monolith gateway started on :%d, backend=%s, static=%s%n", port, backendBase, staticRoot);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (shouldProxy(path)) {
                proxy(exchange);
                return;
            }
            serveFrontend(exchange, path);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            send(exchange, 502, "Gateway interrupted".getBytes());
        } catch (Exception e) {
            send(exchange, 502, ("Gateway error: " + e.getMessage()).getBytes());
        }
    }

    private boolean shouldProxy(String path) {
        if ("/swagger-ui.html".equals(path)) {
            return true;
        }
        return PROXY_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private void serveFrontend(HttpExchange exchange, String requestPath) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method not allowed".getBytes());
            return;
        }

        Path file = resolveStaticFile(requestPath);
        if (file == null || !Files.exists(file) || Files.isDirectory(file)) {
            file = staticRoot.resolve("index.html").normalize();
        }
        if (!file.startsWith(staticRoot) || !Files.exists(file)) {
            send(exchange, 404, "Not found".getBytes());
            return;
        }

        byte[] body = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType(file));
        send(exchange, 200, body);
    }

    private Path resolveStaticFile(String requestPath) {
        String normalized = requestPath == null || requestPath.isBlank() || "/".equals(requestPath)
                ? "/index.html"
                : requestPath;
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        Path file = staticRoot.resolve(normalized).normalize();
        return file.startsWith(staticRoot) ? file : null;
    }

    private void proxy(HttpExchange exchange) throws IOException, InterruptedException {
        URI requestUri = exchange.getRequestURI();
        String target = backendBase.toString().replaceAll("/+$", "") + requestUri.getRawPath();
        if (requestUri.getRawQuery() != null && !requestUri.getRawQuery().isBlank()) {
            target += "?" + requestUri.getRawQuery();
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(target))
                .timeout(Duration.ofSeconds(60));
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                values.forEach(value -> builder.header(name, value));
            }
        });

        byte[] requestBody;
        try (InputStream input = exchange.getRequestBody()) {
            requestBody = input.readAllBytes();
        }
        builder.method(exchange.getRequestMethod(), HttpRequest.BodyPublishers.ofByteArray(requestBody));

        HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        Headers headers = exchange.getResponseHeaders();
        response.headers().map().forEach((name, values) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                values.forEach(value -> headers.add(name, value));
            }
        });
        send(exchange, response.statusCode(), response.body());
    }

    private String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private void send(HttpExchange exchange, int status, byte[] body) throws IOException {
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
