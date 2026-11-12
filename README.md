# Hotel Booking System

## Запуск backend

Нужны JDK 25 и запущенный Docker Desktop.

Из корня проекта один раз создайте `.env`, если его ещё нет:

```sh
cp .env.example .env
```

Укажите в `.env` пароль PostgreSQL. Для существующего Docker volume используйте
имя базы, пользователя и пароль, с которыми он был создан.

Запустите базу и приложение из корня проекта:

```sh
docker compose up -d --wait postgres
cd backend
./mvnw spring-boot:run
```

Если терминал уже находится в `backend`, повторять `cd backend` не нужно.
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

Проверка backend из каталога `backend` при запущенной базе:

```sh
./mvnw test
```

## Запуск frontend

Откройте `frontend/index.html` в браузере. Подробности — в [frontend/README.md](frontend/README.md).
