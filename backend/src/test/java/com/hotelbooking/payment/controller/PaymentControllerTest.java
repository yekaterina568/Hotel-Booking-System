package com.hotelbooking.payment.controller;

import com.hotelbooking.payment.model.PaymentDto;
import com.hotelbooking.payment.model.PaymentOutcome;
import com.hotelbooking.payment.model.PaymentReceiptDto;
import com.hotelbooking.payment.model.PaymentReportDto;
import com.hotelbooking.payment.model.PaymentRequest;
import com.hotelbooking.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** REST-слой /api/payments: сервис замокан, сервер и БД не поднимаются. */
class PaymentControllerTest {

    private PaymentService paymentService;
    private MockMvc mockMvc;

    private final PaymentDto paid = new PaymentDto(5L, 1L, null, new BigDecimal("150.00"), "CARD", "PAID",
            "PAY-ABC", OffsetDateTime.parse("2026-11-20T14:00:00+05:00"));

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentController(paymentService)).build();
    }

    @Test
    @DisplayName("POST /api/payments: новый платёж — 201, ключ из заголовка Idempotency-Key уходит в сервис")
    void createReturns201() throws Exception {
        when(paymentService.create(any(PaymentRequest.class), eq("key-1"))).thenReturn(new PaymentOutcome(paid, false));

        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":1,\"method\":\"CARD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(5))
                .andExpect(jsonPath("$.transactionRef").value("PAY-ABC"));

        verify(paymentService).create(new PaymentRequest(1L, null, "CARD", null), "key-1");
    }

    @Test
    @DisplayName("POST /api/payments: повтор с тем же ключом — 200 и тот же платёж")
    void replayReturns200() throws Exception {
        when(paymentService.create(any(PaymentRequest.class), eq("key-1"))).thenReturn(new PaymentOutcome(paid, true));

        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":1,\"method\":\"CARD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(5));
    }

    @Test
    @DisplayName("POST /api/payments: переплата — 409 из сервиса")
    void overpaymentReturns409() throws Exception {
        when(paymentService.create(any(PaymentRequest.class), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "too much"));

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":1,\"amount\":999,\"method\":\"CASH\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /api/payments/{id}/receipt возвращает данные квитанции")
    void receipt() throws Exception {
        when(paymentService.receipt(5L)).thenReturn(new PaymentReceiptDto("RCP-000005", 5L, "PAY-ABC", "PAID",
                "CARD", new BigDecimal("150.00"), paid.paidAt(), 1L, "Aigerim Sadykova", 204, "DOUBLE",
                null, null, 3, new BigDecimal("50.00"), new BigDecimal("150.00"), new BigDecimal("0.00")));

        mockMvc.perform(get("/api/payments/5/receipt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptNumber").value("RCP-000005"))
                .andExpect(jsonPath("$.method").value("CARD"))
                .andExpect(jsonPath("$.transactionRef").value("PAY-ABC"))
                .andExpect(jsonPath("$.amount").value(150.00));
    }

    @Test
    @DisplayName("GET /api/payments/report?from&to передаёт даты в сервис и возвращает итоги")
    void report() throws Exception {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 30);
        when(paymentService.report(from, to)).thenReturn(new PaymentReportDto(from, to, 2,
                new BigDecimal("200.00"), 1, new BigDecimal("30.00"), new BigDecimal("170.00"), 2,
                List.of(new PaymentReportDto.MethodTotal("CARD", 1, new BigDecimal("150.00")))));

        mockMvc.perform(get("/api/payments/report").param("from", "2026-11-01").param("to", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netRevenue").value(170.00))
                .andExpect(jsonPath("$.refundsCount").value(1))
                .andExpect(jsonPath("$.byMethod[0].method").value("CARD"));
    }

    @Test
    @DisplayName("GET /api/payments/report без дат — период выбирает сервис")
    void reportWithoutDates() throws Exception {
        when(paymentService.report(null, null)).thenReturn(new PaymentReportDto(LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 27), 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, List.of()));

        mockMvc.perform(get("/api/payments/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentsCount").value(0));
    }
}
