# Week 13 demo guide

Use Java 26 and Docker. Run the commands from the repository root. The demo has its own PostgreSQL container, volume, database, and port. Start Docker before running the commands. [Java 26 download](https://adoptium.net/temurin/releases/?version=26)

## Start PostgreSQL and the application

In terminal one, create the dedicated PostgreSQL 17 container:

```sh
export POSTGRES_DB=week13_demo POSTGRES_USER=week13
export POSTGRES_PASSWORD="$(openssl rand -hex 16)"
export HOTEL_AUTH_USERNAME=week13-admin
export HOTEL_AUTH_PASSWORD="$(openssl rand -hex 16)"
export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:55413/week13_demo

docker run -d --name hotel-week13-demo-db \
  -p 127.0.0.1:55413:5432 -v hotel-week13-demo-data:/var/lib/postgresql/data \
  -e POSTGRES_DB -e POSTGRES_USER -e POSTGRES_PASSWORD postgres:17
docker exec hotel-week13-demo-db pg_isready -U week13 -d week13_demo
java -version
printf 'Demo username: %s\nDemo password: %s\n' "$HOTEL_AUTH_USERNAME" "$HOTEL_AUTH_PASSWORD"
(cd backend && ./mvnw spring-boot:run)
```

If the readiness check reports that PostgreSQL is starting, rerun it once the container is ready. For a later run, use `docker start hotel-week13-demo-db` with the same database credentials used to initialize its volume. Keep the generated password in your local environment for reuse.

After the backend has started and Liquibase has finished, open terminal two and run:

```sh
docker exec -i hotel-week13-demo-db psql -X -U week13 -d week13_demo < docs/week13/demo-data.sql
python3 -m http.server 3000 --bind 127.0.0.1 --directory frontend
```

Open [Administration](http://127.0.0.1:3000/admin.html) and sign in using the demo username and password from terminal one. The SQL seed uses synthetic records, runs only in a dedicated database named `week13_demo`, and skips repeated runs after a successful seed.

## Demonstrate the work

1. **Dashboard.** Show the current inventory and activity cards. Select Riverside Hotel, choose **Last 7 days**, and click **Apply filters**. Select **All time** and compare the booking count. The inventory is unchanged by the date filter.
2. **Statistics.** Scroll to the booking trend, status distribution, cleaning status, bookings by hotel, published rating distribution, and refund trend. Open **View chart data** under one chart to show the underlying counts. Try a custom range with no activity.
3. **Reports.** Open **Reports**, choose **Booking report**, select a hotel and **Cancelled**, and generate the preview. Point out the generation time, filters, summary, and columns. Click **Download CSV** and open it in Excel. Click **Print / Save PDF**, then use the browser’s Print command.
4. **Room management.** In **Rooms**, add room 913 to Riverside Hotel, select Double and Dirty, and enter a price of 170.50. Edit it to Suite, mark it Cleaned, move it to Summit Hotel, and set the price to 199.75. Refresh the page and search for room 913. If the room number was already used in a previous demo, choose an available number.
5. **Bookings.** Filter Bookings by hotel and Confirmed. Open the details of a synthetic booking, then cancel and archive it using the confirmation dialogs. Open **View details** from the archive to show the two recorded state changes.
6. **Database verification.** Run the SQL below in another terminal. Adjust the room number if needed.

```sh
docker exec -i hotel-week13-demo-db psql -X -U week13 -d week13_demo <<'SQL'
SELECT h.name, r.room_number, r.room_type, r.cleaning_state, r.price_per_night
FROM rooms r JOIN hotels h USING (hotel_id)
WHERE r.room_number = 913;

SELECT state, count(*) FROM bookings GROUP BY state ORDER BY state;

SELECT b.booking_id, b.previous_state, b.new_state, b.changed_at
FROM booking_status_history b
ORDER BY b.changed_at DESC LIMIT 5;
SQL
```

## Speaking script in English

> This week I added a dashboard and reports to our hotel administration panel. The dashboard shows real information from PostgreSQL, including bookings, room cleaning status, refunds, and published reviews. An administrator can choose a hotel and a date range to compare activity. The inventory cards show how many rooms and hotels exist today. In Reports, the administrator can preview the filtered data, download a CSV for Excel, or print the report. I also added room editing and booking management. A room can have the same number at different hotels. The system follows the booking rules and records changes in the booking history. These features help staff find information faster and manage bookings with fewer mistakes.

## Короткие ответы на вопросы преподавателя

| Вопрос | Ответ |
| --- | --- |
| Где считаются показатели? | PostgreSQL считает количество записей и средний рейтинг. Браузер получает готовые числа и строит графики. |
| Что меняется при выборе даты? | Дата ограничивает бронирования и отзывы по времени создания, а возвраты по дате обработки. Текущее число номеров зависит только от выбранного отеля. |
| Как защищены отчёты? | Сервер проверяет роль администратора. В отчётах отсутствуют номера паспортов и телефоны. В CSV потенциальные формулы записываются как текст. |
| Почему можно создать одинаковый номер в двух отелях? | PostgreSQL проверяет уникальность пары из идентификатора отеля и номера комнаты. |
| Когда можно удалить комнату? | Если на комнату нет ссылок в бронированиях и архиве. Это проверяется ограничениями базы данных. |
| Зачем нужен Liquibase? | Он выполняет новые изменения структуры по порядку и хранит историю уже применённых изменений. При обновлении старые записи сохраняются. |
| Какие данные есть в истории? | Триггер базы данных записывает старый статус, новый статус и время изменения. Для старых записей видны даты, которые уже были сохранены. |
| Почему нет выручки и процента загрузки? | В проверенной версии ветки main нет интегрированных платежей. Для исторической загрузки нужны полные фактические даты заселения и данные о доступных номерах за каждый день. |
