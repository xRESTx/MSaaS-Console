const API_BASE = process.env.API_BASE_URL ?? "http://localhost:8081";
const UI_BASE = process.env.UI_BASE_URL ?? "http://localhost:5173";

const stamp = Date.now();
let token = "";

async function api(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers ?? {})
    }
  });
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new Error(`${options.method ?? "GET"} ${path} -> ${response.status}: ${text}`);
  }
  return body;
}

async function expectStatus(url, status) {
  const response = await fetch(url);
  if (response.status !== status) {
    throw new Error(`${url} expected ${status}, got ${response.status}: ${await response.text()}`);
  }
  return response;
}

async function expectUiRoute(path) {
  const response = await fetch(`${UI_BASE}${path}`);
  const text = await response.text();
  if (!response.ok || !text.includes("<div id=\"root\">")) {
    throw new Error(`UI route ${path} did not return the SPA shell`);
  }
}

const spec = `
openapi: 3.0.3
info:
  title: Smoke API
  version: 1.0.0
paths:
  /orders:
    get:
      operationId: listOrders
      responses:
        "200":
          description: OK
          content:
            application/json:
              examples:
                paid:
                  value:
                    id: ord_1
                    paid: true
  /orders/{id}:
    get:
      operationId: getOrder
      responses:
        "200":
          description: OK
          content:
            application/json:
              schema:
                type: object
                properties:
                  id:
                    type: string
                  title:
                    type: string
`;

await expectUiRoute("/");
await expectUiRoute("/login");
await expectUiRoute("/register");
await expectUiRoute("/console/runtime");

const auth = await api("/api/auth/register", {
  method: "POST",
  body: JSON.stringify({
    email: `smoke-${stamp}@example.com`,
    username: `smoke-${stamp}`,
    password: "password"
  })
});
token = auth.token;

const project = await api("/api/projects", {
  method: "POST",
  body: JSON.stringify({ name: `Smoke ${stamp}`, description: "E2E smoke project" })
});
const version = await api(`/api/projects/${project.id}/spec-versions`, {
  method: "POST",
  body: JSON.stringify({ name: "smoke-openapi.yaml", source: spec })
});
if (version.status !== "VALID") {
  throw new Error(`Expected VALID spec, got ${version.status}`);
}

const routes = await api(`/api/spec-versions/${version.id}/routes`);
if (!routes.some((route) => route.operationId === "listOrders")) {
  throw new Error("Route explorer endpoint did not expose listOrders");
}

const instance = await api(`/api/spec-versions/${version.id}/publish`, {
  method: "POST",
  body: JSON.stringify({ mode: "STATEFUL", requireApiKey: false })
});

await api(`/api/instances/${instance.id}/settings`, {
  method: "PATCH",
  body: JSON.stringify({ rateLimitEnabled: true, rateLimitRequests: 2, rateLimitWindowSeconds: 60 })
});

await api(`/api/instances/${instance.id}/scenarios`, {
  method: "POST",
  body: JSON.stringify({
    name: "Smoke scenario",
    enabled: true,
    priority: 200,
    operationId: "listOrders",
    statusCode: 202,
    contentType: "application/json",
    body: { id: "{{query.name}}", requestId: "{{uuid}}" },
    headers: {},
    delayMs: 0
  })
});

const first = await expectStatus(`${instance.publicUrl}/orders?name=alice`, 202);
if (!(await first.text()).includes("alice")) {
  throw new Error("Scenario template did not render query variable");
}
await expectStatus(`${instance.publicUrl}/orders?name=bob`, 202);
const limited = await expectStatus(`${instance.publicUrl}/orders?name=carol`, 429);
if (!limited.headers.get("Retry-After")) {
  throw new Error("Rate limit response did not include Retry-After");
}

const logs = await api(`/api/instances/${instance.id}/logs`);
if (!logs.some((log) => log.error === "Rate limit exceeded")) {
  throw new Error("Rate limit event was not written to logs");
}

console.log("MSaaS E2E smoke passed");
