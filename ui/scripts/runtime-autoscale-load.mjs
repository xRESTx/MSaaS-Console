import { spawn } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const API_BASE = process.env.API_BASE_URL ?? "http://localhost:8081";
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "../..");

const config = {
  initialWorkers: intEnv("AUTOSCALE_INITIAL_WORKERS", 1),
  addWorkers: intEnv("AUTOSCALE_ADD_WORKERS", 2),
  projects: intEnv("AUTOSCALE_PROJECTS", 8),
  instancesPerProject: intEnv("AUTOSCALE_INSTANCES_PER_PROJECT", 2),
  postScaleInstances: intEnv("AUTOSCALE_POST_SCALE_INSTANCES", 8),
  concurrency: intEnv("AUTOSCALE_CONCURRENCY", 160),
  durationMs: intEnv("AUTOSCALE_DURATION_MS", 45_000),
  warmupMs: intEnv("AUTOSCALE_WARMUP_MS", 8_000),
  monitorIntervalMs: intEnv("AUTOSCALE_MONITOR_INTERVAL_MS", 2_000),
  triggerP95Ms: intEnv("AUTOSCALE_TRIGGER_P95_MS", 220),
  triggerErrorRatePercent: intEnv("AUTOSCALE_TRIGGER_ERROR_RATE_PERCENT", 5),
  responseDelayMs: intEnv("AUTOSCALE_RESPONSE_DELAY_MS", 180),
  timeoutMs: intEnv("AUTOSCALE_TIMEOUT_MS", 20_000),
  waitWorkersMs: intEnv("AUTOSCALE_WAIT_WORKERS_MS", 60_000)
};

const runId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
const latencies = [];
const windowLatencies = [];
const statuses = new Map();
let totalRequests = 0;
let failedRequests = 0;
let scaled = false;
let scaleStartedAt = null;
let scaleFinishedAt = null;
let postScalePublished = false;
let token = "";
let loadTargets = [];

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

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function record(status, durationMs, failed = false) {
  totalRequests += 1;
  latencies.push(durationMs);
  windowLatencies.push(durationMs);
  statuses.set(status, (statuses.get(status) ?? 0) + 1);
  if (failed) {
    failedRequests += 1;
  }
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

    let body = null;
    if (text) {
      try {
        body = JSON.parse(text);
      } catch {
        body = text;
      }
    }

    const failed = options.expectStatus === undefined ? !response.ok : response.status !== options.expectStatus;
    if (options.record !== false) {
      record(response.status, durationMs, failed);
    }
    if (failed) {
      throw new Error(`${options.label ?? method} expected ${options.expectStatus ?? "2xx"}, got ${response.status}: ${text.slice(0, 500)}`);
    }
    return { response, body, text, status: response.status, durationMs };
  } catch (error) {
    const durationMs = performance.now() - startedAt;
    if (options.record !== false) {
      record(0, durationMs, true);
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

function sampleSpec(projectIndex) {
  return `
openapi: 3.0.3
info:
  title: Autoscale API ${projectIndex}
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
              schema:
                type: object
                required: [id, tenant, project]
                properties:
                  id:
                    type: string
                    format: uuid
                  tenant:
                    type: string
                  project:
                    type: string
                  total:
                    type: integer
                    format: int32
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
              schema:
                type: object
                required: [id, accepted]
                properties:
                  id:
                    type: string
                    format: uuid
                  accepted:
                    type: boolean
`;
}

async function registerLoadUser() {
  const username = `autoscale_${runId}`.replace(/[^A-Za-z0-9_.-]/g, "_").slice(0, 32);
  const auth = await request("/api/auth/register", {
    method: "POST",
    record: false,
    label: "register autoscale user",
    body: {
      email: `autoscale-${runId}@example.com`,
      username,
      password: "password"
    }
  });
  token = auth.body.token;
  return auth.body.user;
}

async function createProjectContext(index, label = "initial") {
  const project = await request("/api/projects", {
    method: "POST",
    token,
    record: false,
    label: `create ${label} project ${index}`,
    body: {
      name: `Autoscale ${runId} ${label} ${index}`,
      description: "Generated by runtime-autoscale-load.mjs"
    }
  });

  const version = await request(`/api/projects/${project.body.id}/spec-versions`, {
    method: "POST",
    token,
    record: false,
    label: `upload ${label} spec ${index}`,
    body: {
      name: `autoscale-${label}-${index}.yaml`,
      source: sampleSpec(index)
    }
  });
  assert(version.body.status === "VALID", "Uploaded spec must be valid", version.body);

  const instances = [];
  for (let instanceIndex = 0; instanceIndex < config.instancesPerProject; instanceIndex += 1) {
    const instance = await request(`/api/spec-versions/${version.body.id}/publish`, {
      method: "POST",
      token,
      record: false,
      label: `publish ${label} instance ${index}/${instanceIndex}`,
      body: { mode: instanceIndex % 2 === 0 ? "STATELESS" : "STATEFUL", requireApiKey: false }
    });
    assert(instance.body.publicUrl?.includes("/mock/"), "Published instance must expose public URL", instance.body);
    await request(`/api/instances/${instance.body.id}/settings`, {
      method: "PATCH",
      token,
      record: false,
      label: `settings ${label} instance ${index}/${instanceIndex}`,
      body: {
        rateLimitEnabled: true,
        rateLimitRequests: 100_000,
        rateLimitWindowSeconds: 60,
        smartResponsesEnabled: true,
        smartSeedMode: "STABLE"
      }
    });
    await request(`/api/instances/${instance.body.id}/scenarios`, {
      method: "POST",
      token,
      record: false,
      label: `slow scenario ${label} instance ${index}/${instanceIndex}`,
      body: {
        name: "Autoscale slow path",
        enabled: true,
        priority: 1000,
        operationId: "listOrders",
        statusCode: 200,
        contentType: "application/json",
        body: {
          source: "autoscale-scenario",
          project: project.body.name,
          tenant: "{{query.tenant}}",
          trace: "{{header.x-trace-id}}",
          requestId: "{{uuid}}"
        },
        headers: { "X-Autoscale-Test": label },
        delayMs: config.responseDelayMs
      }
    });
    instances.push(instance.body);
  }

  return { project: project.body, version: version.body, instances };
}

function addTargets(contexts, label) {
  for (const ctx of contexts) {
    for (const instance of ctx.instances) {
      loadTargets.push({ label, projectName: ctx.project.name, publicUrl: instance.publicUrl });
    }
  }
}

async function callMock(target, workerId, index) {
  const tenant = `${target.label}-${workerId}-${index}`;
  const url = `${target.publicUrl}/orders?tenant=${encodeURIComponent(tenant)}&__seed=${encodeURIComponent(tenant)}`;
  const result = await request(url, {
    headers: {
      "X-Trace-Id": `autoscale-${workerId}-${index}`,
      "X-Forwarded-For": `10.210.${workerId % 200}.${(index % 200) + 20}`
    },
    expectStatus: 200,
    label: `mock ${target.label}/${workerId}/${index}`
  });
  assert(result.body.source === "autoscale-scenario", "Scenario response must be returned", result.body);
}

async function runLoadUntil(deadlineMs) {
  let next = 0;
  const failures = [];
  const workers = Array.from({ length: config.concurrency }, async (_, workerId) => {
    while (performance.now() < deadlineMs) {
      const target = loadTargets[next % loadTargets.length];
      next += 1;
      try {
        await callMock(target, workerId + 1, next);
      } catch (error) {
        failures.push(error);
      }
    }
  });
  await Promise.all(workers);
  if (failures.length > 0) {
    console.warn(`load failures captured=${failures.length}`);
    console.warn(failures.slice(0, 5).map((error) => `- ${error.message}`).join("\n"));
  }
}

async function monitorAndScale(startedAt, targetWorkers) {
  let lastTotal = 0;
  while (performance.now() - startedAt < config.durationMs) {
    await sleep(config.monitorIntervalMs);
    const elapsed = Math.round(performance.now() - startedAt);
    const sample = windowLatencies.splice(0, windowLatencies.length);
    const sampleP95 = percentile(sample, 95);
    const newRequests = totalRequests - lastTotal;
    lastTotal = totalRequests;
    const errorRate = totalRequests === 0 ? 0 : (failedRequests / totalRequests) * 100;
    const currentWorkers = await workerSummary().catch(() => []);
    console.log(`t=${elapsed}ms workers=${currentWorkers.length} req=${totalRequests} +${newRequests} p95=${sampleP95}ms errors=${errorRate.toFixed(2)}%`);

    if (!scaled && elapsed >= config.warmupMs && (sampleP95 >= config.triggerP95Ms || errorRate >= config.triggerErrorRatePercent)) {
      scaled = true;
      scaleStartedAt = performance.now();
      console.log(`Autoscale trigger reached: p95=${sampleP95}ms errorRate=${errorRate.toFixed(2)}%. Scaling runtime to ${targetWorkers}.`);
      await scaleRuntime(targetWorkers);
      await waitForWorkers(targetWorkers);
      scaleFinishedAt = performance.now();
      console.log(`Runtime scale-up completed in ${Math.round(scaleFinishedAt - scaleStartedAt)}ms.`);
      await publishPostScaleTargets();
      postScalePublished = true;
    }
  }
}

async function publishPostScaleTargets() {
  const count = Math.max(1, config.postScaleInstances);
  const contexts = [];
  for (let i = 0; i < count; i += 1) {
    contexts.push(await createProjectContext(i + 1, "post-scale"));
  }
  addTargets(contexts, "post-scale");
  console.log(`Published post-scale contexts=${contexts.length}; total targets=${loadTargets.length}.`);
}

async function workerSummary() {
  const workers = await request("/api/runtime/workers", {
    token,
    record: false,
    label: "runtime workers"
  });
  return workers.body;
}

async function slotSummary() {
  const slots = await request("/api/runtime/slots", {
    token,
    record: false,
    label: "runtime slots"
  });
  return slots.body;
}

async function waitForWorkers(expected) {
  const startedAt = performance.now();
  while (performance.now() - startedAt < config.waitWorkersMs) {
    const workers = await workerSummary();
    const live = workers.filter((worker) => worker.status === "UP" || worker.status === "DEGRADED");
    if (live.length >= expected) {
      return workers;
    }
    await sleep(1_000);
  }
  throw new Error(`Timed out waiting for ${expected} live runtime workers`);
}

function runDockerCompose(args) {
  return new Promise((resolve, reject) => {
    const child = spawn("docker", ["compose", ...args], {
      cwd: repoRoot,
      stdio: "inherit",
      shell: process.platform === "win32"
    });
    child.on("error", reject);
    child.on("exit", (code) => {
      if (code === 0) {
        resolve();
        return;
      }
      reject(new Error(`docker compose ${args.join(" ")} exited with ${code}`));
    });
  });
}

async function scaleRuntime(count) {
  await runDockerCompose(["up", "-d", "--scale", `runtime=${count}`]);
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

console.log(`MSaaS runtime autoscale load starting against ${API_BASE}`);
console.log(`run=${runId} initialWorkers=${config.initialWorkers} addWorkers=${config.addWorkers} projects=${config.projects} instancesPerProject=${config.instancesPerProject} concurrency=${config.concurrency}`);

await request("/actuator/health", { record: false, label: "health" });
console.log(`Scaling runtime to initial baseline ${config.initialWorkers}.`);
await scaleRuntime(config.initialWorkers);

const user = await registerLoadUser();
console.log(`Registered load user ${user.username ?? user.email}. Waiting for baseline workers.`);
await waitForWorkers(config.initialWorkers);

const initialContexts = [];
for (let i = 0; i < config.projects; i += 1) {
  initialContexts.push(await createProjectContext(i + 1, "initial"));
}
addTargets(initialContexts, "initial");

const targetWorkers = config.initialWorkers + config.addWorkers;
const beforeWorkers = await workerSummary();
const beforeSlots = await slotSummary();
console.log(`Baseline workers=${beforeWorkers.length}; initial targets=${loadTargets.length}; visible slots=${beforeSlots.length}.`);

const startedAt = performance.now();
await Promise.all([
  runLoadUntil(startedAt + config.durationMs),
  monitorAndScale(startedAt, targetWorkers)
]);

assert(scaled, "Autoscale trigger was not reached. Lower AUTOSCALE_TRIGGER_P95_MS or increase AUTOSCALE_RESPONSE_DELAY_MS/concurrency.");
assert(postScalePublished, "Post-scale targets were not published");

const afterWorkers = await workerSummary();
const afterSlots = await slotSummary();
const liveAfter = afterWorkers.filter((worker) => worker.status === "UP" || worker.status === "DEGRADED");

assert(liveAfter.length >= targetWorkers, "Runtime worker count did not increase after scale-up", afterWorkers);
assert(totalRequests > 0, "Load phase did not send requests");

console.log("MSaaS runtime autoscale load passed");
console.log(`workers before=${beforeWorkers.length} after=${afterWorkers.length} target=${targetWorkers}`);
console.log(`slots before=${beforeSlots.length} after=${afterSlots.length}`);
console.log(`requests=${totalRequests} failures=${failedRequests}`);
console.log(`latencyMs p50=${percentile(latencies, 50)} p95=${percentile(latencies, 95)} p99=${percentile(latencies, 99)}`);
console.log(`statuses ${statusSummary()}`);
console.log(`scaleDurationMs=${scaleFinishedAt && scaleStartedAt ? Math.round(scaleFinishedAt - scaleStartedAt) : "n/a"}`);
