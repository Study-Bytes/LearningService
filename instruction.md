# Как поднять `LearningService` + `CodeExecutorService` в Docker и проверить через Postman

## 1) Что уже настроено в проекте

- `LearningService` в `docker-compose.yml` ходит в реальный `CodeExecutorService` по адресу:
  - `http://code-executor-service:8095`
- `CodeExecutorService` добавлен как отдельный сервис в том же compose.
- `LearningService` ходит в реальный `CourseService` по адресу:
  - `http://host.docker.internal:8082`
- Для `CodeExecutorService` подключены:
  - `/var/run/docker.sock:/var/run/docker.sock` (чтобы он мог запускать контейнеры для прогона кода),
  - `/tmp:/tmp` (чтобы работали bind mount'ы временных файлов кода).

## 2) Запуск контейнеров

Открой терминал в папке:

```powershell
D:\Hw\StudyBytes\LearningService
```

Выполни:

```powershell
docker compose build
docker compose up -d
docker compose ps
```

Проверка логов:

```powershell
docker compose logs -f code-executor-service
docker compose logs -f learning-service
```

Порты с хоста:

- `LearningService`: `http://localhost:8090`
- `CodeExecutorService`: `http://localhost:8095`

### Опционально: поднять только `CodeExecutorService` отдельно (из его папки)

Открой терминал в:

```powershell
D:\Hw\StudyBytes\CodeExecutorService
```

И выполни:

```powershell
docker build -t studybytes/code-executor-service:local .
docker run -d --name code-executor-service-local -p 8095:8095 -e SERVER_PORT=8095 -v /var/run/docker.sock:/var/run/docker.sock -v /tmp:/tmp studybytes/code-executor-service:local
```

## 3) Postman: подготовить данные прогресса (обязательно)

`LearningService` перед отправкой в executor ищет `task_progress`, `module_progress`, `course_enrollment`.
Поэтому сначала создай их.

### 3.1 Create course enrollment

- Method: `POST`
- URL: `http://localhost:8090/api/v1/learning/course-enrollments`
- Body (raw JSON):

```json
{
  "userId": 101,
  "courseId": 1001
}
```

### 3.2 Create module progress

- Method: `POST`
- URL: `http://localhost:8090/api/v1/learning/module-progress`
- Body:

```json
{
  "userId": 101,
  "courseId": 1001,
  "moduleId": 2001
}
```

### 3.3 Create task progress

- Method: `POST`
- URL: `http://localhost:8090/api/v1/learning/task-progress`
- Body:

```json
{
  "userId": 101,
  "courseId": 1001,
  "moduleId": 2001,
  "taskId": 2
}
```

## 4) Postman: прогон теста через LearningService (основной сценарий)

### Submit solution

- Method: `POST`
- URL: `http://localhost:8090/api/v1/learning/tasks/2/submissions`
- Headers:
  - `Content-Type: application/json`
  - `Authorization: Bearer <access_token>`
- Body:

```json
{
  "taskId": 2,
  "language": "python",
  "sourceCode": "import sys\nprint(sys.stdin.read(), end='')",
  "executionMode": "BATCH"
}
```

Ожидаемое поведение:

1. `LearningService` берет тесты у реального `CourseService` (на `8082`).
2. Отправляет batch-запрос в реальный `CodeExecutorService`.
3. Получает фактический stdout/stderr по тестам.
4. Сравнивает с expected output и возвращает финальный вердикт.

Важно: `taskId`, `courseId`, `moduleId` в примере должны соответствовать данным, которые реально есть в вашем `CourseService`.

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

## 5) Остановка

Из папки `D:\Hw\StudyBytes\LearningService`:

```powershell
docker compose down
```

Если нужно удалить volume postgres:

```powershell
docker compose down -v
```
