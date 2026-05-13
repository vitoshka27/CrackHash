# CrackHash v1

Распределенная система для подбора строк по MD5-хэшу методом brute-force.

## 📋 Описание

Система принимает на вход MD5-хэш и максимальную длину строки, разбивает задачу на части и распределяет перебор между несколькими воркерами.

### Принцип работы

1. Клиент отправляет POST-запрос с хэшем и `maxLength`
2. Менеджер создает уникальный `requestId` и делит пространство перебора на части
3. Каждая часть отправляется воркеру через HTTP
4. Воркеры перебирают строки из алфавита `a-z0-9`, вычисляют MD5 и сравнивают с целевым хэшем
5. Результаты возвращаются менеджеру
6. Менеджер агрегирует ответы и предоставляет статус клиенту

## 🏗️ Архитектура

```
┌─────────────┐      HTTP      ┌─────────────┐
│   Client    │ ◄────────────► │   Manager   │
└─────────────┘                └──────┬──────┘
                                      │
                    ┌─────────────────┼─────────────────┐
                    │                 │                 │
                    ▼                 ▼                 ▼
             ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
             │  Worker 1   │  │  Worker 2   │  │  Worker 3   │
             └─────────────┘  └─────────────┘  └─────────────┘
```

### Компоненты

| Модуль | Описание |
|--------|----------|
| `manager` | REST API, управление задачами, отправка задач воркерам |
| `worker` | Получение задач, brute-force перебор, отправка результатов |
| `crackhash-model` | Общие JAXB-модели (генерируются из XSD) |

## 🚀 Быстрый старт

### Требования

- Docker и Docker Compose
- Или Java 11+ и Gradle

### Запуск через Docker Compose

```bash
cd CrackHash
docker-compose up --build
```

Сервисы будут доступны:
- **Manager**: http://localhost:8085
- **Worker 1-3**: внутренние контейнеры

### Остановка

```bash
docker-compose down
```

## 📡 API

### Создать задачу

```bash
curl -X POST http://localhost:8085/api/hash/crack \
  -H "Content-Type: application/json" \
  -d '{"hash":"e2fc714c4727ee9395f324cd2e7f331f","maxLength":4}'
```

Ответ:
```json
{"requestId": "abc123..."}
```

### Получить статус

```bash
curl "http://localhost:8085/api/hash/status?requestId=abc123..."
```

Ответы:
- `IN_PROGRESS` — задача выполняется
- `READY` — найдено совпадение
- `NOT_FOUND` — совпадений нет
- `ERROR` — ошибка/таймаут
- `CANCELLED` — отменено

### Отменить задачу

```bash
curl -X POST "http://localhost:8085/api/hash/cancel?requestId=abc123..."
```

### Отменить все задачи

```bash
curl -X POST http://localhost:8085/api/hash/cancel-all
```

## 🖥️ Веб-интерфейс

Откройте http://localhost:8085 для доступа к UI:
- Генератор MD5-хэшей
- Форма отправки задач
- История запросов
- Просмотр исходного кода
- Стресс-тестирование

## ⚙️ Конфигурация

### Manager (application.yml)

```yaml
server:
  port: 8085
crackhash:
  worker-urls: http://worker-1:8081,http://worker-2:8081,http://worker-3:8081
  timeout: 600000
  manager:
    dispatch-pool-size: 600
```

### Worker (application.yml)

```yaml
server:
  port: 8081
MANAGER_URL: http://manager:8086
```

## 🧪 Нагрузочное тестирование

Встроенный k6-сценарий доступен через UI или напрямую:

```bash
docker exec crackhash-manager-1 k6 run /app/k6-stress.js
```

Параметры через переменные окружения:
- `K6_VUS` — количество виртуальных пользователей (по умолчанию: 2000)
- `K6_DURATION` — длительность теста (по умолчанию: 1m)
- `K6_HASH` — хэш для подбора
- `K6_MAX_LENGTH` — максимальная длина строки

## 🛠️ Локальная разработка

### Сборка

```bash
./gradlew build
```

### Запуск

```bash
# Manager
./gradlew :manager:bootRun

# Worker
./gradlew :worker:bootRun
```

## 📁 Структура проекта

```
CrackHash/
├── crackhash-model/     # XSD схемы и общие модели
├── manager/             # Менеджер (Spring Boot)
│   ├── src/main/java/
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── static/      # Веб-интерфейс
│   │   └── k6-stress.js
│   └── build.gradle
├── worker/              # Воркер (Spring Boot)
│   ├── src/main/java/
│   ├── src/main/resources/
│   │   └── application.yml
│   └── build.gradle
├── docker-compose.yml
├── build.gradle
└── settings.gradle
```

## 🧰 Технологии

- Java 11
- Spring Boot 2.7.x
- Gradle
- Docker Compose
- JAXB (XML binding)
- combinatoricslib3
- k6 (нагрузочное тестирование)

## ⚠️ Ограничения

- Состояние хранится только в памяти менеджера
- После перезапуска менеджера активные задачи теряются
- Простая стратегия разбиения диапазона
- Кооперативная отмена задач (воркер должен проверять флаги)
