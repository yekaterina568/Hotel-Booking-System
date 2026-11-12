package com.hotelbooking;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import liquibase.integration.spring.SpringLiquibase;
import java.util.UUID;

/** Real PostgreSQL upgrade; independent schemas preserve all existing databases. */
class MigrationUpgradeTest {
    String url=System.getenv().getOrDefault("TEST_DATABASE_URL","jdbc:postgresql://127.0.0.1:55433/stage0_test");
    String user=System.getenv().getOrDefault("TEST_DATABASE_USER","stage0");
    String password=System.getenv().getOrDefault("TEST_DATABASE_PASSWORD","stage0-test-only");
    DriverManagerDataSource root() {
        var ds=new DriverManagerDataSource(url,user,password);
        assertTrue(new JdbcTemplate(ds).queryForObject("SELECT current_database()",String.class).startsWith("stage0_test"), "Migration tests require stage0_test* database");
        return ds;
    }
    DriverManagerDataSource schema(String name) { return new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+name+",public",user,password); }
    void migrate(DriverManagerDataSource source,String schema,String changelog,String contexts) throws Exception {
        var l=new SpringLiquibase();l.setDataSource(source);l.setDefaultSchema(schema);l.setChangeLog(changelog);l.setContexts(contexts);l.afterPropertiesSet();
    }
    @Test void populated001UpgradePreservesLegacyRowsAndBenchmarks() throws Exception {
        String schema="stage0_upgrade_"+UUID.randomUUID().toString().replace("-","");
        new JdbcTemplate(root()).execute("CREATE SCHEMA "+schema);var ds=schema(schema);var sql=new JdbcTemplate(ds);
        migrate(ds,schema,"classpath:001-only.yaml","test");
        sql.update("INSERT INTO guests(passport_num,first_name,last_name,phone_num) VALUES ('LEGACY0001','Legacy','Guest','+77000000000')");
        sql.update("INSERT INTO rooms(room_number,room_type,cleaning_state,price_per_night) VALUES (101,'Single','Cleaned',15000)");
        // Deliberate historical overlaps: upgrade must preserve, not delete/rewrite.
        sql.update("INSERT INTO bookings(guest_id,room_id,expected_arrival,state) SELECT 1,1,'2026-11-12T14:00:00+05:00','Confirmed' FROM generate_series(1,1000)");
        sql.update("INSERT INTO bookings(guest_id,room_id,expected_arrival,state) VALUES (1,1,'2026-10-01T14:00:00+05:00','Cancelled')");
        sql.update("INSERT INTO refunds(booking_id,amount,refund_reason,state) VALUES (1001,100,'Legacy refund','Completed')");
        sql.update("INSERT INTO keycards(booking_id,card_uid,is_active) VALUES (1001,'LEGACY-CARD',false)");
        sql.update("INSERT INTO bookings_archive(booking_id,guest_id,room_id,expected_arrival,state) VALUES (1001,1,1,'2026-10-01T14:00:00+05:00','Cancelled')");
        var bookings=sql.queryForList("SELECT booking_id,guest_id,room_id,expected_arrival,state::text,created_at FROM bookings ORDER BY booking_id");
        var rooms=sql.queryForList("SELECT room_id,room_number,room_type::text,cleaning_state::text,price_per_night FROM rooms");
        var refunds=sql.queryForList("SELECT refund_id,booking_id,amount,refund_reason,state::text FROM refunds");
        String checksum=sql.queryForObject("SELECT md5sum FROM databasechangelog WHERE id='001'",String.class);
        migrate(ds,schema,"classpath:db/changelog/db.changelog-master.yaml","test");
        assertEquals(bookings,sql.queryForList("SELECT booking_id,guest_id,room_id,expected_arrival,state::text,created_at FROM bookings ORDER BY booking_id"));
        assertEquals(rooms,sql.queryForList("SELECT room_id,room_number,room_type::text,cleaning_state::text,price_per_night FROM rooms"));
        assertEquals(refunds,sql.queryForList("SELECT refund_id,booking_id,amount,refund_reason,state::text FROM refunds"));
        assertEquals(checksum,sql.queryForObject("SELECT md5sum FROM databasechangelog WHERE id='001'",String.class));
        assertEquals(1001,sql.queryForObject("SELECT count(*) FROM booking_costs WHERE estimated_legacy",Integer.class));
        assertEquals(1001,sql.queryForObject("SELECT count(*) FROM bookings WHERE check_in_date IS NULL",Integer.class));
        assertEquals(1,sql.queryForObject("SELECT count(*) FROM rooms JOIN hotels USING(hotel_id)",Integer.class));
        assertEquals(1,sql.queryForObject("SELECT count(*) FROM keycards WHERE card_uid='LEGACY-CARD'",Integer.class));
        assertEquals(1,sql.queryForObject("SELECT count(*) FROM bookings_archive WHERE booking_id=1001",Integer.class));
        assertEquals(0,sql.queryForObject("SELECT count(*) FROM hotels WHERE name='Stage 0 Demo Hotel'",Integer.class));
        migrate(ds,schema,"classpath:db/changelog/db.changelog-master.yaml","test"); // repeat run/checksums
        assertEquals(3,sql.queryForObject("SELECT count(*) FROM databasechangelog",Integer.class));
        // Evidence is intentionally retained in this isolated test schema.
        System.out.println("UPGRADE_EVIDENCE schema="+schema+" bookings=1001 original_rows_unchanged=true seed_absent=true");
    }
    @Test void freshDatabaseMigrationsAndDevelopmentSeedAreOptIn() throws Exception {
        String schema="stage0_fresh_"+UUID.randomUUID().toString().replace("-","");
        new JdbcTemplate(root()).execute("CREATE SCHEMA "+schema);var ds=schema(schema);var sql=new JdbcTemplate(ds);
        migrate(ds,schema,"classpath:db/changelog/db.changelog-master.yaml","test");
        assertEquals(1,sql.queryForObject("SELECT count(*) FROM hotels",Integer.class));assertEquals(0,sql.queryForObject("SELECT count(*) FROM guests",Integer.class));
        migrate(ds,schema,"classpath:db/changelog/db.changelog-master.yaml","dev");
        assertEquals(2,sql.queryForObject("SELECT count(*) FROM rooms",Integer.class));assertEquals(1,sql.queryForObject("SELECT count(*) FROM guests",Integer.class));
        migrate(ds,schema,"classpath:db/changelog/db.changelog-master.yaml","dev");
        assertEquals(2,sql.queryForObject("SELECT count(*) FROM rooms",Integer.class));
        System.out.println("FRESH_EVIDENCE schema="+schema+" opt_in_seed=true repeat_run=true");
    }
}
