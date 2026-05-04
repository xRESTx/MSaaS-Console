# MSaaS Console

**MSaaS Console** is a backend-first Mock Server as a Service platform for creating, publishing, and diagnosing mock APIs from OpenAPI contracts.

Repository description for GitHub:

```text
Backend-first Mock Server as a Service platform with private projects, OpenAPI validation, warm Java runtime slots, public mock links, request logs, and a React console.
```

## Overview

The project helps developers and testers continue frontend, integration, and CI/CD work before a real backend service is ready. A user creates a private project, uploads an OpenAPI specification, publishes a mock instance, and receives a long public URL that can be used by a client application as a temporary API dependency.

The current version is focused on a working backend and a practical web console:

- private users and projects;
- OpenAPI upload, parsing, validation, and normalization;
- mock instance publishing by long public link;
- Java warm runtime slots instead of one container per instance;
- basic stateless and stateful CRUD behavior;
- request logs and diagnostics;
- token rotation and state reset;
- deletion of projects, specification versions, and instances with confirmation;
- light/dark theme;
- Russian/English UI;
- adaptive React interface.

## Architecture

The platform follows a hybrid architecture:

- **Control API** manages users, projects, OpenAPI versions, publications, and logs.
- **Mock Runtime** serves public mock traffic through warm in-process runtime slots.
- **MongoDB** stores users, projects, specifications, mock instances, and request logs.
- **React UI** provides a dashboard for the full workflow.

Published mock URLs look like this:

```text
http://localhost:8081/mock/{publicToken}/...
```

Project management and logs require JWT authentication. Calling a mock URL requires only the long public token.

## Tech Stack

Backend:

- Java 25 LTS
- Spring Boot 4
- Spring Security
- Spring Data MongoDB
- Swagger Parser
- JWT authentication

Frontend:

- React
- TypeScript
- Vite
- lucide-react icons
- CSS variables for light/dark themes

Infrastructure:

- Docker
- Docker Compose
- MongoDB 8

## Project Structure

```text
.
├── docker-compose.yml
├── README.md
├── server
│   ├── Dockerfile
│   ├── pom.xml
│   └── src
│       ├── main/java/com/msaas
│       │   ├── auth
│       │   ├── config
│       │   ├── instance
│       │   ├── log
│       │   ├── project
│       │   ├── runtime
│       │   ├── security
│       │   ├── spec
│       │   └── user
│       └── test/java/com/msaas
└── ui
    ├── Dockerfile
    ├── package.json
    └── src
```

## Running With Docker Compose

Start the full stack:

```powershell
docker compose up -d --build
```

Open:

- UI: http://localhost:5173
- Server health: http://localhost:8081/actuator/health
- MongoDB: `localhost:27017`

Check containers:

```powershell
docker compose ps
```

View logs:

```powershell
docker compose logs -f server
docker compose logs -f ui
```

Stop the stack:

```powershell
docker compose down
```

Stop and remove MongoDB data:

```powershell
docker compose down -v
```

## Running Locally

Backend requires Java 25 and Maven:

```powershell
cd server
mvn spring-boot:run
```

Frontend:

```powershell
cd ui
npm install
npm run dev
```

When running locally, make sure MongoDB is available and set:

```powershell
$env:MONGODB_URI="mongodb://localhost:27017/msaas"
$env:JWT_SECRET="replace-this-development-secret-with-at-least-32-bytes"
$env:APP_PUBLIC_BASE_URL="http://localhost:8081"
$env:APP_CORS_ALLOWED_ORIGINS="http://localhost:5173"
```

## Basic Workflow

1. Open http://localhost:5173.
2. Register or log in.
3. Create a project.
4. Upload an OpenAPI YAML or JSON specification.
5. Check that the version is marked as valid.
6. Publish a mock instance in `STATELESS` or `STATEFUL` mode.
7. Copy the public mock URL.
8. Send test requests from the UI, Postman, curl, PowerShell, or a client application.
9. Review request logs and matching diagnostics.

## Example Mock Calls

After publishing an instance, copy its public URL:

```powershell
$mock = "http://localhost:8081/mock/YOUR_PUBLIC_TOKEN"
```

Call a list endpoint:

```powershell
Invoke-RestMethod "$mock/orders"
```

Create a resource:

```powershell
$order = Invoke-RestMethod "$mock/orders" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"title":"First order","paid":false}'
```

Read the created resource:

```powershell
Invoke-RestMethod "$mock/orders/$($order.id)"
```

Update it:

```powershell
Invoke-RestMethod "$mock/orders/$($order.id)" `
  -Method Put `
  -ContentType "application/json" `
  -Body '{"title":"Updated order","paid":true}'
```

Delete it:

```powershell
Invoke-WebRequest "$mock/orders/$($order.id)" -Method Delete
```

## Main API Endpoints

Authentication:

- `POST /api/auth/register`
- `POST /api/auth/login`

Projects:

- `POST /api/projects`
- `GET /api/projects`
- `GET /api/projects/{projectId}`
- `DELETE /api/projects/{projectId}`

Specification versions:

- `POST /api/projects/{projectId}/spec-versions`
- `GET /api/projects/{projectId}/spec-versions`
- `DELETE /api/spec-versions/{versionId}`

Mock instances:

- `POST /api/spec-versions/{versionId}/publish`
- `GET /api/projects/{projectId}/instances`
- `GET /api/instances/{instanceId}`
- `POST /api/instances/{instanceId}/reset-state`
- `POST /api/instances/{instanceId}/rotate-token`
- `DELETE /api/instances/{instanceId}`
- `GET /api/instances/{instanceId}/logs`

Runtime:

- `ANY /mock/{publicToken}/**`

## Deletion Rules

Deletion is intentionally conservative:

- deleting a project requires typing the exact project name in the UI;
- deleting a project removes its specification versions, mock instances, runtime slots, and logs;
- deleting a specification version removes related mock instances and their logs;
- deleting a mock instance removes its runtime slot and logs;
- all delete operations verify that the current user owns the project.

## Testing

Backend tests are executed during Docker image build:

```powershell
docker compose build server
```

Frontend build:

```powershell
cd ui
npm run build
```

Full stack smoke check:

```powershell
docker compose up -d --build
Invoke-WebRequest -UseBasicParsing http://localhost:8081/actuator/health
Invoke-WebRequest -UseBasicParsing http://localhost:5173
```

## Git Commit And Push

This folder is not initialized as a Git repository by default. To create the first commit and push it to a remote repository by URL:

```powershell
cd D:\MSaaS
git init
git add .
git commit -m "feat: implement backend-first MSaaS prototype"
git branch -M main
git remote add origin <PASTE_YOUR_REPOSITORY_URL_HERE>
git push -u origin main
```

Example with an HTTPS GitHub URL:

```powershell
git remote add origin https://github.com/your-username/msaas-console.git
git push -u origin main
```

If the remote already exists:

```powershell
git remote set-url origin https://github.com/your-username/msaas-console.git
git push -u origin main
```

Recommended commit message:

```text
feat: implement backend-first MSaaS prototype
```

Longer commit body:

```text
Add Spring Boot control API, warm-slot mock runtime, MongoDB persistence,
private projects, OpenAPI parsing, public mock links, request logs,
delete flows, and a responsive React console with themes and ru/en UI.
```

## Current Limitations

- OpenAPI REST is supported first; GraphQL is planned as a future extension.
- Runtime slots currently run inside one server process.
- Redis-based distributed state and multi-worker routing are not included yet.
- WASM is planned as a future extension point for sandboxed response transformers.
