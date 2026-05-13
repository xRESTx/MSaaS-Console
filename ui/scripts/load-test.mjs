const API_BASE = process.env.API_BASE_URL ?? "http://localhost:8081";

const config = {
  users: intEnv("LOAD_USERS", 6),
  requestsPerUser: intEnv("LOAD_REQUESTS_PER_USER", 40),
  concurrency: intEnv("LOAD_CONCURRENCY", 24),
  timeoutMs: intEnv("LOAD_TIMEOUT_MS", 20_000),
  highRateLimit: intEnv("LOAD_HIGH_RATE_LIMIT", 1_000),
  lowRateLimit: intEnv("LOAD_LOW_RATE_LIMIT", 2),
  lowRateWindowSeconds: intEnv("LOAD_LOW_RATE_WINDOW_SECONDS", 60)
};

const runId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
const latencies = [];
const statuses = new Map();

function intEnv(name, fallback) {
  const value = Number.parseInt(process.env[name] ?? "", 10);
  return Number.isFinite(value) && value > 0 ? value : fallback;
}

function assert(condition, message, details = undefined) {
  if (!condition) {
    const suffix = details === undefined ? "" : `\n${JSON.stringify(details, null, 2)}`;
    throw new Error(`${message}${suffix}`);
  }
}

function record(status, durationMs) {
  latencies.push(durationMs);
  statuses.set(status, (statuses.get(status) ?? 0) + 1);
}

async function request(pathOrUrl, options = {}) {
  const url = pathOrUrl.startsWith("http") ? pathOrUrl : `${API_BASE}${pathOrUrl}`;
  const method = options.method ?? "GET";
  const headers = {
    ...(options.body === undefined ? {} : { "Content-Type": "application/json" }),
    ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
    ...(options.headers ?? {})
  };
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), config.timeoutMs);
  const startedAt = performance.now();
  try {
    const response = await fetch(url, {
      method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: controller.signal
    });
    const text = await response.text();
    const durationMs = performance.now() - startedAt;
    record(response.status, durationMs);

    let body = null;
    if (text) {
      try {
        body = JSON.parse(text);
      } catch {
        body = text;
      }
    }

    if (options.expectStatus !== undefined && response.status !== options.expectStatus) {
      throw new Error(`${options.label ?? method} expected ${options.expectStatus}, got ${response.status}: ${text.slice(0, 500)}`);
    }
    if (options.expectStatus === undefined && !response.ok) {
      throw new Error(`${options.label ?? method} failed with ${response.status}: ${text.slice(0, 500)}`);
    }
    return { response, body, text, status: response.status, durationMs };
  } finally {
    clearTimeout(timeout);
  }
}

function sampleSpec(userIndex) {
  return `
openapi: 3.0.3
info:
  title: Load API ${userIndex}
  version: 1.0.0
paths:
  /orders:
    get:
      operationId: listOrders
      parameters:
        - in: query
          name: tenant
          required: true
          schema:
            type: string
      responses:
        "200":
          description: OK
          content:
            application/json:
              examples:
                paid:
                  value:
                    kind: named-example
                    paid: true
                    tenant: "{{query.tenant}}"
                unpaid:
                  value:
                    kind: named-example
                    paid: false
                    tenant: "{{query.tenant}}"
            text/plain:
              example: "orders for {{query.tenant}}"
    post:
      operationId: createOrder
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
      responses:
        "201":
          description: Created
          content:
            application/json:
              example:
                id: generated
                title: Created order
  /orders/special:
    get:
      operationId: getSpecialOrder
      responses:
        "200":
          description: OK
          content:
            application/json:
              example:
                route: exact
                id: special
  /orders/{id}:
    get:
      operationId: getOrder
      responses:
        "200":
          description: OK
          content:
            application/json:
              example:
                id: "{{path.id}}"
                route: templated
  /health:
    get:
      operationId: health
      responses:
        "200":
          description: OK
          content:
            application/json:
              example:
                ok: true
`;
}

async function registerUser(index) {
  const username = `load_${runId}_${index}`.replace(/[^A-Za-z0-9_.-]/g, "_").slice(0, 32);
  const auth = await request("/api/auth/register", {
    method: "POST",
    label: `register user ${index}`,
    body: {
      email: `load-${runId}-${index}@example.com`,
      username,
      password: "password"
    }
  });
  return { index, username, email: auth.body.user.email, token: auth.body.token };
}

async function createProjectContext(user) {
  const project = await request("/api/projects", {
    method: "POST",
    token: user.token,
    label: `create project ${user.index}`,
    body: {
      name: `Load ${runId} U${user.index}`,
      description: "Generated by ui/scripts/load-test.mjs"
    }
  });

  const version = await request(`/api/projects/${project.body.id}/spec-versions`, {
    method: "POST",
    token: user.token,
    label: `upload spec ${user.index}`,
    body: {
      name: `load-openapi-${user.index}.yaml`,
      source: sampleSpec(user.index)
    }
  });
  assert(version.body.status === "VALID", "Uploaded spec must be valid", version.body);

  const routes = await request(`/api/spec-versions/${version.body.id}/routes`, {
    token: user.token,
    label: `route explorer ${user.index}`
  });
  assert(routes.body.some((route) => route.operationId === "listOrders"), "Route explorer must include listOrders");
  assert(routes.body.some((route) => route.pathTemplate === "/orders/special"), "Route explorer must include exact route");

  const scenario = await publishInstance(user, version.body.id, "STATELESS", false, "scenario");
  const stateful = await publishInstance(user, version.body.id, "STATEFUL", false, "stateful");
  const protectedInstance = await publishInstance(user, version.body.id, "STATELESS", true, "protected");
  const limited = await publishInstance(user, version.body.id, "STATELESS", false, "limited");

  await setRateLimit(user, scenario.body.id, config.highRateLimit, 60);
  await setRateLimit(user, stateful.body.id, config.highRateLimit, 60);
  await setRateLimit(user, protectedInstance.body.id, config.highRateLimit, 60);
  await setRateLimit(user, limited.body.id, config.lowRateLimit, config.lowRateWindowSeconds);

  await request(`/api/instances/${scenario.body.id}/scenarios`, {
    method: "POST",
    token: user.token,
    label: `create scenario ${user.index}`,
    body: {
      name: `Load scenario ${user.index}`,
      enabled: true,
      priority: 500,
      operationId: "listOrders",
      statusCode: 202,
      contentType: "application/json",
      body: {
        kind: "scenario",
        owner: user.username,
        tenant: "{{query.tenant}}",
        trace: "{{header.x-trace-id}}",
        id: "{{uuid}}"
      },
      headers: { "X-Load-Scenario": "{{query.tenant}}" },
      delayMs: 0
    }
  });

  return {
    user,
    project: project.body,
    version: version.body,
    scenario: scenario.body,
    stateful: stateful.body,
    protectedInstance: protectedInstance.body,
    limited: limited.body
  };
}

async function publishInstance(user, versionId, mode, requireApiKey, label) {
  const instance = await request(`/api/spec-versions/${versionId}/publish`, {
    method: "POST",
    token: user.token,
    label: `publish ${label} ${user.index}`,
    body: { mode, requireApiKey }
  });
  assert(instance.body.publicUrl?.includes("/mock/"), `Published ${label} instance must expose a public URL`, instance.body);
  if (requireApiKey) {
    assert(instance.body.mockApiKey, "Protected instance must return one-time mock API key", instance.body);
  }
  return instance;
}

async function setRateLimit(user, instanceId, requests, windowSeconds) {
  await request(`/api/instances/${instanceId}/settings`, {
    method: "PATCH",
    token: user.token,
    label: `settings ${instanceId}`,
    body: {
      rateLimitEnabled: true,
      rateLimitRequests: requests,
      rateLimitWindowSeconds: windowSeconds
    }
  });
}

async function verifyAccessIsolation(contexts) {
  if (contexts.length < 2) {
    return;
  }
  await request(`/api/projects/${contexts[0].project.id}`, {
    token: contexts[1].user.token,
    expectStatus: 404,
    label: "other user cannot read project"
  });
  await request(`/api/instances/${contexts[0].scenario.id}`, {
    token: contexts[1].user.token,
    expectStatus: 404,
    label: "other user cannot read instance"
  });
}

async function verifyLowRateLimit(ctx) {
  const ip = `10.77.${ctx.user.index}.1`;
  const url = `${ctx.limited.publicUrl}/orders?tenant=limited-${ctx.user.index}`;
  await request(url, { headers: { "X-Forwarded-For": ip }, expectStatus: 200, label: "low limit first call" });
  await request(url, { headers: { "X-Forwarded-For": ip }, expectStatus: 200, label: "low limit second call" });
  const limited = await request(url, { headers: { "X-Forwarded-For": ip }, expectStatus: 429, label: "low limit third call" });
  assert(limited.response.headers.get("Retry-After"), "429 response must include Retry-After");
  await request(url, {
    headers: { "X-Forwarded-For": `10.77.${ctx.user.index}.2` },
    expectStatus: 200,
    label: "low limit different client"
  });
}

async function verifyLogs(ctx) {
  const logs = await request(`/api/instances/${ctx.limited.id}/logs?limit=20`, {
    token: ctx.user.token,
    label: `logs ${ctx.user.index}`
  });
  assert(logs.body.some((log) => log.error === "Rate limit exceeded"), "Rate limit event must be written to logs");
}

function buildTasks(contexts) {
  const tasks = [];
  for (const ctx of contexts) {
    for (let i = 0; i < config.requestsPerUser; i += 1) {
      tasks.push(async () => {
        const tenant = `tenant-${ctx.user.index}-${i}`;
        const trace = `trace-${ctx.user.index}-${i}`;
        const ip = `10.${ctx.user.index}.${Math.floor(i / 200)}.${(i % 200) + 20}`;
        const lane = i % 6;

        if (lane === 0) {
          const result = await request(`${ctx.scenario.publicUrl}/orders?tenant=${encodeURIComponent(tenant)}`, {
            headers: { "X-Trace-Id": trace, "X-Forwarded-For": ip },
            expectStatus: 202,
            label: `scenario ${ctx.user.index}/${i}`
          });
          assert(result.body.kind === "scenario", "Scenario response kind must match", result.body);
          assert(result.body.owner === ctx.user.username, "Scenario must stay inside owning project", result.body);
          assert(result.body.tenant === tenant, "Scenario template must render query.tenant", result.body);
          assert(result.body.trace === trace, "Scenario template must render header.x-trace-id", result.body);
          return;
        }

        if (lane === 1) {
          const created = await request(`${ctx.stateful.publicUrl}/orders`, {
            method: "POST",
            headers: { "X-Forwarded-For": ip },
            body: { title: `Load order ${ctx.user.index}/${i}`, owner: ctx.user.username },
            expectStatus: 201,
            label: `stateful create ${ctx.user.index}/${i}`
          });
          assert(created.body.id, "Stateful create must return an id", created.body);
          const fetched = await request(`${ctx.stateful.publicUrl}/orders/${created.body.id}`, {
            headers: { "X-Forwarded-For": ip },
            expectStatus: 200,
            label: `stateful fetch ${ctx.user.index}/${i}`
          });
          assert(fetched.body.id === created.body.id, "Stateful fetch must return the created item", fetched.body);
          return;
        }

        if (lane === 2) {
          const result = await request(`${ctx.protectedInstance.publicUrl}/orders?tenant=${encodeURIComponent(tenant)}&__example=paid`, {
            headers: {
              "X-Forwarded-For": ip,
              "X-Mock-Api-Key": ctx.protectedInstance.mockApiKey
            },
            expectStatus: 200,
            label: `api key named example ${ctx.user.index}/${i}`
          });
          assert(result.body.kind === "named-example", "Named example must be selected", result.body);
          assert(result.body.paid === true, "Named example must return paid=true", result.body);
          assert(result.body.tenant === tenant, "OpenAPI example template must render query.tenant", result.body);
          return;
        }

        if (lane === 3) {
          const denied = await request(`${ctx.protectedInstance.publicUrl}/orders?tenant=${encodeURIComponent(tenant)}`, {
            headers: { "X-Forwarded-For": ip },
            expectStatus: 401,
            label: `api key denied ${ctx.user.index}/${i}`
          });
          assert(denied.body.error === "Mock API key is required", "Missing API key must be rejected", denied.body);
          return;
        }

        if (lane === 4) {
          const exact = await request(`${ctx.protectedInstance.publicUrl}/orders/special`, {
            headers: {
              "X-Forwarded-For": ip,
              "X-Mock-Api-Key": ctx.protectedInstance.mockApiKey
            },
            expectStatus: 200,
            label: `exact route ${ctx.user.index}/${i}`
          });
          assert(exact.body.route === "exact", "Exact route must win over templated route", exact.body);
          return;
        }

        const text = await request(`${ctx.protectedInstance.publicUrl}/orders?tenant=${encodeURIComponent(tenant)}`, {
          headers: {
            "X-Forwarded-For": ip,
            "X-Mock-Api-Key": ctx.protectedInstance.mockApiKey,
            "Accept": "text/plain"
          },
          expectStatus: 200,
          label: `accept text ${ctx.user.index}/${i}`
        });
        assert(text.response.headers.get("content-type")?.includes("text/plain"), "Accept text/plain must select text content", text.text);
        assert(text.text.includes(tenant), "Text/plain response must render query template", text.text);
      });
    }
  }
  return tasks.sort(() => Math.random() - 0.5);
}

async function runPool(tasks, concurrency) {
  let next = 0;
  const failures = [];
  const workers = Array.from({ length: Math.min(concurrency, tasks.length) }, async () => {
    while (next < tasks.length) {
      const current = next;
      next += 1;
      try {
        await tasks[current]();
      } catch (error) {
        failures.push(error);
      }
    }
  });
  await Promise.all(workers);
  if (failures.length > 0) {
    throw new Error(`${failures.length} load task(s) failed:\n${failures.slice(0, 10).map((error) => `- ${error.message}`).join("\n")}`);
  }
}

function percentile(values, p) {
  if (values.length === 0) {
    return 0;
  }
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return Math.round(sorted[index]);
}

function statusSummary() {
  return [...statuses.entries()]
    .sort(([a], [b]) => a - b)
    .map(([status, count]) => `${status}:${count}`)
    .join(" ");
}

console.log(`MSaaS load test starting against ${API_BASE}`);
console.log(`run=${runId} users=${config.users} requestsPerUser=${config.requestsPerUser} concurrency=${config.concurrency}`);

await request("/actuator/health", { label: "health" });

const users = await Promise.all(Array.from({ length: config.users }, (_, index) => registerUser(index + 1)));
const contexts = await Promise.all(users.map(createProjectContext));

await verifyAccessIsolation(contexts);
await Promise.all(contexts.map(verifyLowRateLimit));

const tasks = buildTasks(contexts);
await runPool(tasks, config.concurrency);

await Promise.all(contexts.map(verifyLogs));

const totalRequests = latencies.length;
console.log("MSaaS load test passed");
console.log(`requests=${totalRequests} users=${config.users} projects=${contexts.length} instances=${contexts.length * 4}`);
console.log(`latencyMs p50=${percentile(latencies, 50)} p95=${percentile(latencies, 95)} p99=${percentile(latencies, 99)}`);
console.log(`statuses ${statusSummary()}`);
