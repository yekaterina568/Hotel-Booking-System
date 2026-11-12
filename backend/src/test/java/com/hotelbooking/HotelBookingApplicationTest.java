package com.hotelbooking;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.hotelbooking.room.service.RoomService;
import com.github.benmanes.caffeine.cache.Cache;
import tools.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HotelBookingApplicationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void dedicatedDatabase(org.springframework.test.context.DynamicPropertyRegistry properties) throws Exception {
        String url=System.getenv().getOrDefault("TEST_DATABASE_URL","jdbc:postgresql://127.0.0.1:55433/stage0_test");
        String user=System.getenv().getOrDefault("TEST_DATABASE_USER","stage0");
        String password=System.getenv().getOrDefault("TEST_DATABASE_PASSWORD","stage0-test-only");
        // Check before Spring/Liquibase starts; ambient SPRING_DATASOURCE_* values
        // must never redirect this test suite to a working database.
        try(var connection=java.sql.DriverManager.getConnection(url,user,password);
            var statement=connection.createStatement(); var result=statement.executeQuery("SELECT current_database()")) {
            assertTrue(result.next() && result.getString(1).startsWith("stage0_test"), "Use a dedicated stage0_test* PostgreSQL database");
        }
        properties.add("spring.datasource.url",() -> url);
        properties.add("spring.datasource.username",() -> user);
        properties.add("spring.datasource.password",() -> password);
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate sql;
    @Autowired CacheManager caches;
    @Autowired RoomService roomService;
    @Autowired PlatformTransactionManager transactions;
    final ObjectMapper json=new ObjectMapper();
    final HttpClient http=HttpClient.newHttpClient();
    final LocalDate today=LocalDate.now(ZoneId.of("Asia/Almaty"));
    @BeforeEach void requireIsolatedPostgresDatabase() {
        String database=sql.queryForObject("SELECT current_database()",String.class);
        assertTrue(database.startsWith("stage0_test"), "Tests require a dedicated stage0_test* PostgreSQL database");
    }
    record Reply(int status, JsonNode body) {}
    record Fixture(long hotel,long guest,long room) {}
    String key() { return UUID.randomUUID().toString(); }
    Reply call(String method,String path,Object data) throws Exception { return call(method,path,data,true); }
    Reply call(String method,String path,Object data,boolean authenticated) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20));
        if(authenticated) b.header("Authorization","Basic "+Base64.getEncoder().encodeToString("test-staff:test-password".getBytes()));
        b.header("Content-Type","application/json"); b.method(method,data==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data)));
        var response=http.send(b.build(),HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(),response.body().isBlank()?json.createObjectNode():json.readTree(response.body()));
    }
    JsonNode expect(int status,String method,String path,Object data) throws Exception {
        var r=call(method,path,data); assertEquals(status,r.status(),r.body().toString()); return r.body();
    }
    Fixture fixture(String cleaning) throws Exception {
        long hotel=expect(201,"POST","/api/hotels",Map.of("name","Test "+key(),"city","Almaty","timezone","Asia/Almaty")).get("hotelId").asLong();
        long guest=expect(201,"POST","/api/guests",Map.of("passportNum",key().replace("-","").substring(0,16).toUpperCase(),"firstName","Test","lastName","Guest","phoneNum","+77000000000")).get("guestId").asLong();
        long room=expect(201,"POST","/api/rooms",Map.of("hotelId",hotel,"roomNumber",101,"roomType","SINGLE","cleaningState",cleaning,"pricePerNight",15000)).get("roomId").asLong();
        return new Fixture(hotel,guest,room);
    }
    Map<String,Object> bookingBody(Fixture f,LocalDate start,LocalDate end) {
        return Map.of("guestId",f.guest(),"roomId",f.room(),"checkInDate",start.toString(),"checkOutDate",end.toString(),"expectedArrival",start+"T14:00:00+05:00");
    }
    long booking(Fixture f) throws Exception { return expect(201,"POST","/api/bookings",bookingBody(f,today,today.plusDays(2))).get("bookingId").asLong(); }
    JsonNode pay(long id,String key,boolean decline) throws Exception { return expect(201,"POST","/api/payments",Map.of("bookingId",id,"idempotencyKey",key,"simulateDecline",decline)); }
    Map<String,Object> refund(long id,String amount,String key) { return Map.of("bookingId",id,"amount",amount,"refundReason","Test cancellation","idempotencyKey",key); }

    @Test void bookingCreatesDatedCostSnapshotAndCanBeRetrieved() throws Exception {
        var f=fixture("CLEANED"); long id=booking(f);
        assertEquals("CONFIRMED",expect(200,"GET","/api/bookings/"+id,null).get("state").asString());
        var c=expect(200,"GET","/api/bookings/"+id+"/cost",null);
        assertEquals(2,c.get("nights").asInt()); assertEquals(31000,c.get("total").decimalValue().intValue());
        assertEquals(1,sql.queryForObject("SELECT count(*) FROM booking_costs WHERE booking_id=?",Integer.class,id));
    }
    @Test void cancellationReleasesDatesAndCannotRepeat() throws Exception {
        var f=fixture("CLEANED"); long id=booking(f); expect(200,"POST","/api/bookings/"+id+"/cancel",null);
        expect(409,"POST","/api/bookings/"+id+"/cancel",null); booking(f);
    }
    @Test void overlapIsRejectedButAdjacentStayIsAllowed() throws Exception {
        var f=fixture("CLEANED"); booking(f); expect(409,"POST","/api/bookings",bookingBody(f,today.plusDays(1),today.plusDays(3)));
        expect(201,"POST","/api/bookings",bookingBody(f,today.plusDays(2),today.plusDays(3)));
    }
    @Test void concurrentBookingRequestsHaveOneWinner() throws Exception {
        var f=fixture("CLEANED"); var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Integer> attempt=() -> {start.await(); return call("POST","/api/bookings",bookingBody(f,today,today.plusDays(1))).status();};
            var a=pool.submit(attempt);var b=pool.submit(attempt);start.countDown();
            assertEquals(List.of(201,409),java.util.stream.Stream.of(a.get(),b.get()).sorted().toList());
        }
        assertEquals(1,sql.queryForObject("SELECT count(*) FROM bookings WHERE room_id=?",Integer.class,f.room()));
    }
    @Test void availabilityUsesRequestedDates() throws Exception {
        var f=fixture("CLEANED"); booking(f);
        assertFalse(expect(200,"GET","/api/bookings/availability?roomId="+f.room()+"&checkInDate="+today+"&checkOutDate="+today.plusDays(1),null).get("available").asBoolean());
        assertTrue(expect(200,"GET","/api/bookings/availability?roomId="+f.room()+"&checkInDate="+today.plusDays(2)+"&checkOutDate="+today.plusDays(3),null).get("available").asBoolean());
    }
    @Test void cleanCheckInIssuesOneKeycardAndCannotRepeat() throws Exception {
        long id=booking(fixture("CLEANED")); expect(200,"POST","/api/bookings/"+id+"/check-in",null);
        expect(409,"POST","/api/bookings/"+id+"/check-in",null);
        expect(201,"POST","/api/keycards",Map.of("bookingId",id));
        assertEquals(1,sql.queryForObject("SELECT count(*) FROM keycards WHERE booking_id=? AND is_active",Integer.class,id));
    }
    @Test void dirtyRoomCannotCheckIn() throws Exception {
        long id=booking(fixture("DIRTY")); expect(409,"POST","/api/bookings/"+id+"/check-in",null);
        assertEquals(0,sql.queryForObject("SELECT count(*) FROM keycards WHERE booking_id=?",Integer.class,id));
    }
    @Test void cleaningRoomCannotCheckInUntilCleaned() throws Exception {
        var f=fixture("CLEANING");long id=booking(f);expect(409,"POST","/api/bookings/"+id+"/check-in",null);
        expect(200,"PATCH","/api/rooms/"+f.room(),Map.of("cleaningState","CLEANED")); expect(200,"POST","/api/bookings/"+id+"/check-in",null);
    }
    @Test void cancelledOrFutureBookingCannotCheckIn() throws Exception {
        var f=fixture("CLEANED"); long id=booking(f);expect(200,"POST","/api/bookings/"+id+"/cancel",null);expect(409,"POST","/api/bookings/"+id+"/check-in",null);
        long future=expect(201,"POST","/api/bookings",bookingBody(f,today.plusDays(1),today.plusDays(2))).get("bookingId").asLong();
        expect(409,"POST","/api/bookings/"+future+"/check-in",null);
    }
    @Test void priorityQueueReflectsCleaningChangesAndCheckIn() throws Exception {
        var f=fixture("DIRTY");long id=booking(f);
        assertFalse(expect(200,"GET","/api/bookings/priority-queue",null).toString().contains("\"bookingId\":"+id+","));
        expect(200,"PATCH","/api/rooms/"+f.room(),Map.of("cleaningState","CLEANED"));
        assertTrue(expect(200,"GET","/api/bookings/priority-queue",null).toString().contains("\"bookingId\":"+id+","));
        expect(200,"POST","/api/bookings/"+id+"/check-in",null);
        assertFalse(expect(200,"GET","/api/bookings/priority-queue",null).toString().contains("\"bookingId\":"+id+","));
    }
    @Test void paymentPersistsAndRetryIsIdempotent() throws Exception {
        long id=booking(fixture("CLEANED"));String k=key();var a=pay(id,k,false);var b=pay(id,k,false);
        assertEquals(a.get("paymentId"),b.get("paymentId"));assertEquals("SUCCEEDED",a.get("state").asString());
        assertEquals(1,sql.queryForObject("SELECT count(*) FROM payments WHERE booking_id=?",Integer.class,id));
        expect(409,"POST","/api/payments",Map.of("bookingId",id,"idempotencyKey",key(),"simulateDecline",false));
    }
    @Test void declinedPaymentIsStoredAndCanRetryWithNewKey() throws Exception {
        long id=booking(fixture("CLEANED")); assertEquals("DECLINED",pay(id,key(),true).get("state").asString());
        assertEquals("SUCCEEDED",pay(id,key(),false).get("state").asString());
        assertEquals(2,sql.queryForObject("SELECT count(*) FROM payments WHERE booking_id=?",Integer.class,id));
    }
    @Test void cancelledBookingCannotBePaidAndKeyCannotChangePayload() throws Exception {
        long id=booking(fixture("CLEANED"));String k=key();pay(id,k,true);
        expect(409,"POST","/api/payments",Map.of("bookingId",id,"idempotencyKey",k,"simulateDecline",false));
        expect(200,"POST","/api/bookings/"+id+"/cancel",null);
        expect(409,"POST","/api/payments",Map.of("bookingId",id,"idempotencyKey",key(),"simulateDecline",false));
    }
    @Test void refundPersistsAndRetryDoesNotDuplicate() throws Exception {
        long id=booking(fixture("CLEANED"));pay(id,key(),false);expect(200,"POST","/api/bookings/"+id+"/cancel",null);
        String k=key();var a=expect(201,"POST","/api/refunds",refund(id,"10000.00",k));var b=expect(201,"POST","/api/refunds",refund(id,"10000.00",k));
        assertEquals(a.get("refundId"),b.get("refundId"));assertEquals("COMPLETED",a.get("state").asString());
        assertEquals(1,sql.queryForObject("SELECT count(*) FROM refunds WHERE booking_id=? AND payment_id IS NOT NULL",Integer.class,id));
        expect(200,"GET","/api/refunds/"+a.get("refundId").asLong(),null);
    }
    @Test void refundRejectsUnpaidActiveNegativeAndOverBalance() throws Exception {
        long id=booking(fixture("CLEANED"));expect(409,"POST","/api/refunds",refund(id,"1",key()));pay(id,key(),false);
        expect(409,"POST","/api/refunds",refund(id,"1",key()));expect(200,"POST","/api/bookings/"+id+"/cancel",null);
        expect(400,"POST","/api/refunds",refund(id,"-1",key()));expect(201,"POST","/api/refunds",refund(id,"30000",key()));
        expect(409,"POST","/api/refunds",refund(id,"1000.01",key()));expect(201,"POST","/api/refunds",refund(id,"1000",key()));
    }
    @Test void hotelRoomUniquenessAndGuestForeignKeyAreEnforced() throws Exception {
        var f=fixture("CLEANED");var data=Map.of("hotelId",f.hotel(),"roomNumber",101,"roomType","DOUBLE","cleaningState","CLEANED","pricePerNight",10);
        expect(409,"POST","/api/rooms",data);
        var other=fixture("CLEANED");assertNotEquals(f.room(),other.room());
        expect(404,"POST","/api/rooms",Map.of("hotelId",Long.MAX_VALUE,"roomNumber",101,"roomType","DOUBLE","cleaningState","CLEANED","pricePerNight",10));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,() -> sql.update("INSERT INTO rooms(hotel_id,room_number,price_per_night) VALUES (?,?,?)",Long.MAX_VALUE,999,10));
        booking(f);expect(409,"DELETE","/api/guests/"+f.guest(),null);
    }
    @Test void databaseRejectsDirectOverlappingDatedInsert() throws Exception {
        var f=fixture("CLEANED");booking(f);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,() -> sql.update("INSERT INTO bookings(guest_id,room_id,expected_arrival,check_in_date,check_out_date) VALUES (?,?,now(),?,?)",f.guest(),f.room(),today,today.plusDays(1)));
    }
    @Test void roomCacheHitsAndInvalidatesAfterUpdateCreateAndDelete() throws Exception {
        var f=fixture("CLEANED");caches.getCache("rooms").clear();roomService.findAll();
        var nativeCache=(Cache<?,?>)caches.getCache("rooms").getNativeCache();long before=nativeCache.stats().hitCount();roomService.findAll();
        assertTrue(nativeCache.stats().hitCount()>before);
        expect(200,"PATCH","/api/rooms/"+f.room(),Map.of("cleaningState","DIRTY","pricePerNight",20000));
        var updated=roomService.findAll().stream().filter(r -> r.roomId()==f.room()).findFirst().orElseThrow();assertEquals("DIRTY",updated.cleaningState().name());
        long newRoom=expect(201,"POST","/api/rooms",Map.of("hotelId",f.hotel(),"roomNumber",102,"roomType","SINGLE","cleaningState","CLEANED","pricePerNight",10)).get("roomId").asLong();
        assertTrue(roomService.findAll().stream().anyMatch(r -> r.roomId()==newRoom));expect(204,"DELETE","/api/rooms/"+newRoom,null);
        assertFalse(roomService.findAll().stream().anyMatch(r -> r.roomId()==newRoom));
    }
    @Test void dtoResponsesOmitSensitiveAndInternalFields() throws Exception {
        var f=fixture("CLEANED");long id=booking(f);expect(200,"POST","/api/bookings/"+id+"/check-in",null);
        for(String path:List.of("/api/guests","/api/bookings","/api/keycards","/api/archive","/api/payments","/api/refunds")) {
            var text=expect(200,"GET",path,null).toString();assertFalse(text.contains("passportNum"));assertFalse(text.contains("phoneNum"));assertFalse(text.contains("cardUid"));assertFalse(text.contains("idempotencyKey"));
        }
    }
    @Test void unauthenticatedApiDeniedAndStaticFrontendAvailable() throws Exception {
        for(String path:List.of("/api/rooms","/api/guests","/api/refunds","/api/bookings/priority-queue")) assertEquals(401,call("GET",path,null,false).status());
        var r=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/")).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,r.statusCode());assertTrue(r.body().contains("refundForm"));assertTrue(r.body().contains("paymentForm"));
    }
    @Test void invalidFieldsAndMissingRecordsGiveClientErrors() throws Exception {
        expect(400,"POST","/api/guests",Map.of("passportNum","!!","firstName","Test","lastName","Guest","phoneNum","x"));
        var f=fixture("CLEANED");expect(400,"POST","/api/bookings",bookingBody(f,today,today));
        expect(400,"POST","/api/bookings",bookingBody(f,today.minusDays(1),today));
        expect(404,"GET","/api/bookings/9223372036854775807",null);expect(400,"GET","/api/bookings/not-a-number",null);
        expect(400,"PATCH","/api/rooms/"+f.room(),Map.of("cleaningState","UNKNOWN"));
        expect(400,"GET","/api/bookings/availability?roomId="+f.room()+"&checkInDate=bad&checkOutDate=bad",null);
    }
    @Test void archiveCopiesFieldsRevokesKeyAndMarksRoomDirty() throws Exception {
        var f=fixture("CLEANED");long id=booking(f);expect(409,"POST","/api/archive/bookings/"+id,null);
        expect(200,"POST","/api/bookings/"+id+"/check-in",null);var a=expect(200,"POST","/api/archive/bookings/"+id,null);
        assertEquals(f.guest(),a.get("guestId").asLong());assertEquals("CHECKEDIN",a.get("previousState").asString());
        assertEquals(0,sql.queryForObject("SELECT count(*) FROM keycards WHERE booking_id=? AND is_active",Integer.class,id));
        assertEquals("DIRTY",expect(200,"GET","/api/rooms/"+f.room(),null).get("cleaningState").asString());expect(409,"POST","/api/archive/bookings/"+id,null);
    }
    @Test void reviewRequiresVerifiedStayAndRatingIsValidated() throws Exception {
        long id=booking(fixture("CLEANED"));expect(409,"POST","/api/reviews",Map.of("bookingId",id,"rating",5,"comment","Good"));
        expect(200,"POST","/api/bookings/"+id+"/check-in",null);expect(200,"POST","/api/archive/bookings/"+id,null);
        expect(400,"POST","/api/reviews",Map.of("bookingId",id,"rating",6,"comment","Good"));expect(201,"POST","/api/reviews",Map.of("bookingId",id,"rating",5,"comment","Good"));
        expect(409,"POST","/api/reviews",Map.of("bookingId",id,"rating",4,"comment","Again"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,() -> sql.update("INSERT INTO reviews(booking_id,rating,comment) VALUES (?,?,?)",id,0,"Bad rating"));
    }
    @Test void keycardFailureRollsBackWholeCheckIn() throws Exception {
        long id=booking(fixture("CLEANED"));
        // A database-level injected failure proves state and issuance share one transaction.
        String name="stage0_fail_key_"+id;
        sql.execute("CREATE FUNCTION "+name+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.booking_id="+id+" THEN RAISE EXCEPTION 'Injected issuance failure'; END IF; RETURN NEW; END $$");
        sql.execute("CREATE TRIGGER "+name+" BEFORE INSERT ON keycards FOR EACH ROW EXECUTE FUNCTION "+name+"()");
        try { assertEquals(500,call("POST","/api/bookings/"+id+"/check-in",null).status()); }
        finally { sql.execute("DROP TRIGGER "+name+" ON keycards");sql.execute("DROP FUNCTION "+name+"()"); }
        assertEquals("Confirmed",sql.queryForObject("SELECT state::text FROM bookings WHERE booking_id=?",String.class,id));
        assertEquals(0,sql.queryForObject("SELECT count(*) FROM keycards WHERE booking_id=?",Integer.class,id));
    }
    @Test void pricingSnapshotSurvivesLaterRoomPriceChange() throws Exception {
        var f=fixture("CLEANED");long id=booking(f);expect(200,"PATCH","/api/rooms/"+f.room(),Map.of("cleaningState","CLEANED","pricePerNight",99999));
        assertEquals(31000,expect(200,"GET","/api/bookings/"+id+"/cost",null).get("total").decimalValue().intValue());
    }
    @Test void cacheEvictionWaitsForCommitAndRollbackKeepsData() throws Exception {
        var f=fixture("CLEANED");roomService.findAll();
        var tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> { roomService.update(f.room(),new com.hotelbooking.api.ApiDtos.RoomUpdate(com.hotelbooking.room.model.CleaningState.DIRTY,null)); status.setRollbackOnly(); });
        assertEquals("CLEANED",roomService.findAll().stream().filter(r -> r.roomId()==f.room()).findFirst().orElseThrow().cleaningState().name());
        assertEquals("Cleaned",sql.queryForObject("SELECT cleaning_state::text FROM rooms WHERE room_id=?",String.class,f.room()));
    }
}
