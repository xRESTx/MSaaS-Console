import { spawn } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const API_BASE = process.env.API_BASE_URL ?? "http://localhost:8081";
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "../..");

const config = {
  intervalMs: intEnv("AUTOSCALER_INTERVAL_MS", 5_000),
  requestTimeoutMs: intEnv("AUTOSCALER_REQUEST_TIMEOUT_MS", 10_000),
  cooldownMs: intEnv("AUTOSCALER_COOLDOWN_MS", 60_000),
  waitWorkersMs: intEnv("AUTOSCALER_WAIT_WORKERS_MS", 60_000),
  minWorkers: intEnv("AUTOSCALER_MIN_WORKERS", 1),
  maxWorkers: intEnv("AUTOSCALER_MAX_WORKERS", 6),
  scaleStep: intEnv("AUTOSCALER_SCALE_STEP", 2),
  maxSlotsPerWorker: intEnv("AUTOSCALER_MAX_SLOTS_PER_WORKER", 250),
  slotRatioPercent: intEnv("AUTOSCALER_SLOT_RATIO_PERCENT", 75),
  minTotalSlots: intEnv("AUTOSCALER_MIN_TOTAL_SLOTS", 1),
  dryRun: boolEnv("AUTOSCALER_DRY_RUN", false),
  once: boolEnv("AUTOSCALER_ONCE", false),
  allowTempUser: boolEnv("AUTOSCALER_ALLOW_TEMP_USER", true),
  rebalance: boolEnv("AUTOSCALER_REBALANCE", true),
  internalSecret: process.env.AUTOSCALER_INTERNAL_SECRET ?? "local-runtime-secret"
};

let token = process.env.AUTOSCALER_TOKEN ?? "";
let lastScaleAt = 0;
let stopped = false;

process.on("SIGINT", () => {
  stopped = true;
  console.log("Autoscaler stopping...");
});

process.on("SIGTERM", () => {
  stopped = true;
  console.log("Autoscaler stopping...");
});

function intEnv(name, fallback) {
  const value = Number.parseInt(process.env[name] ?? "", 10);
  return Number.isFinite(value) && value > 0 ? value : fallback;
}

function boolEnv(name, fallback) {
  const raw = process.env[name];
  if (raw === undefined || raw === "") {
    return fallback;
  }
  return ["1", "true", "yes", "on"].includes(raw.toLowerCase());
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function api(pathOrUrl, options = {}) {
  const url = pathOrUrl.startsWith("http") ? pathOrUrl : `${API_BASE}${pathOrUrl}`;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), config.requestTimeoutMs);
  try {
    const response = await fetch(url, {
      method: options.method ?? "GET",
      headers: {
        ...(options.body === undefined ? {} : { "Content-Type": "application/json" }),
        ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
        ...(options.headers ?? {})
      },
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: controller.signal
    });
    const text = await response.text();
    let body = null;
    if (text) {
      try {
        body = JSON.parse(text);
      } catch {
        body = text;
      }
    }
    if (!response.ok) {
      throw new Error(`${options.method ?? "GET"} ${url} -> ${response.status}: ${text.slice(0, 500)}`);
    }
    return body;
  } finally {
    clearTimeout(timeout);
  }
}

async function ensureToken() {
  if (token) {
    return token;
  }

  const identifier = process.env.AUTOSCALER_IDENTIFIER ?? process.env.AUTOSCALER_EMAIL;
  const password = process.env.AUTOSCALER_PASSWORD;
  if (identifier && password) {
    const auth = await api("/api/auth/login", {
      method: "POST",
      body: { identifier, password }
    });
    token = auth.token;
    return token;
  }

  if (!config.allowTempUser) {
    throw new Error("Set AUTOSCALER_TOKEN or AUTOSCALER_IDENTIFIER/AUTOSCALER_PASSWORD");
  }

  const stamp = `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
  const username = `autoscaler_${stamp}`.replace(/[^A-Za-z0-9_.-]/g, "_").slice(0, 32);
  const auth = await api("/api/auth/register", {
    method: "POST",
    body: {
      email: `autoscaler-${stamp}@example.com`,
      username,
      password: "password"
    }
  });
  token = auth.token;
  console.log(`Autoscaler registered temporary local user: ${auth.user.username ?? auth.user.email}`);
  return token;
}

async function runtimeWorkers() {
  const authToken = await ensureToken();
  return api("/api/runtime/workers", { token: authToken });
}

function liveWorkers(workers) {
  return workers.filter((worker) => worker.status === "UP" || worker.status === "DEGRADED");
}

function decideScale(workers) {
  const live = liveWorkers(workers);
  const liveCount = live.length;
  const threshold = Math.max(1, Math.floor(config.maxSlotsPerWorker * (config.slotRatioPercent / 100)));
  const totalSlots = live.reduce((sum, worker) => sum + Math.max(0, worker.slotCount ?? 0), 0);
  const hottest = live.reduce((max, worker) => Math.max(max, worker.slotCount ?? 0), 0);
  const averageSlots = liveCount === 0 ? 0 : totalSlots / liveCount;

  if (liveCount < config.minWorkers) {
    return {
      scale: true,
      target: config.minWorkers,
      reason: `live workers ${liveCount} below minimum ${config.minWorkers}`,
      liveCount,
      totalSlots,
      hottest,
      averageSlots,
      threshold
    };
  }

  const overloaded = totalSlots >= config.minTotalSlots && (hottest >= threshold || averageSlots >= threshold);
  if (!overloaded) {
    return {
      scale: false,
      target: liveCount,
      reason: `slot pressure is below threshold ${threshold}`,
      liveCount,
      totalSlots,
      hottest,
      averageSlots,
      threshold
    };
  }

  if (liveCount >= config.maxWorkers) {
    return {
      scale: false,
      target: liveCount,
      reason: `runtime is overloaded, but max workers ${config.maxWorkers} reached`,
      liveCount,
      totalSlots,
      hottest,
      averageSlots,
      threshold
    };
  }

  return {
    scale: true,
    target: Math.min(config.maxWorkers, liveCount + config.scaleStep),
    reason: `slot pressure reached: hottest=${hottest}, average=${averageSlots.toFixed(1)}, threshold=${threshold}`,
    liveCount,
    totalSlots,
    hottest,
    averageSlots,
    threshold
  };
}

async function tick() {
  const workers = await runtimeWorkers();
  const decision = decideScale(workers);
  const now = Date.now();
  const cooldownLeft = Math.max(0, lastScaleAt + config.cooldownMs - now);
  console.log(
    `${new Date().toISOString()} workers=${decision.liveCount}/${workers.length} slots=${decision.totalSlots} hottest=${decision.hottest} avg=${decision.averageSlots.toFixed(1)} threshold=${decision.threshold} target=${decision.target} reason="${decision.reason}"`
  );

  if (!decision.scale) {
    return;
  }

  if (cooldownLeft > 0) {
    console.log(`Scale skipped: cooldown ${Math.ceil(cooldownLeft / 1000)}s left.`);
    return;
  }

  await scaleRuntime(decision.target);
  lastScaleAt = Date.now();
  await waitForLiveWorkers(decision.target);
  if (config.rebalance) {
    await rebalanceRuntime();
  }
}

async function waitForLiveWorkers(expected) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < config.waitWorkersMs) {
    const workers = await runtimeWorkers();
    const liveCount = liveWorkers(workers).length;
    if (liveCount >= expected) {
      console.log(`Runtime scale confirmed: live workers=${liveCount}.`);
      return;
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

async function scaleRuntime(target) {
  if (config.dryRun) {
    console.log(`[dry-run] docker compose up -d --scale runtime=${target}`);
    return;
  }
  console.log(`Scaling runtime: docker compose up -d --scale runtime=${target}`);
  await runDockerCompose(["up", "-d", "--scale", `runtime=${target}`]);
}

async function rebalanceRuntime() {
  if (config.dryRun) {
    console.log("[dry-run] POST /api/runtime/rebalance");
    return;
  }
  const authToken = await ensureToken();
  const result = await api("/api/runtime/rebalance", {
    method: "POST",
    token: authToken,
    headers: { "X-MSaaS-Internal-Secret": config.internalSecret },
    body: {}
  });
  console.log(`Runtime rebalance completed: workers=${result.workerCount} instances=${result.instanceCount} reassigned=${result.reassignedCount}`);
  console.log(`Runtime distribution: ${JSON.stringify(result.distribution)}`);
}

console.log(`MSaaS runtime autoscaler started against ${API_BASE}`);
console.log(
  `min=${config.minWorkers} max=${config.maxWorkers} step=${config.scaleStep} threshold=${config.slotRatioPercent}% of ${config.maxSlotsPerWorker} slots dryRun=${config.dryRun} once=${config.once}`
);

await ensureToken();

do {
  try {
    await tick();
  } catch (error) {
    console.error(`Autoscaler tick failed: ${error.message}`);
  }
  if (config.once || stopped) {
    break;
  }
  await sleep(config.intervalMs);
} while (!stopped);
