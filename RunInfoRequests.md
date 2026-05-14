# RunInfoRequests: сценарий прогона кода через LearningService

Ниже полный ход запросов в сценарии:

- фронт отправляет решение в `LearningService`,
- `LearningService` получает execution package из `CourseService`,
- `LearningService` отправляет в `CodeExecutorService` либо `batch`, либо `one by one`,
- `LearningService` сравнивает фактический вывод с `expectedOutput`,
- фронт получает финальный вердикт.

## 0) Предусловия (данные прогресса)

Перед submit у пользователя должна быть запись `task_progress` для `userId + taskId`.
Обычно это создается заранее:

1. `POST /api/v1/learning/course-enrollments`
2. `POST /api/v1/learning/module-progress`
3. `POST /api/v1/learning/task-progress`

Если `task_progress` нет, submit вернет ошибку `404 Task progress not found for user and task`.

---

## 1) Фронт -> LearningService: отправка решения

### HTTP

- Method: `POST`
- URL: `http://localhost:8090/api/v1/learning/tasks/{taskId}/submissions`
- Headers:
  - `Content-Type: application/json`
  - `user_id: <long>`

### Body (пример)

```json
{
  "taskId": 2,
  "language": "python",
  "sourceCode": "import sys\nprint(sys.stdin.read(), end='')",
  "executionMode": "BATCH"
}
```

### Поля запроса

- `taskId` (`Long`) — id задачи.
- `language` (`String`) — язык (например `python`).
- `sourceCode` (`String`) — код пользователя.
- `executionMode` (`Enum`) — режим прогона:
  - `BATCH` (по умолчанию, если поле не передано),
  - `ONE_BY_ONE`.
- `user_id` header (`Long`) — id пользователя.

---

## 2) LearningService -> CourseService: запрос execution package

`LearningService` запрашивает полный пакет исполнения по `taskId`.

### HTTP (внутренний)

- Method: `GET`
- URL: `{CODERUNNER_COURSE_SERVICE_BASE_URL}/api/v1/internal/course-items/{itemId}/execution-package`
- Headers:
  - `{CODERUNNER_COURSE_SERVICE_INTERNAL_API_KEY_HEADER}: {CODERUNNER_COURSE_SERVICE_INTERNAL_API_KEY}`

### Response Body (пример)

```json
{
  "itemId": 2,
  "moduleId": 2001,
  "courseId": 1001,
  "itemType": "CODING",
  "title": "Task 2",
  "language": "python",
  "starterCode": "",
  "limits": {
    "timeLimitMs": 1500,
    "memoryLimitMb": 256,
    "outputLimitKb": 256
  },
  "executionPolicy": {
    "networkDisabled": true,
    "readOnlyFs": true
  },
  "evaluationPolicy": {
    "comparisonMode": "EXACT",
    "normalizeLineEndings": true,
    "trimTrailingWhitespaces": true
  },
  "tests": [
    {
      "testKey": "open-1",
      "visibility": "OPEN",
      "inputData": "hello\n",
      "expectedOutput": "hello\n",
      "orderIndex": 1
    },
    {
      "testKey": "hidden-1",
      "visibility": "HIDDEN",
      "inputData": "world\n",
      "expectedOutput": "world\n",
      "orderIndex": 2
    }
  ]
}
```

### Какие поля используются из ответа CourseService

- `limits`:
  - `timeLimitMs`
  - `memoryLimitMb`
  - `outputLimitKb`
- `executionPolicy`:
  - `networkDisabled`
  - `readOnlyFs`
- `evaluationPolicy`:
  - `comparisonMode`
  - `normalizeLineEndings`
  - `trimTrailingWhitespaces`
- `tests[]`:
  - `testKey`
  - `inputData`
  - `expectedOutput`
  - `orderIndex`

Также валидируются:

- `itemId == taskId`
- `itemType == CODING`
- при наличии в ответе:
  - `courseId` должен совпадать с `task_progress.courseId`
  - `moduleId` должен совпадать с `task_progress.moduleId`

---

## 3) LearningService -> CodeExecutorService: запуск (по executionMode)

На основе execution package и `sourceCode` формируется вызов в executor.

### 3.1) Режим `BATCH`

### HTTP (внутренний)

- Method: `POST`
- URL: `{CODERUNNER_EXECUTOR_SERVICE_BASE_URL}/executions/batch`
- Headers:
  - `Authorization: Bearer {CODERUNNER_EXECUTOR_SERVICE_AUTH_TOKEN}`
  - `Content-Type: application/json`

### Body (структура)

```json
{
  "language": "python",
  "code": "string",
  "tests": [
    {
      "id": "testKey",
      "input": "inputData",
      "timeoutMs": null
    }
  ],
  "limits": {
    "timeLimitMs": 1500,
    "memoryLimitMb": 256,
    "outputLimitKb": 256
  },
  "executionPolicy": {
    "networkDisabled": true,
    "readOnlyFs": true
  },
  "metadata": {
    "taskId": "2"
  }
}
```

### Как мапятся поля

- `code` <- `sourceCode` от фронта
- `tests[].id` <- `CourseService.tests[].testKey`
- `tests[].input` <- `CourseService.tests[].inputData`
- `limits` <- `CourseService.limits`
- `executionPolicy` <- `CourseService.executionPolicy`

### 3.2) Режим `ONE_BY_ONE`

#### Шаг A: создать сессию

- Method: `POST`
- URL: `{CODERUNNER_EXECUTOR_SERVICE_BASE_URL}/executions`
- Headers:
  - `Authorization: Bearer {CODERUNNER_EXECUTOR_SERVICE_AUTH_TOKEN}`
  - `Content-Type: application/json`

Body:

```json
{
  "language": "python",
  "code": "string",
  "limits": {
    "timeLimitMs": 1500,
    "memoryLimitMb": 256,
    "outputLimitKb": 256
  },
  "executionPolicy": {
    "networkDisabled": true,
    "readOnlyFs": true
  },
  "metadata": {
    "taskId": "2"
  }
}
```

#### Шаг B: прогнать каждый тест отдельно

Для каждого теста из `CourseService.tests[]`:

- Method: `POST`
- URL: `{CODERUNNER_EXECUTOR_SERVICE_BASE_URL}/executions/{sessionId}/tests`

Body:

```json
{
  "id": "testKey",
  "input": "inputData",
  "timeoutMs": null
}
```

#### Шаг C: закрыть сессию

- Method: `POST`
- URL: `{CODERUNNER_EXECUTOR_SERVICE_BASE_URL}/executions/{sessionId}/cancel`

---

## 4) CodeExecutorService -> LearningService: результаты выполнения

### Response (структура)

```json
{
  "id": "uuid",
  "status": "FINISHED",
  "language": "python",
  "durationMs": 12,
  "peakMemoryMb": 24,
  "tests": [
    {
      "testId": "open-1",
      "outcome": "OK",
      "exitCode": 0,
      "stdout": { "data": "hello\n", "truncated": false },
      "stderr": { "data": "", "truncated": false },
      "durationMs": 4,
      "memoryMb": 16
    }
  ],
  "metadata": {
    "taskId": "2"
  }
}
```

`id` из ответа сохраняется в `executorRequestId`.

---

## 5) Внутри LearningService: сравнение с правильными ответами

Для каждого теста из `CourseService.tests[]`:

1. Ищется результат executor по ключу:
   - `test.testKey == execution.tests[].testId`
2. Если `outcome == OK`:
   - сравнивается `expectedOutput` (из CourseService) и `stdout.data` (из CodeExecutor),
   - перед сравнением применяются правила из `evaluationPolicy`:
     - `normalizeLineEndings`
     - `trimTrailingWhitespaces`
3. Если `outcome != OK`:
   - тест помечается как `ERROR` с учетом типа ошибки (`RUNTIME_ERROR`, `TIMEOUT`, `MEMORY_LIMIT`, `INTERNAL_ERROR`).

### Статусы по тестам

- `PASSED` — `outcome=OK` и вывод совпал.
- `FAILED` — `outcome=OK`, но вывод не совпал.
- `ERROR` — ошибка выполнения.
- `SKIPPED` — нет результата по тесту.

### Финальные вычисления

- `passedTestsCount`
- `totalTestsCount`
- `score = round(passedTestsCount * 100 / totalTestsCount)`
- `verdict`:
  - `OK` / `WA` / `RE` / `TL` / `ML` / `PE`

---

## 6) LearningService -> Фронт: финальный ответ submit

### Response (пример)

```json
{
  "submissionId": 2,
  "status": "FINISHED",
  "verdict": "OK",
  "score": 100,
  "passedTestsCount": 2,
  "totalTestsCount": 2,
  "executorRequestId": "682b488e-36a9-4c43-8ef5-ab5f22dbad02"
}
```

---

## Краткая схема потока

1. Front -> LearningService: `POST /api/v1/learning/tasks/{taskId}/submissions`
2. LearningService -> CourseService: `GET /api/v1/internal/course-items/{itemId}/execution-package`
3. LearningService -> CodeExecutorService:
   - `BATCH`: `POST /executions/batch`
   - `ONE_BY_ONE`: `POST /executions` -> `POST /executions/{id}/tests` -> `POST /executions/{id}/cancel`
4. CodeExecutorService -> LearningService: `ExecutionResponse (tests outcomes/stdout/stderr)`
5. LearningService: сравнение с `expectedOutput` + расчет score/verdict
6. LearningService -> Front: `SubmissionCreateResponse`
