# CrackHash

`CrackHash` — учебный распределенный сервис для brute-force поиска строки по MD5-хэшу. Проект построен как multi-module Gradle приложение с двумя Spring Boot сервисами: менеджером и воркерами.

В этом репозитории должен находиться только сам проект `CrackHash`, без внешних архивов, вспомогательных скриптов и локального мусора сборки.

## Что делает проект

Система принимает MD5-хэш и максимальную длину искомой строки, разбивает задачу на части и распределяет перебор между несколькими worker-узлами.

Общий сценарий работы:

1. Клиент отправляет хэш и `maxLength` в `manager`.
2. `manager` создает уникальный `requestId`.
3. `manager` делит пространство перебора на части по количеству доступных воркеров.
4. Каждому `worker` отправляется своя часть задачи.
5. `worker` перебирает кандидаты из алфавита `a-z0-9`, вычисляет MD5 и сравнивает с целевым хэшем.
6. Найденные результаты возвращаются обратно в `manager`.
7. `manager` агрегирует ответы и отдает итоговый статус клиенту.

## Архитектура

Проект состоит из трех модулей:

- `manager` — центральный сервис, который принимает запросы от пользователя, рассылает части задачи воркерам и собирает результаты;
- `worker` — вычислительный сервис, который выполняет brute-force для своей части диапазона;
- `crackhash-model` — общий модуль с JAXB-моделями, генерируемыми из XSD-схем.

### Manager

Основные обязанности менеджера:

- внешний REST API для постановки задачи и проверки статуса;
- хранение состояния запросов в памяти;
- отправка задач воркерам по HTTP;
- прием ответов от воркеров;
- отмена одной задачи или всех задач сразу;
- контроль таймаутов через фоновую задачу `@Scheduled`.

Основные публичные endpoint'ы:

- `POST /api/hash/crack`
- `GET /api/hash/status?requestId=...`
- `POST /api/hash/cancel?requestId=...`
- `POST /api/hash/cancel-all`

Внутренний callback от воркеров:

- `PATCH /internal/api/manager/hash/crack/request`

### Worker

Основные обязанности воркера:

- прием части задачи от менеджера;
- генерация слов длиной от `1` до `maxLength`;
- вычисление MD5 для каждого кандидата;
- отправка результатов обратно в менеджер;
- поддержка отмены конкретной задачи и глобальной отмены.

Внутренние endpoint'ы:

- `POST /internal/api/worker/hash/crack/task`
- `POST /internal/api/worker/hash/crack/cancel?requestId=...`
- `POST /internal/api/worker/hash/crack/cancel-all`

### Shared Model

Модуль `crackhash-model` содержит общие модели обмена между сервисами. JAXB-классы генерируются из XSD-схем во время сборки, чтобы `manager` и `worker` использовали один и тот же контракт данных.

## Структура проекта

```text
CrackHash/
├─ crackhash-model/   # XSD-схемы и общие модели
├─ manager/           # Spring Boot приложение менеджера
├─ worker/            # Spring Boot приложение воркера
├─ gradle/            # файлы Gradle Wrapper
├─ build.gradle       # корневая Gradle-конфигурация
├─ settings.gradle    # описание модулей проекта
└─ docker-compose.yml # запуск manager + workers в контейнерах
```

## Технологии

- Java 11
- Spring Boot 2.7.x
- Gradle
- Docker / Docker Compose
- JAXB
- combinatoricslib3
- k6

## Состояния запроса

Менеджер хранит состояние задачи в памяти и использует следующие статусы:

- `IN_PROGRESS` — задача еще выполняется;
- `READY` — все части завершены, найден хотя бы один результат;
- `NOT_FOUND` — все части завершены, совпадений нет;
- `ERROR` — ошибка при рассылке, таймаут или некорректное состояние;
- `CANCELLED` — задача отменена пользователем.

## Конфигурация

### Manager

Файл: `manager/src/main/resources/application.yml`

Основные параметры:

- `server.port=8085`
- `crackhash.worker-urls` — список URL воркеров через запятую
- `crackhash.timeout` — таймаут задачи в миллисекундах
- `crackhash.manager.dispatch-pool-size` — размер пула потоков для рассылки задач

### Worker

Файл: `worker/src/main/resources/application.yml`

Основные параметры:

- `server.port=8081`
- `MANAGER_URL` — адрес менеджера для отправки результатов

## Запуск через Docker Compose

Это основной и рекомендуемый способ запуска всей системы.

### Требования

- Docker
- Docker Compose

### Запуск

```powershell
cd CrackHash
docker-compose up --build
```

После запуска поднимутся:

- `manager` на `http://localhost:8085`
- `worker-1`
- `worker-2`
- `worker-3`

### Остановка

```powershell
docker-compose down
```

## Запуск без Docker

Можно запустить сервисы отдельно.

### Сборка

```powershell
./gradlew build
```

### Запуск manager

```powershell
./gradlew :manager:bootRun
```

### Запуск worker

```powershell
./gradlew :worker:bootRun
```

Если запускать не через Docker, нужно вручную убедиться, что `manager` и `worker` видят друг друга по корректным URL.

## Примеры API

### Поставить задачу на взлом

```powershell
curl -X POST http://localhost:8085/api/hash/crack ^
  -H "Content-Type: application/json" ^
  -d "{\"hash\":\"e2fc714c4727ee9395f324cd2e7f331f\",\"maxLength\":4}"
```

Этот хэш соответствует строке `abcd`.

В ответ приходит JSON с `requestId`.

### Узнать статус

```powershell
curl "http://localhost:8085/api/hash/status?requestId=<REQUEST_ID>"
```

### Отменить одну задачу

```powershell
curl -X POST "http://localhost:8085/api/hash/cancel?requestId=<REQUEST_ID>"
```

### Отменить все активные задачи

```powershell
curl -X POST http://localhost:8085/api/hash/cancel-all
```

## Веб-интерфейс

После запуска менеджера:

- `http://localhost:8085`

В UI есть:

- генератор MD5 для проверки строк;
- форма отправки задачи;
- история запросов с периодическим опросом статуса;
- просмотрщик исходников проекта;
- страница стресс-теста.

## Нагрузочное тестирование

В контейнер менеджера встроен `k6`, а в проекте уже есть сценарий стресс-теста.

Что умеет stress UI:

- запускать подготовленный сценарий нагрузки;
- показывать текущие значения concurrency, p95 latency, request rate, errors;
- выводить итоговое заключение после завершения теста.

## Особенности сборки

- `crackhash-model` генерирует JAXB-классы из XSD при сборке;
- `manager` и `worker` собираются как Spring Boot jar;
- используется Gradle Wrapper, поэтому отдельная установка Gradle не обязательна.

## Ограничения текущей реализации

- состояние задач хранится только в памяти менеджера;
- после перезапуска `manager` активные задачи теряются;
- стратегия разбиения диапазона простая и учебная;
- отмена задач кооперативная: воркер должен периодически проверять флаги отмены.
