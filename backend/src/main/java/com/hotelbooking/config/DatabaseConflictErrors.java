package com.hotelbooking.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns database constraint errors into 409 Conflict with a readable message instead of 500.
 * The main case is the exclusion constraint that forbids double bookings: it fires when two requests
 * for the same room and dates get past the application check at the same moment.
 */
@Slf4j
@RestControllerAdvice
public class DatabaseConflictErrors {

    static final String NO_OVERLAP_CONSTRAINT = "excl_bookings_room_no_overlap";
    static final String UNIQUE_PASSPORT_CONSTRAINT = "guests_passport_num_key";

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handle(DataIntegrityViolationException exception) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(exception);
        String text = cause.getMessage() == null ? "" : cause.getMessage();
        String detail;
        if (text.contains(NO_OVERLAP_CONSTRAINT)) {
            detail = "Номер уже занят на эти даты";
        } else if (text.contains(UNIQUE_PASSPORT_CONSTRAINT)) {
            detail = "Гость с таким паспортом уже зарегистрирован";
        } else {
            detail = "Операция нарушает ограничения базы данных";
        }
        log.warn("Database constraint violation: {}", text);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail));
    }
}
