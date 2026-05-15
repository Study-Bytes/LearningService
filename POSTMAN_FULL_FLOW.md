# Full Postman Check: UserService -> CourseService -> LearningService -> CodeExecutorService

This scenario assumes empty local databases and all services running:

- UserService: `http://localhost:8081`
- CourseService: `http://localhost:8082`
- LearningService: `http://localhost:8090`
- CodeExecutorService: `http://localhost:8095`

Use a Postman environment with these variables:

```text
user_base=http://localhost:8081
course_base=http://localhost:8082
learning_base=http://localhost:8090
executor_base=http://localhost:8095
teacher_email=teacher@example.com
student_email=student@example.com
password=strong-password
internal_api_key=change-me-internal-api-key
teacher_token=
student_token=
teacher_id=
student_id=
course_id=
module_id=
task_id=
session_id=
```

If your CourseService uses a different internal key, set `internal_api_key` to the same value as `COURSE_SERVICE_INTERNAL_API_KEY`.

## 1. Register Teacher

`POST {{user_base}}/api/v1/auth/register`

Headers:

```http
Content-Type: application/json
```

Body:

```json
{
  "email": "{{teacher_email}}",
  "password": "{{password}}",
  "fullName": "Teacher User"
}
```

Save `userId` as `teacher_id`.

Postman Tests:

```js
const json = pm.response.json();
pm.environment.set("teacher_id", json.userId);
```

## 2. Make Teacher In Database

The registration endpoint creates `STUDENT`. For admin CourseService endpoints, update the role in UserService database:

```bash
docker exec -it user-service-postgres psql -U postgres -d user_service
```

SQL:

```sql
UPDATE users SET role = 'TEACHER' WHERE email = 'teacher@example.com';
SELECT id, email, role FROM users WHERE email = 'teacher@example.com';
```

Log in after this change so the JWT contains `TEACHER`.

## 3. Login Teacher

`POST {{user_base}}/api/v1/auth/login`

Body:

```json
{
  "email": "{{teacher_email}}",
  "password": "{{password}}"
}
```

Postman Tests:

```js
const json = pm.response.json();
pm.environment.set("teacher_token", json.accessToken);
```

## 4. Register Student

`POST {{user_base}}/api/v1/auth/register`

Body:

```json
{
  "email": "{{student_email}}",
  "password": "{{password}}",
  "fullName": "Student User"
}
```

Postman Tests:

```js
const json = pm.response.json();
pm.environment.set("student_id", json.userId);
```

## 5. Login Student

`POST {{user_base}}/api/v1/auth/login`

Body:

```json
{
  "email": "{{student_email}}",
  "password": "{{password}}"
}
```

Postman Tests:

```js
const json = pm.response.json();
pm.environment.set("student_token", json.accessToken);
```

## 6. Check User `/me`

`GET {{user_base}}/api/v1/users/me`

Headers:

```http
Authorization: Bearer {{student_token}}
```

Expected: user DTO with `role: STUDENT`.

## 7. Create Course

`POST {{course_base}}/api/v1/admin/courses`

Headers:

```http
Content-Type: application/json
Authorization: Bearer {{teacher_token}}
```

Body:

```json
{
  "slug": "python-basics-postman",
  "title": "Python Basics Postman",
  "shortDescription": "Postman integration course",
  "description": "Created from an empty database for end-to-end testing.",
  "difficulty": "BEGINNER",
  "accessType": "PUBLIC",
  "enrollmentEnabled": true,
  "coverImageUrl": null,
  "estimatedMinutes": 30,
  "createdByUserId": {{teacher_id}}
}
```

Postman Tests:

```js
const json = pm.response.json();
pm.environment.set("course_id", json.id);
```

## 8. Create Module

`POST {{course_base}}/api/v1/admin/courses/{{course_id}}/modules`

Headers:

```http
Content-Type: application/json
Authorization: Bearer {{teacher_token}}
```

Body:

```json
{
  "title": "Intro",
  "description": "First module",
  "orderIndex": 0
}
```

Postman Tests:

```js
const json = pm.response.json();
pm.environment.set("module_id", json.id);
```

## 9. Create Coding Task

`POST {{course_base}}/api/v1/admin/modules/{{module_id}}/items`

Headers:

```http
Content-Type: application/json
Authorization: Bearer {{teacher_token}}
```

Body:

```json
{
  "title": "Sum two numbers",
  "itemType": "CODING",
  "statement": "Read two integers from stdin and print their sum.",
  "starterCode": "import sys\nnums = list(map(int, sys.stdin.read().split()))\nprint(sum(nums))",
  "language": "python",
  "orderIndex": 0,
  "timeLimitMs": 2000,
  "memoryLimitMb": 128,
  "outputLimitKb": 64,
  "networkDisabled": true,
  "readOnlyFs": true,
  "comparisonMode": "EXACT",
  "normalizeLineEndings": true,
  "trimTrailingWhitespaces": true
}
```

Postman Tests:

```js
const json = pm.response.json();
pm.environment.set("task_id", json.id);
```

## 10. Add Task Content Blocks

`PUT {{course_base}}/api/v1/admin/course-items/{{task_id}}/content-blocks`

Headers:

```http
Content-Type: application/json
Authorization: Bearer {{teacher_token}}
```

Body:

```json
{
  "contentBlocks": [
    {
      "blockType": "TEXT",
      "orderIndex": 0,
      "title": "Statement",
      "textContent": "Read two integers from stdin and print their sum.",
      "url": null,
      "language": null,
      "metadataJson": null
    }
  ]
}
```

## 11. Add Test Cases

`PUT {{course_base}}/api/v1/admin/course-items/{{task_id}}/test-cases`

Headers:

```http
Content-Type: application/json
Authorization: Bearer {{teacher_token}}
```

Body:

```json
{
  "testCases": [
    {
      "testKey": "open-1",
      "orderIndex": 0,
      "visibility": "OPEN",
      "inputData": "2 3\n",
      "expectedOutput": "5\n"
    },
    {
      "testKey": "hidden-1",
      "orderIndex": 1,
      "visibility": "HIDDEN",
      "inputData": "10 -4\n",
      "expectedOutput": "6\n"
    }
  ]
}
```

## 12. Publish Course

`POST {{course_base}}/api/v1/admin/courses/{{course_id}}/publish`

Headers:

```http
Authorization: Bearer {{teacher_token}}
```

## 13. Check Public Course Endpoints

`GET {{course_base}}/api/v1/courses`

`GET {{course_base}}/api/v1/courses/{{course_id}}`

`GET {{course_base}}/api/v1/course-items/{{task_id}}`

Expected: public responses do not expose hidden tests or expected outputs.

## 14. Check Course Internal Execution Package

`GET {{course_base}}/api/v1/internal/course-items/{{task_id}}/execution-package`

Headers:

```http
X-Internal-Api-Key: {{internal_api_key}}
Authorization: Bearer {{student_token}}
```

Expected:

- `itemType: CODING`
- `courseId == {{course_id}}`
- `moduleId == {{module_id}}`
- `tests.length == 2`

## 15. Create Course Enrollment In LearningService

`POST {{learning_base}}/api/v1/learning/course-enrollments`

Headers:

```http
Content-Type: application/json
```

Body:

```json
{
  "userId": {{student_id}},
  "courseId": {{course_id}},
  "status": "IN_PROGRESS",
  "progressPercent": 0,
  "completedTasksCount": 0,
  "totalTasksCount": 1,
  "totalScore": 0
}
```

## 16. Create Module Progress

`POST {{learning_base}}/api/v1/learning/module-progress`

Headers:

```http
Content-Type: application/json
```

Body:

```json
{
  "userId": {{student_id}},
  "courseId": {{course_id}},
  "moduleId": {{module_id}},
  "status": "IN_PROGRESS",
  "progressPercent": 0,
  "completedTasksCount": 0,
  "totalTasksCount": 1,
  "score": 0
}
```

## 17. Create Task Progress

`POST {{learning_base}}/api/v1/learning/task-progress`

Headers:

```http
Content-Type: application/json
```

Body:

```json
{
  "userId": {{student_id}},
  "courseId": {{course_id}},
  "moduleId": {{module_id}},
  "taskId": {{task_id}},
  "status": "NOT_STARTED",
  "attemptsCount": 0,
  "bestScore": 0,
  "lastScore": 0,
  "isCompleted": false
}
```

LearningService requires a `task_progress` row for `userId + taskId` before submit. During submit, `courseId/moduleId` from CourseService are treated as authoritative and synced into this row if needed.

## 18. Submit Wrong Solution And Check WA

`POST {{learning_base}}/api/v1/learning/tasks/{{task_id}}/submissions`

Headers:

```http
Content-Type: application/json
Authorization: Bearer {{student_token}}
```

Body:

```json
{
  "taskId": {{task_id}},
  "language": "python",
  "sourceCode": "import sys\nprint(sys.stdin.read(), end='')",
  "executionMode": "BATCH"
}
```

Expected response:

```json
{
  "status": "FINISHED",
  "verdict": "WA",
  "score": 0,
  "passedTestsCount": 0,
  "totalTestsCount": 2
}
```

## 19. Submit Correct Solution And Check OK

`POST {{learning_base}}/api/v1/learning/tasks/{{task_id}}/submissions`

Headers:

```http
Content-Type: application/json
Authorization: Bearer {{student_token}}
```

Body:

```json
{
  "taskId": {{task_id}},
  "language": "python",
  "sourceCode": "import sys\nnums = list(map(int, sys.stdin.read().split()))\nprint(sum(nums))",
  "executionMode": "BATCH"
}
```

Expected response:

```json
{
  "status": "FINISHED",
  "verdict": "OK",
  "score": 100,
  "passedTestsCount": 2,
  "totalTestsCount": 2
}
```

## 20. Check Learning Progress After OK

`GET {{learning_base}}/api/v1/learning/task-progress/{{student_id}}/{{course_id}}/{{module_id}}/{{task_id}}`

Expected:

- `status: COMPLETED`
- `isCompleted: true`
- `bestScore: 100`

`GET {{learning_base}}/api/v1/learning/module-progress/{{student_id}}/{{course_id}}/{{module_id}}`

Expected:

- `status: COMPLETED`
- `progressPercent: 100.00`
- `score: 100`

`GET {{learning_base}}/api/v1/learning/course-enrollments/{{student_id}}/{{course_id}}`

Expected:

- `status: COMPLETED`
- `progressPercent: 100.00`
- `totalScore: 100`

## 21. Optional: Check CodeExecutorService Directly

### Batch

`POST {{executor_base}}/executions/batch`

Headers:

```http
Content-Type: application/json
```

Body:

```json
{
  "language": "python",
  "code": "import sys\nnums = list(map(int, sys.stdin.read().split()))\nprint(sum(nums))",
  "tests": [
    {
      "id": "open-1",
      "input": "2 3\n"
    },
    {
      "id": "hidden-1",
      "input": "10 -4\n"
    }
  ],
  "limits": {
    "timeLimitMs": 2000,
    "memoryLimitMb": 128,
    "outputLimitKb": 64
  },
  "executionPolicy": {
    "networkDisabled": true,
    "readOnlyFs": true
  },
  "metadata": {
    "taskId": "{{task_id}}"
  }
}
```

Expected: each test has `outcome: OK`; stdout is `5\n` and `6\n`.

### Step

`POST {{executor_base}}/executions`

Body:

```json
{
  "language": "python",
  "code": "import sys\nnums = list(map(int, sys.stdin.read().split()))\nprint(sum(nums))",
  "limits": {
    "timeLimitMs": 2000,
    "memoryLimitMb": 128,
    "outputLimitKb": 64
  },
  "executionPolicy": {
    "networkDisabled": true,
    "readOnlyFs": true
  },
  "metadata": {
    "taskId": "{{task_id}}"
  }
}
```

Save `id` as `session_id`.

`POST {{executor_base}}/executions/{{session_id}}/tests`

```json
{
  "id": "open-1",
  "input": "2 3\n"
}
```

`GET {{executor_base}}/executions/{{session_id}}`

`POST {{executor_base}}/executions/{{session_id}}/cancel`

## Troubleshooting

- `401` from LearningService with empty body: missing/expired/invalid `Authorization` token, wrong `iss`, `aud`, or JWKS URL.
- `CourseService returned 401`: `CODERUNNER_COURSE_SERVICE_INTERNAL_API_KEY` in LearningService does not match `COURSE_SERVICE_INTERNAL_API_KEY` in CourseService.
- `Task progress not found for user and task`: create `task_progress` for `student_id + task_id`.
- `Course item is not CODING`: the CourseService item was created as `THEORY`, `QUIZ`, etc.; use a `CODING` item.
- `Execution package has no tests`: add test cases in CourseService before submit.
- `WA`: executor ran successfully, but actual stdout does not match expected output.
