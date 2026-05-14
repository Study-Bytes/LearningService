# CodeRunner Commands

## 1) Что теперь запускается

`docker-compose.yml` поднимает 3 контейнера:

1. `learning-postgres` - PostgreSQL
2. `code-executor-service` - реальный CodeExecutorService
3. `learning-service` - текущий микросервис

По умолчанию наружу проброшены порты:

- `8090` -> `learning-service`
- `8095` -> `code-executor-service`
- `6767` -> `learning-postgres`

Все переменные вынесены в `.env`.

## 2) Важно про CourseService

`LearningService` ходит в реальный `CourseService` по адресу из `.env`:

- `CODERUNNER_COURSE_SERVICE_BASE_URL=http://host.docker.internal:8082`

Для сценария проверки используй реальные `courseId/moduleId/taskId`, которые существуют в CourseService.

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
- `docker compose logs learning-service --tail 50`
- `docker compose logs code-executor-service --tail 50`

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

## 7) Настройка адресов интеграций

В `.env` поменяй:

- `CODERUNNER_COURSE_SERVICE_BASE_URL`
- `CODERUNNER_COURSE_SERVICE_INTERNAL_API_KEY_HEADER`
- `CODERUNNER_COURSE_SERVICE_INTERNAL_API_KEY`
- `CODERUNNER_EXECUTOR_SERVICE_BASE_URL`
- `CODERUNNER_EXECUTOR_SERVICE_AUTH_TOKEN`

Примеры:

- executor в той же docker-сети: `http://code-executor-service:8095`
- executor на хосте: `http://host.docker.internal:8095`

После изменения перезапусти:

```powershell
docker compose up --build -d
```

## 8) Остановка

```powershell
docker compose down
```

С удалением тома БД:

```powershell
docker compose down -v
```
