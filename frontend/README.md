# Hotel Booking System — Frontend Prototype

Этот модуль представляет собой прототип интерфейса (Mock UI) для проекта **Hotel Booking System**.

## Реализованный функционал:
1. **Front-Desk Dashboard (Aruzhan Tanatar):**
   - Очередь приоритетного заселения гостей (выборка комнат со статусом `Cleaned`).
   - Функция автоматического оформления Check-in.
   - Модуль валидации паспорта (Regex-проверка).
2. **Housekeeping Dashboard (Zeinetula Ayan):**
   - Интерфейс для службы клининга.
   - Переключение 3 статусов комнат: `Cleaned`, `Cleaning`, `Dirty`.

## Инструкция по запуску:
Откройте файл `index.html` в любом современном браузере.

## Админ-панель

Откройте **Administration** на стойке регистрации или перейдите на `http://localhost:3000/admin.html`. Здесь можно управлять отелями и отзывами, а также просматривать номера, гостей, бронирования, архив бронирований, возвраты и ключ-карты. В рабочих таблицах есть поиск и, где применимо, фильтр по статусу.

Запустите из корня проекта, указав учетные данные для входа и параметры PostgreSQL в `.env`.

```sh
set -a
. ./.env
set +a
export HOTEL_AUTH_USERNAME
export HOTEL_AUTH_PASSWORD
docker compose up -d
(cd backend && bash ./mvnw spring-boot:run)
```

Указанные учетные данные используются для входа на стойке регистрации и в админ-панели. Все API-маршруты защищены HTTP Basic Authentication. При запуске бэкенда Liquibase создаст таблицы `hotels` и `reviews` и привяжет существующие номера к Grand Almaty.
