# Hotel Booking System

Stage 0 connects the frontend to PostgreSQL through authenticated Spring Boot APIs.
See [implementation and limits](docs/STAGE0_IMPLEMENTATION_REPORT.md),
[executed tests](docs/STAGE0_TEST_REPORT.md), [team demo](docs/STAGE0_DEMO_GUIDE.md),
and the [future roadmap](docs/PROJECT_ROADMAP_3_WEEKS.md). Stages 1–3 are plans only.

## Запуск backend

Нужны JDK 25 и запущенный Docker Desktop.

Из корня проекта один раз создайте `.env`, если его ещё нет:

```sh
cp .env.example .env
```

Укажите в `.env` пароль PostgreSQL и отдельный `HOTEL_STAFF_PASSWORD`. Для существующего Docker volume используйте
имя базы, пользователя и пароль, с которыми он был создан.

Запустите базу и приложение из корня проекта:

```sh
docker compose up -d --wait postgres
cd backend
./mvnw spring-boot:run
```

Если терминал уже находится в `backend`, повторять `cd backend` не нужно.
Для демонстрационных гостей и комнат включите профиль `dev`: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`.
Без профиля `dev` демонстрационные записи не добавляются.
Spring Boot читает корневой `.env` автоматически. Переменные, экспортированные
в терминале, имеют приоритет; если раньше использовались другие настройки, сбросьте их:

```sh
unset POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD POSTGRES_HOST POSTGRES_PORT SPRING_DATASOURCE_URL
```

Docker PostgreSQL доступен на `localhost:55432`, backend — на `http://localhost:8080`.
Порт 55432 отделяет базу проекта от локальных PostgreSQL на портах 5432 и 5433.
Для другого порта задайте `POSTGRES_PORT` в `.env` и повторите `docker compose up`.
Liquibase применяет миграции при запуске backend; сама база должна уже существовать.
Изменение `POSTGRES_DB` не создаёт новую базу в существующем Docker volume.

Автотесты используют отдельную PostgreSQL на порту 55433, а не вашу рабочую базу.
Из корня запустите `./scripts/start-stage0-postgres.sh`, затем из `backend`:

```sh
./mvnw test
```

## Запуск frontend

Откройте `http://127.0.0.1:8080` в браузере и войдите как `staff` с паролем `HOTEL_STAFF_PASSWORD` из `.env`. Подробности — в [frontend/README.md](frontend/README.md).
