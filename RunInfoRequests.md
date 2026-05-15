# RunInfoRequests: сценарий прогона кода через LearningService (JWT)

Ниже полный поток запросов в сценарии, где фронт отправляет JWT access token.

Ключевое изменение:

- фронт больше не передает `user_id` отдельным header;
- `LearningService` берет `userId` из JWT claim `sub`;
- JWT валидируется по JWKS UserService.

## 0) Предусловия (данные прогресса)

Перед submit у пользователя должна быть запись `task_progress` для `userId + taskId`.

Обычно это создается заранее:

1. `POST /api/v1/learning/course-enrollments`
2. `POST /api/v1/learning/module-progress`
3. `POST /api/v1/learning/task-progress`

Если `task_progress` нет, submit вернет `404 Task progress not found for user and task`.

---

## 1) Фронт -> LearningService: отправка решения

### HTTP

- Method: `POST`
- URL: `http://localhost:8090/api/v1/learning/tasks/{taskId}/submissions`
- Headers:
  - `Content-Type: application/json`
  - `Authorization: Bearer <access_token>`

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

- `taskId` (`Long`) - id задачи.
- `language` (`String`) - язык (например `python`).
- `sourceCode` (`String`) - код пользователя.
- `executionMode` (`Enum`) - режим прогона:
  - `BATCH` (по умолчанию, если поле не передано),
  - `ONE_BY_ONE`.

`access_token` должен содержать минимум:

- `iss` (issuer),
- `aud` (audience),
- `exp` (срок жизни),
- `sub` (id пользователя, строкой; например `"123"`).

---

## 2) Внутри LearningService: валидация JWT и извлечение userId

`LearningService` работает как OAuth2 Resource Server и делает следующее:

1. Проверяет подпись JWT (RS256) по JWKS UserService.
2. Проверяет claims:
   - `iss`
   - `aud`
   - `exp`
3. Извлекает `sub`.
4. Парсит `sub` в `Long userId`.

Если токен невалиден или `sub` нечисловой, возвращается `401 Unauthorized`.

JWKS endpoint UserService:

- `GET /api/v1/auth/.well-known/jwks.json`

---

## 3) LearningService -> CourseService: запрос execution package

`LearningService` запрашивает execution package по `taskId`.

### HTTP (внутренний)

- Method: `GET`
- URL: `{CODERUNNER_COURSE_SERVICE_BASE_URL}/api/v1/internal/course-items/{itemId}/execution-package`
- Headers:
  - `{CODERUNNER_COURSE_SERVICE_INTERNAL_API_KEY_HEADER}: {CODERUNNER_COURSE_SERVICE_INTERNAL_API_KEY}`

### Что используется из ответа

- `limits`
- `executionPolicy`
- `evaluationPolicy`
- `tests[]` (`testKey`, `inputData`, `expectedOutput`, `orderIndex`)

Также валидируется:

- `itemId == taskId`
- `itemType == CODING`
- `courseId` и `moduleId` (если переданы CourseService) совпадают с `task_progress`.

---

## 4) LearningService -> CodeExecutorService: запуск (по executionMode)

### 4.1 Режим `BATCH`

- Method: `POST`
- URL: `{CODERUNNER_EXECUTOR_SERVICE_BASE_URL}/executions/batch`
- Headers:
  - `Authorization: Bearer {CODERUNNER_EXECUTOR_SERVICE_AUTH_TOKEN}`
  - `Content-Type: application/json`

`LearningService` маппит:

- `code` <- `sourceCode`
- `tests[].id` <- `testKey`
- `tests[].input` <- `inputData`
- `limits` <- `CourseService.limits`
- `executionPolicy` <- `CourseService.executionPolicy`

### 4.2 Режим `ONE_BY_ONE`

1. `POST /executions` (создание сессии)
2. `POST /executions/{sessionId}/tests` для каждого теста
3. `POST /executions/{sessionId}/cancel` (закрытие сессии)

---

## 5) CodeExecutorService -> LearningService: результаты выполнения

`LearningService` получает execution response с результатами тестов:

- `id` (сохраняется как `executorRequestId`)
- `status`
- `tests[]` (`testId`, `outcome`, `stdout`, `stderr`, `durationMs`, `memoryMb`)

---

## 6) Внутри LearningService: сравнение и расчеты

Для каждого теста:

1. Находит результат executor по `testId`.
2. Если `outcome == OK`:
   - сравнивает `stdout.data` с `expectedOutput`;
   - применяет `evaluationPolicy` (`normalizeLineEndings`, `trimTrailingWhitespaces`).
3. Если `outcome != OK`, помечает как `ERROR` с соответствующим типом.

Финально считаются:

- `passedTestsCount`
- `totalTestsCount`
- `score`
- `verdict` (`OK/WA/RE/TL/ML/PE`)

Далее обновляются:

- `task_submissions`
- `submission_test_results`
- `task_progress`
- агрегаты `module_progress` и `course_enrollments`.

`userId`, полученный из JWT `sub`, используется во всех этих обновлениях и сохраняется в полях `user_id` соответствующих таблиц.

---

## 7) LearningService -> Фронт: финальный ответ submit

Пример:

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

1. Front -> LearningService: `POST /api/v1/learning/tasks/{taskId}/submissions` + `Authorization: Bearer <access_token>`
2. LearningService -> UserService JWKS: валидация подписи и claims (`iss/aud/exp`)
3. LearningService: `userId = Long.parseLong(jwt.sub)`
4. LearningService -> CourseService: `GET /api/v1/internal/course-items/{itemId}/execution-package`
5. LearningService -> CodeExecutorService:
   - `BATCH`: `POST /executions/batch`
   - `ONE_BY_ONE`: `POST /executions` -> `POST /executions/{id}/tests` -> `POST /executions/{id}/cancel`
6. LearningService: сравнение результата с expected output + расчет score/verdict
7. LearningService -> Front: `SubmissionCreateResponse`
