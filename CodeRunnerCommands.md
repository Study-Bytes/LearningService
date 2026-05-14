# CodeRunner Commands

## 1) Что теперь запускается

`docker-compose.yml` поднимает 4 контейнера:

1. `learning-postgres` - PostgreSQL
2. `mock-course-service` - mock CourseService (WireMock)
3. `mock-code-executor` - mock CodeExecutorService (WireMock)
4. `learning-service` - текущий микросервис

По умолчанию наружу проброшены порты:

- `8090` -> `learning-service`
- `6767` -> `learning-postgres`

Mock-сервисы наружу не пробрасываются (работают только во внутренней docker-сети).

Все переменные вынесены в `.env`.

## 2) Важно про mock-контракты

Текущий mock CourseService настроен на `taskId=2` (endpoint `/api/v1/internal/course-items/2/execution-package`).

Для сценария проверки кода используй:

- `courseId = 1001`
- `moduleId = 2001`
- `taskId = 2`

## 3) Запуск одной кнопкой из IDE

В IntelliJ IDEA:

1. Открой `docker-compose.yml`.
2. Нажми Run (иконка запуска рядом с `services`/файлом compose).

CLI-эквивалент:

```powershell
docker compose up --build -d
```

Проверка:

```powershell
docker compose ps
docker compose logs learning-service --tail 100
```

## 4) Проверка health

- `GET http://localhost:8090/actuator/health`
- `docker compose ps`
- `docker compose logs mock-course-service --tail 50`
- `docker compose logs mock-code-executor --tail 50`

## 5) Сценарий проверки кода (через Postman)

### 5.1 Подготовить progress-записи

1) `POST http://localhost:8090/api/v1/learning/course-enrollments`

```json
{
  "userId": 101,
  "courseId": 1001,
  "status": "IN_PROGRESS",
  "progressPercent": 0.0,
  "completedTasksCount": 0,
  "totalTasksCount": 1,
  "totalScore": 0
}
```

2) `POST http://localhost:8090/api/v1/learning/module-progress`

```json
{
  "userId": 101,
  "courseId": 1001,
  "moduleId": 2001,
  "status": "IN_PROGRESS",
  "progressPercent": 0.0,
  "completedTasksCount": 0,
  "totalTasksCount": 1,
  "score": 0
}
```

3) `POST http://localhost:8090/api/v1/learning/task-progress`

```json
{
  "userId": 101,
  "courseId": 1001,
  "moduleId": 2001,
  "taskId": 2,
  "status": "NOT_STARTED",
  "attemptsCount": 0,
  "bestScore": 0,
  "lastScore": 0,
  "isCompleted": false
}
```

### 5.2 Отправить решение на проверку

`POST http://localhost:8090/api/v1/learning/tasks/2/submissions`

Headers:

- `Content-Type: application/json`
- `user_id: 101`

Body:

```json
{
  "taskId": 2,
  "language": "python",
  "sourceCode": "print(input())"
}
```

Ожидаемо:

- HTTP `201`
- в ответе есть `submissionId`, `status`, `verdict`, `score`, `executorRequestId`

### 5.3 Проверить агрегаты прогресса

- `GET http://localhost:8090/api/v1/learning/task-progress/101/1001/2001/2`
- `GET http://localhost:8090/api/v1/learning/module-progress/101/1001/2001`
- `GET http://localhost:8090/api/v1/learning/course-enrollments/101/1001`

## 6) Проверка данных в БД

```powershell
docker compose exec learning-postgres psql -U postgres -d learning_service -c "select id,user_id,task_id,submission_number,status,verdict,score,passed_tests_count,total_tests_count,executor_request_id from task_submissions order by id desc limit 10;"
docker compose exec learning-postgres psql -U postgres -d learning_service -c "select submission_id,testkey,test_order,status,execution_time_ms,memory_kb from submission_test_results order by id desc limit 30;"
```

## 7) Как перейти с mock на реальный CodeExecutorService

В `.env` поменяй:

- `CODERUNNER_EXECUTOR_SERVICE_BASE_URL`
- `CODERUNNER_EXECUTOR_SERVICE_AUTH_TOKEN`

Примеры:

- executor в той же docker-сети: `http://code-executor:8080`
- executor на хосте: `http://host.docker.internal:8083`

После изменения перезапусти:

```powershell
docker compose up --build -d
```

`mock-course-service` можно оставить, если CourseService пока не готов.

## 8) Остановка

```powershell
docker compose down
```

С удалением тома БД:

```powershell
docker compose down -v
```
