// Use DBML to define your database structure
// Docs: https://dbml.dbdiagram.io/docs
//
// Learning Service хранит только прогресс и историю попыток.
// Сущности курса (course/module/task/test case) лежат в Course Service.
// Поэтому course_id / module_id / task_id / testKey здесь — это внешние id из Course Service.
// user_id — внешний id пользователя из UserService / AuthService.

Enum progress_status {
  NOT_STARTED
  IN_PROGRESS
  COMPLETED
  LOCKED
}

Enum submission_status {
  QUEUED
  RUNNING
  FINISHED
  FAILED
}

Enum submission_verdict {
  OK
  WA
  CE
  RE
  TL
  ML
  PE
}

Enum test_result_status {
  PASSED
  FAILED
  ERROR
  SKIPPED
}

Table course_enrollments [note: 'Общий прогресс пользователя по курсу. Одна запись = один пользователь на одном курсе'] {
  id bigint [primary key, increment, note: 'Внутренний id записи прохождения курса']

  user_id bigint [not null, note: 'Id пользователя из UserService/AuthService']
  course_id bigint [not null, note: 'Id курса из Course Service']

  status progress_status [not null, default: 'NOT_STARTED', note: 'Текущий статус прохождения курса']
  progress_percent decimal(5,2) [not null, default: 0, note: 'Общий прогресс по курсу в процентах: от 0 до 100']
  completed_tasks_count integer [not null, default: 0, note: 'Сколько задач курса уже завершено пользователем']
  total_tasks_count integer [not null, default: 0, note: 'Сколько задач всего есть в курсе на момент расчёта']
  total_score integer [not null, default: 0, note: 'Суммарный балл пользователя по курсу']

  started_at timestamp [note: 'Когда пользователь впервые реально начал проходить курс']
  completed_at timestamp [note: 'Когда курс был полностью завершён']
  last_activity_at timestamp [note: 'Когда в курсе была последняя активность пользователя']

  created_at timestamp [not null, default: `now()`, note: 'Когда запись прохождения курса была создана']
  updated_at timestamp [not null, default: `now()`, note: 'Когда запись прохождения курса последний раз обновлялась']

  indexes {
    (user_id, course_id) [unique, name: 'uq_course_enrollments_user_course']
    user_id [name: 'idx_course_enrollments_user_id']
    course_id [name: 'idx_course_enrollments_course_id']
    status [name: 'idx_course_enrollments_status']
  }
}

Table module_progress [note: 'Прогресс пользователя по конкретному модулю курса. Одна запись = один пользователь на одном модуле'] {
  id bigint [primary key, increment, note: 'Внутренний id записи прогресса по модулю']

  user_id bigint [not null, note: 'Id пользователя из UserService/AuthService']
  course_id bigint [not null, note: 'Id курса из Course Service']
  module_id bigint [not null, note: 'Id модуля из Course Service']

  status progress_status [not null, default: 'NOT_STARTED', note: 'Текущий статус прохождения модуля']
  progress_percent decimal(5,2) [not null, default: 0, note: 'Прогресс по модулю в процентах: от 0 до 100']
  completed_tasks_count integer [not null, default: 0, note: 'Сколько задач внутри модуля уже завершено']
  total_tasks_count integer [not null, default: 0, note: 'Сколько задач всего находится в модуле']
  score integer [not null, default: 0, note: 'Сумма баллов пользователя по задачам этого модуля']

  started_at timestamp [note: 'Когда пользователь впервые начал модуль']
  completed_at timestamp [note: 'Когда модуль был полностью завершён']
  last_activity_at timestamp [note: 'Когда в этом модуле была последняя активность']

  created_at timestamp [not null, default: `now()`, note: 'Когда запись прогресса по модулю была создана']
  updated_at timestamp [not null, default: `now()`, note: 'Когда запись прогресса по модулю последний раз обновлялась']

  indexes {
    (user_id, module_id) [unique, name: 'uq_module_progress_user_module']
    (user_id, course_id) [name: 'idx_module_progress_user_course']
    course_id [name: 'idx_module_progress_course_id']
    module_id [name: 'idx_module_progress_module_id']
    status [name: 'idx_module_progress_status']
  }
}

Table task_progress [note: 'Текущее состояние пользователя по конкретной задаче. Одна запись = один пользователь на одной задаче'] {
  id bigint [primary key, increment, note: 'Внутренний id записи прогресса по задаче']

  user_id bigint [not null, note: 'Id пользователя из UserService/AuthService']
  course_id bigint [not null, note: 'Id курса из Course Service']
  module_id bigint [not null, note: 'Id модуля из Course Service']
  task_id bigint [not null, note: 'Id задачи из Course Service']

  status progress_status [not null, default: 'NOT_STARTED', note: 'Текущий статус задачи: не начата, в процессе, завершена или заблокирована']
  attempts_count integer [not null, default: 0, note: 'Сколько всего попыток отправки было по задаче']
  best_score integer [not null, default: 0, note: 'Лучший набранный балл по задаче среди всех попыток']
  last_score integer [not null, default: 0, note: 'Балл за последнюю попытку']
  is_completed boolean [not null, default: false, note: 'Флаг, засчитана ли задача как завершённая']

  first_opened_at timestamp [note: 'Когда пользователь впервые открыл задачу']
  started_at timestamp [note: 'Когда пользователь реально начал решать задачу']
  first_success_at timestamp [note: 'Когда задача впервые была успешно решена']
  completed_at timestamp [note: 'Когда задача была окончательно засчитана']
  last_submission_at timestamp [note: 'Когда была последняя отправка решения по задаче']
  last_activity_at timestamp [note: 'Когда была любая последняя активность по задаче']

  created_at timestamp [not null, default: `now()`, note: 'Когда запись прогресса по задаче была создана']
  updated_at timestamp [not null, default: `now()`, note: 'Когда запись прогресса по задаче последний раз обновлялась']

  indexes {
    (user_id, task_id) [unique, name: 'uq_task_progress_user_task']
    (user_id, module_id) [name: 'idx_task_progress_user_module']
    (user_id, course_id) [name: 'idx_task_progress_user_course']
    task_id [name: 'idx_task_progress_task_id']
    status [name: 'idx_task_progress_status']
    is_completed [name: 'idx_task_progress_is_completed']
  }
}

Table task_submissions [note: 'История всех отправок решения по задачам. Одна запись = одна конкретная попытка пользователя'] {
  id bigint [primary key, increment, note: 'Внутренний id попытки']

  user_id bigint [not null, note: 'Id пользователя из UserService/AuthService']
  course_id bigint [not null, note: 'Id курса из Course Service']
  module_id bigint [not null, note: 'Id модуля из Course Service']
  task_id bigint [not null, note: 'Id задачи из Course Service']

  submission_number integer [not null, note: 'Порядковый номер попытки по этой задаче для данного пользователя']
  language varchar(50) [not null, note: 'Язык программирования отправленного решения, например JAVA или PYTHON']
  source_code text [not null, note: 'Исходный код, который пользователь отправил на проверку']

  status submission_status [not null, default: 'QUEUED', note: 'Технический статус обработки попытки: в очереди, выполняется, завершена или упала']
  verdict submission_verdict [note: 'Итоговый вердикт проверки после завершения прогона']
  score integer [not null, default: 0, note: 'Баллы, начисленные именно за эту попытку']
  passed_tests_count integer [not null, default: 0, note: 'Сколько тестов прошло успешно в этой попытке']
  total_tests_count integer [not null, default: 0, note: 'Сколько тестов всего проверялось в этой попытке']

  executor_request_id varchar(255) [note: 'Id джобы/запроса в CodeExecutorService для трассировки']
  error_message text [note: 'Техническая ошибка проверки, если прогон не удалось выполнить корректно']

  submitted_at timestamp [not null, default: `now()`, note: 'Когда пользователь нажал кнопку отправки решения']
  started_at timestamp [note: 'Когда CodeExecutor реально начал прогон этой попытки']
  finished_at timestamp [note: 'Когда проверка этой попытки закончилась']

  created_at timestamp [not null, default: `now()`, note: 'Когда запись попытки была создана в БД']
  updated_at timestamp [not null, default: `now()`, note: 'Когда запись попытки последний раз обновлялась']

  indexes {
    (user_id, task_id, submission_number) [unique, name: 'uq_task_submissions_user_task_number']
    (user_id, task_id) [name: 'idx_task_submissions_user_task']
    task_id [name: 'idx_task_submissions_task_id']
    status [name: 'idx_task_submissions_status']
    verdict [name: 'idx_task_submissions_verdict']
    submitted_at [name: 'idx_task_submissions_submitted_at']
    executor_request_id [name: 'idx_task_submissions_executor_request_id']
  }
}

Table submission_test_results [note: 'Результаты прогона по каждому отдельному тесту внутри одной попытки. Нужна для детального разбора, какие тесты прошли, а какие нет'] {
  id bigint [primary key, increment, note: 'Внутренний id результата отдельного теста']

  submission_id bigint [not null, note: 'Ссылка на попытку из task_submissions']
  testKey varchar(255) [not null, note: 'Уникальный ключ теста из Course Service (tests[].testKey)']
  test_order integer [not null, note: 'Порядковый номер теста внутри конкретной попытки']

  status test_result_status [not null, note: 'Результат конкретного теста: PASSED, FAILED, ERROR или SKIPPED']
  input_snapshot text [note: 'Фактический вход, который был подан в решение на этом тесте']
  expected_output text [note: 'Ожидаемый вывод для этого теста']
  actual_output text [note: 'Фактический вывод программы пользователя']
  error_output text [note: 'stderr или текст ошибки, если программа завершилась с ошибкой']

  execution_time_ms integer [note: 'Время выполнения на этом тесте в миллисекундах']
  memory_kb integer [note: 'Использованная память на этом тесте в килобайтах']

  created_at timestamp [not null, default: `now()`, note: 'Когда был сохранён результат этого теста']

  indexes {
    (submission_id, test_order) [unique, name: 'uq_submission_test_results_submission_order']
    submission_id [name: 'idx_submission_test_results_submission_id']
    testKey [name: 'idx_submission_test_results_testKey']
    status [name: 'idx_submission_test_results_status']
  }
}

Ref submission_test_results_submission: submission_test_results.submission_id > task_submissions.id
