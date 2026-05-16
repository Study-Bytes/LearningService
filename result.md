# LearningService: запуск и проверка API

## 1) Как поднять микросервис

### 1.1. Требования
- Java 21
- Maven 3.9+
- PostgreSQL 14+ (или 13+, но лучше 14+)

Проверка:

```powershell
java -version
mvn -version
psql --version
```

### 1.2. Поднять PostgreSQL

Есть 2 варианта: локально установленный PostgreSQL или Docker. Ниже самый быстрый вариант через Docker.

```powershell
docker run --name learning-postgres `
  -e POSTGRES_USER=postgres `
  -e POSTGRES_PASSWORD=password `
  -e POSTGRES_DB=postgres `
  -p 5432:5432 `
  -d postgres:16
```

Если PostgreSQL уже установлен локально, просто убедись, что сервис запущен и порт `5432` свободен.

### 1.3. Создать БД для сервиса

Подключись к PostgreSQL и создай БД `learning_service`:

```powershell
psql -h localhost -U postgres -d postgres
```

Внутри `psql`:

```sql
CREATE DATABASE learning_service;
\q
```

Пароль берётся из `application.yml`:
- `spring.datasource.username: postgres`
- `spring.datasource.password: password`

Если у тебя другой пароль/пользователь, поменяй их в файле:

`src/main/resources/application.yml`

### 1.4. Проверить, что БД доступна

```powershell
psql -h localhost -U postgres -d learning_service -c "SELECT 1;"
```

Если вернулось `1`, БД работает.

### 1.5. Запустить микросервис

Из корня проекта:

```powershell
mvn spring-boot:run
```

Приложение стартует на:

`http://localhost:8083`

При старте Hibernate автоматически создаст/обновит 3 нужные таблицы:
- `course_enrollments`
- `module_progress`
- `task_progress`

Проверка таблиц:

```powershell
psql -h localhost -U postgres -d learning_service -c "\dt"
```

## 2) Запросы для проверки в Postman

Base URL:

`http://localhost:8083/api/v1/learning`

Заголовок для всех `POST/PUT`:
- `Content-Type: application/json`

Формат дат: `yyyy-MM-ddTHH:mm:ss` (пример: `2026-05-13T12:00:00`).

---

## A. `course_enrollments`

### POST create
`POST /course-enrollments/`

```json
{
  "userId": {{student_id}},
  "courseId": {{course_id}},
  "status": "IN_PROGRESS",
  "progressPercent": 10.5,
  "completedTasksCount": 1,
  "totalTasksCount": 10,
  "totalScore": 15,
  "startedAt": "2026-05-13T10:00:00",
  "lastActivityAt": "2026-05-13T10:10:00"
}
```

### PUT update
`PUT /course-enrollments/{{student_id}}/{{course_id}}`

```json
{
  "status": "COMPLETED",
  "progressPercent": 100.0,
  "completedTasksCount": 10,
  "totalTasksCount": 10,
  "totalScore": 130,
  "completedAt": "2026-05-13T12:00:00",
  "lastActivityAt": "2026-05-13T12:00:00"
}
```

### GET by `user_id + course_id`
`GET /course-enrollments/{{student_id}}/{{course_id}}`

---

## B. `module_progress`

### POST create
`POST /module-progress/`

```json
{
  "userId": {{student_id}},
  "courseId": {{course_id}},
  "moduleId": {{module_id}},
  "status": "IN_PROGRESS",
  "progressPercent": 25.0,
  "completedTasksCount": 1,
  "totalTasksCount": 4,
  "score": 20,
  "startedAt": "2026-05-13T10:20:00",
  "lastActivityAt": "2026-05-13T10:30:00"
}
```

### PUT update
`PUT /module-progress/{{student_id}}/{{course_id}}/{{module_id}}`

```json
{
  "status": "COMPLETED",
  "progressPercent": 100.0,
  "completedTasksCount": 4,
  "totalTasksCount": 4,
  "score": 80,
  "completedAt": "2026-05-13T12:10:00",
  "lastActivityAt": "2026-05-13T12:10:00"
}
```

### GET by `user_id + course_id + module_id`
`GET /module-progress/{{student_id}}/{{course_id}}/{{module_id}}`

---

## C. `task_progress`

### POST create
`POST /task-progress/`

```json
{
  "userId": {{student_id}},
  "courseId": {{course_id}},
  "moduleId": {{module_id}},
  "taskId": {{task_id}},
  "status": "IN_PROGRESS",
  "attemptsCount": 1,
  "bestScore": 40,
  "lastScore": 40,
  "isCompleted": false,
  "firstOpenedAt": "2026-05-13T10:40:00",
  "startedAt": "2026-05-13T10:41:00",
  "lastSubmissionAt": "2026-05-13T10:50:00",
  "lastActivityAt": "2026-05-13T10:50:00"
}
```

### PUT update
`PUT /task-progress/{{student_id}}/{{course_id}}/{{module_id}}/{{task_id}}`

```json
{
  "status": "COMPLETED",
  "attemptsCount": 2,
  "bestScore": 100,
  "lastScore": 100,
  "isCompleted": true,
  "firstSuccessAt": "2026-05-13T11:00:00",
  "completedAt": "2026-05-13T11:00:00",
  "lastSubmissionAt": "2026-05-13T11:00:00",
  "lastActivityAt": "2026-05-13T11:00:00"
}
```

### GET by `user_id + course_id + module_id + task_id`
`GET /task-progress/{{student_id}}/{{course_id}}/{{module_id}}/{{task_id}}`

---

## Быстрая самопроверка

1. Сделай `POST` для курса, модуля и таски.
2. Сделай `PUT` для каждой записи.
3. Сделай `GET` и проверь, что поля обновились.
4. Повтори `POST` с тем же ключом:
   - курс: тот же `userId + courseId`
   - модуль: тот же `userId + moduleId`
   - таска: тот же `userId + taskId`
   Ожидаемо: `409 Conflict`.
