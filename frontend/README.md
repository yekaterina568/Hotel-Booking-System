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

3. **Payments (Zeinetula Ayan):**
   - Панель оплаты брони (карта / наличные / перевод) → `POST /api/payments` → таблица `payments` в PostgreSQL.
   - История платежей из `GET /api/payments`. Ошибки бэкенда/БД показываются как есть, без имитации успеха.

## Инструкция по запуску:
Откройте файл `index.html` в любом современном браузере.

Для работы оплаты нужны запущенные PostgreSQL (`docker compose up -d`) и backend (`./mvnw spring-boot:run` в папке `backend`, порт 8080).