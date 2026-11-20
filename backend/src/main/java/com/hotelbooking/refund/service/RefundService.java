package com.hotelbooking.refund.service;

import com.hotelbooking.refund.db.RefundEntity;
import com.hotelbooking.refund.db.RefundRepository;
import com.hotelbooking.payment.service.PaymentService;
import com.hotelbooking.refund.model.RefundDto;
import com.hotelbooking.refund.model.RefundState;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class RefundService {

    private final RefundRepository refundRepository;
    private final PaymentService paymentService;

    public RefundService(RefundRepository refundRepository, PaymentService paymentService) {
        this.refundRepository = refundRepository;
        this.paymentService = paymentService;
    }

    @Cacheable("refunds")
    public List<RefundDto> findAll() {
        return refundRepository.findAll().stream().map(RefundDto::fromEntity).toList();
    }

    @Cacheable("refunds")
    public RefundDto findById(Long id) {
        return RefundDto.fromEntity(findRefundEntityById(id));
    }

    private RefundEntity findRefundEntityById(Long id) {
        return refundRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Refund not found: " + id));
    }

    /**
     * Сохраняет возврат; если он завершён (COMPLETED), в той же транзакции записывает его
     * в платёжный журнал (payments, статус REFUNDED), чтобы баланс брони учитывал возврат.
     */
    @Transactional
    @CacheEvict(value = "refunds", allEntries = true)
    public RefundDto create(RefundEntity refund) {
        RefundEntity saved = refundRepository.save(refund);
        if (saved.getState() == RefundState.COMPLETED) {
            paymentService.recordRefund(saved);
        }
        return RefundDto.fromEntity(saved);
    }

    @CacheEvict(value = "refunds", allEntries = true)
    public void delete(Long id) {
        if (!refundRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund not found: " + id);
        }
        refundRepository.deleteById(id);
    }
}