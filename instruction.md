# Как поднять `LearningService` + `CodeExecutorService` в Docker и проверить через Postman

## 1) Что уже настроено в проекте

- `LearningService` в `docker-compose.yml` ходит в реальный `CodeExecutorService` по адресу:
  - `http://code-executor-service:8084`
- `CodeExecutorService` поднимается отдельно из своего репозитория (`../CodeExecutorService`) своим `docker-compose.yml`.
- `LearningService` ходит в реальный `CourseService` по адресу:
  - `http://course-service:8082`
- При submit `LearningService` прокидывает frontend `Authorization: Bearer <access_token>` в CourseService internal request.
- Также отправляется internal API key из `CODERUNNER_COURSE_SERVICE_INTERNAL_API_KEY`; он должен совпадать с `COURSE_SERVICE_INTERNAL_API_KEY` в CourseService.
- Для `CodeExecutorService` подключены:
  - `/var/run/docker.sock:/var/run/docker.sock` (чтобы он мог запускать контейнеры для прогона кода),
  - `/tmp:/tmp` (чтобы работали bind mount'ы временных файлов кода).

## 2) Запуск контейнеров

Сначала подними `CodeExecutorService`:

```powershell
cd ../CodeExecutorService
docker compose up --build -d
docker compose ps
```

Потом подними `LearningService`:

```powershell
cd ../LearningService
docker compose build
docker compose up -d
docker compose ps
```

Проверка логов:

```powershell
cd ../CodeExecutorService
docker compose logs -f code-executor-service
cd ../LearningService
docker compose logs -f learning-service
```

Порты с хоста:

- `LearningService`: `http://localhost:8083`
- `CodeExecutorService`: `http://localhost:8084`

## 3) Postman: подготовить данные прогресса (обязательно)

`LearningService` перед отправкой в executor ищет `task_progress`, `module_progress`, `course_enrollment`.
Поэтому сначала создай их. Для полной проверки с пустыми БД, регистрацией пользователей, созданием курса, coding task и тестов используй `POSTMAN_FULL_FLOW.md`.

### 3.1 Create course enrollment

- Method: `POST`
- URL: `http://localhost:8083/api/v1/learning/course-enrollments`
- Body (raw JSON):

```json
{
  "userId": {{student_id}},
  "courseId": {{course_id}}
}
```

### 3.2 Create module progress

- Method: `POST`
- URL: `http://localhost:8083/api/v1/learning/module-progress`
- Body:

```json
{
  "userId": {{student_id}},
  "courseId": {{course_id}},
  "moduleId": {{module_id}}
}
```

### 3.3 Create task progress

- Method: `POST`
- URL: `http://localhost:8083/api/v1/learning/task-progress`
- Body:

```json
{
  "userId": {{student_id}},
  "courseId": {{course_id}},
  "moduleId": {{module_id}},
  "taskId": {{task_id}}
}
```

## 4) Postman: прогон теста через LearningService (основной сценарий)

### Submit solution

- Method: `POST`
- URL: `http://localhost:8083/api/v1/learning/tasks/{{task_id}}/submissions`
- Headers:
  - `Content-Type: application/json`
  - `Authorization: Bearer <access_token>`
- Body:

```json
{
  "taskId": {{task_id}},
  "language": "python",
  "sourceCode": "import sys\nnums = list(map(int, sys.stdin.read().split()))\nprint(sum(nums))",
  "executionMode": "BATCH"
}
```

Ожидаемое поведение:

1. `LearningService` берет тесты у реального `CourseService` (на `8082`).
2. Отправляет batch-запрос в реальный `CodeExecutorService`.
3. Получает фактический stdout/stderr по тестам.
4. Сравнивает с expected output и возвращает финальный вердикт.

Важно: `taskId` должен соответствовать реальному item в `CourseService`. `courseId` и `moduleId` для сабмита берутся из CourseService execution package и синхронизируются в `task_progress`, если локальная запись была создана со старыми id.

Пример успешного ответа (`201 Created`, поля могут отличаться по id):

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

Для проверки негативного сценария `WA` можно отправить код, который просто возвращает входные данные:

```json
{
  "taskId": {{task_id}},
  "language": "python",
  "sourceCode": "import sys\nprint(sys.stdin.read(), end='')",
  "executionMode": "BATCH"
}
```

## 5) Остановка

Из папки `D:\Hw\StudyBytes\LearningService`:

```powershell
docker compose down
```

Если нужно удалить volume postgres:

```powershell
docker compose down -v
```

Отдельно остановка `CodeExecutorService`:

```powershell
cd ../CodeExecutorService
docker compose down
```
