# LearningService

LearningService stores user learning state and code submission history for the StudyBytes platform.

The service owns:

- course enrollment state per user;
- module-level progress per user;
- task-level progress per user;
- code submission attempts;
- per-test execution/comparison results;
- score aggregation from task to module and course.

It does **not** own course structure or test authoring data. That belongs to CourseService.

## Tech stack

- Java 21
- Spring Boot 3.3.5
- Maven
- PostgreSQL
- Spring Data JPA
- Flyway
- Spring Validation
- Spring Security OAuth2 Resource Server (JWT + JWKS)

## Responsibility

LearningService answers:

> What is the current learning state for this user and how did their code submission perform?

LearningService does not answer:

> What is the canonical course/module/item definition?

For canonical course and test data, it calls CourseService internal API.

## Domain model

```text
CourseEnrollment
 └── ModuleProgress
      └── TaskProgress
           └── TaskSubmission
                └── SubmissionTestResult
```

Main status enums:

- `ProgressStatus`: `NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`, `LOCKED`
- `SubmissionStatus`: `QUEUED`, `RUNNING`, `FINISHED`, `FAILED`
- `SubmissionVerdict`: `OK`, `WA`, `RE`, `TL`, `ML`, `PE`
- `TestResultStatus`: `PASSED`, `FAILED`, `ERROR`, `SKIPPED`

## API overview

Base path: `/api/v1/learning`

### Progress API

```http
POST /course-enrollments
GET  /course-enrollments/{userId}/{courseId}
PUT  /course-enrollments/{userId}/{courseId}

POST /module-progress
GET  /module-progress/{userId}/{courseId}/{moduleId}
PUT  /module-progress/{userId}/{courseId}/{moduleId}

POST /task-progress
GET  /task-progress/{userId}/{courseId}/{moduleId}/{taskId}
PUT  /task-progress/{userId}/{courseId}/{moduleId}/{taskId}
```

Behavior notes:

- `POST` creates unique records (duplicate keys return `409 Conflict`).
- `GET` returns one record by composite identifiers.
- `PUT` partially updates mutable fields through DTOs.

### CodeRunner API

```http
POST /tasks/{taskId}/submissions
```

Required header:

```http
Authorization: Bearer <access_token>
```

Request body:

```json
{
  "taskId": 2,
  "language": "python",
  "sourceCode": "print(input())",
  "executionMode": "BATCH"
}
```

`executionMode` values:

- `BATCH` (default)
- `ONE_BY_ONE`

Accepted aliases for `ONE_BY_ONE`: `ONE-BY-ONE`, `ONE_BY_ONE`, `ONEBYONE`.

Response (`201 Created`):

```json
{
  "submissionId": 1,
  "status": "FINISHED",
  "verdict": "OK",
  "score": 100,
  "passedTestsCount": 2,
  "totalTestsCount": 2,
  "executorRequestId": "..."
}
```

## Code execution flow

For `POST /tasks/{taskId}/submissions`:

1. Validate JWT access token from `Authorization: Bearer <access_token>` via UserService JWKS.
2. Extract `userId` from JWT claim `sub`.
3. Load existing `TaskProgress` by `userId + taskId`.
4. Request execution package from CourseService:
   - `GET /api/v1/internal/course-items/{itemId}/execution-package`
   - header `X-Internal-API-Key` (configurable name/value).
5. Validate package consistency (`itemId`, `courseId`, `moduleId`, `itemType=CODING`, tests not empty).
6. Save `TaskSubmission` (`QUEUED` -> `RUNNING`).
7. Send code to CodeExecutorService:
   - `BATCH`: `POST /executions/batch`
   - `ONE_BY_ONE`: session-based flow:
     - `POST /executions`
     - `POST /executions/{id}/tests` for each test
     - `POST /executions/{id}/cancel` for cleanup
8. Compare outputs in LearningService using `evaluationPolicy` from CourseService package.
9. Save `SubmissionTestResult` rows.
10. Compute score/verdict and finish submission.
11. Update `TaskProgress`, then recalculate `ModuleProgress` and `CourseEnrollment`.

## External integrations

### UserService (JWT validation source of truth)

LearningService validates frontend access tokens as an OAuth2 Resource Server.

JWT expectations:

- signature: `RS256`
- `iss`: configured issuer
- `aud`: must include configured audience
- `exp`: must be valid
- `sub`: parsed as numeric `userId` for CodeRunner flow

Configured by:

- `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI`
- `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI`
- `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUDIENCES`

### CourseService (required)

Configured by:

- `CODERUNNER_COURSE_SERVICE_BASE_URL`
- `CODERUNNER_COURSE_SERVICE_INTERNAL_API_KEY_HEADER`
- `CODERUNNER_COURSE_SERVICE_INTERNAL_API_KEY`

Current default path used by LearningService:

```http
GET {courseServiceBaseUrl}/api/v1/internal/course-items/{itemId}/execution-package
```

### CodeExecutorService (required for code-runner scenario)

Configured by:

- `CODERUNNER_EXECUTOR_SERVICE_BASE_URL`
- `CODERUNNER_EXECUTOR_SERVICE_AUTH_TOKEN`

LearningService sends `Authorization: Bearer <token>`.

## Configuration

Primary config file: `src/main/resources/application.yml`

Environment-driven keys:

```text
SERVER_PORT
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SPRING_JPA_HIBERNATE_DDL_AUTO
SPRING_FLYWAY_BASELINE_ON_MIGRATE
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUDIENCES
CODERUNNER_COURSE_SERVICE_BASE_URL
CODERUNNER_COURSE_SERVICE_INTERNAL_API_KEY_HEADER
CODERUNNER_COURSE_SERVICE_INTERNAL_API_KEY
CODERUNNER_EXECUTOR_SERVICE_BASE_URL
CODERUNNER_EXECUTOR_SERVICE_AUTH_TOKEN
```

Current default health endpoint:

```http
GET /actuator/health
```

## Database and migrations

Flyway migrations:

- `V1__create_learning_progress_schema.sql`
- `V2__create_progress_schema_if_missing.sql`
- `V3__create_code_runner_tables_if_missing.sql`

Owned tables:

- `course_enrollments`
- `module_progress`
- `task_progress`
- `task_submissions`
- `submission_test_results`
- `flyway_schema_history`

Notes:

- `V2` exists to safely create progress schema in non-empty local DB states.
- `V3` creates/patches CodeRunner tables and backward-compatible columns.

## Local run

## Option A: Docker Compose (recommended for integration scenario)

This repository contains `docker-compose.yml` that starts:

1. `learning-postgres`
2. `code-executor-service` (from sibling repo `../CodeExecutorService`)
3. `learning-service`

Prerequisites:

- Docker Desktop running;
- repository `CodeExecutorService` located at `../CodeExecutorService` relative to this project;
- CourseService running and reachable by `CODERUNNER_COURSE_SERVICE_BASE_URL`;
- UserService running and reachable by `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` for JWT verification.

Start:

```powershell
docker compose up --build -d
```

Status and logs:

```powershell
docker compose ps
docker compose logs learning-service --tail 100
docker compose logs code-executor-service --tail 100
```

Stop:

```powershell
docker compose down
```

Stop + DB volume reset:

```powershell
docker compose down -v
```

## Option B: Run app directly with Maven

1. Start PostgreSQL (locally or dockerized).
2. Configure datasource/env variables.
3. Run:

```powershell
mvn spring-boot:run
```

## Build and test

Compile:

```powershell
mvn -DskipTests compile
```

Package:

```powershell
mvn -DskipTests package
```

Run tests:

```powershell
mvn test
```

## Manual end-to-end check (Postman)

1. Create progress records:
   - `POST /api/v1/learning/course-enrollments`
   - `POST /api/v1/learning/module-progress`
   - `POST /api/v1/learning/task-progress`
2. Submit code:
   - `POST /api/v1/learning/tasks/{taskId}/submissions`
   - with header `Authorization: Bearer <access_token>` where JWT `sub` is numeric user id.
   - token must pass `iss`, `aud`, `exp` validation.
3. Verify aggregates:
   - `GET /task-progress/{userId}/{courseId}/{moduleId}/{taskId}`
   - `GET /module-progress/{userId}/{courseId}/{moduleId}`
   - `GET /course-enrollments/{userId}/{courseId}`
4. Verify DB rows:

```powershell
docker compose exec learning-postgres psql -U postgres -d learning_service -c "select id,user_id,task_id,submission_number,status,verdict,score,passed_tests_count,total_tests_count,executor_request_id from task_submissions order by id desc limit 10;"
docker compose exec learning-postgres psql -U postgres -d learning_service -c "select submission_id,testkey,test_order,status,execution_time_ms,memory_kb from submission_test_results order by id desc limit 30;"
```

## Error model

Global error payload:

```json
{
  "timestamp": "2026-05-15T10:00:00",
  "status": 409,
  "error": "Conflict",
  "message": "...",
  "path": "/api/v1/learning/..."
}
```

Common statuses:

- `401` missing/invalid JWT for protected endpoints
- `400` validation/request format errors
- `404` entity not found
- `409` unique conflict on create
- `500` integration/runtime failure

## Known operational notes

- `409` on progress `POST` is expected when the same composite record already exists.
- If host port is occupied (for example `8090`), adjust `.env` host port mapping.
- If local DB state is stale, run `docker compose down -v` and start again.
- `target/` artifacts are local build outputs and should not be used as source-of-truth docs.

## Contract and helper files in repository

- `contracts.yaml` - progress API contract snapshot.
- `CodeRunnerCommands.md` - command-oriented run/check guide.
- `RunInfoRequests.md` - request examples for integration runs.
