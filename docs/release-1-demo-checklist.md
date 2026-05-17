# MSaaS Release 1.0 Demo Checklist

## Start

```powershell
cd D:\MSaaS
docker compose up -d --build
docker compose up -d --scale runtime=3
docker compose ps
```

Open:

- UI: http://localhost:5173
- API health: http://localhost:8081/actuator/health
- Swagger UI: http://localhost:8081/swagger-ui.html

## Demo Flow

1. Register `admin@example.com` to get admin access.
2. Create a project.
3. Upload `docs/demo-openapi.yaml` or paste your own OpenAPI contract.
4. Open Specifications, inspect routes, and generate a response preview with a seed.
5. Publish a `STATEFUL` mock instance.
6. Open Runtime and call the public mock URL from the built-in request panel.
7. Add a response scenario preset, then call the route and show that the scenario wins.
8. Add a response rule preset, then call a generated response and show the changed field.
9. Create or activate a `qa`/fault profile and show a `503` response without rotating the mock URL.
10. Open Logs and filter by source: Scenario, Field rule, Schema generated, Fallback.
11. Open Admin and show workers, active slots, unmatched ratio, rate-limit events, users, projects, logs, and audit.

## Verification

```powershell
cd D:\MSaaS\ui
npm run build
npm run test:e2e
npm run test:load
```

```powershell
cd D:\MSaaS
docker compose build server
docker compose logs --tail 160 server runtime
```

## Commit Message

```text
feat: prepare MSaaS OpenAPI release 1.0
```

Recommended body:

```text
Stabilize the OpenAPI-first mock platform with smart schema responses,
response scenarios, field rules, environment/fault profiles, observable
response sources in logs, runtime worker health, release smoke tests,
load checks, and demo-ready documentation.
```
