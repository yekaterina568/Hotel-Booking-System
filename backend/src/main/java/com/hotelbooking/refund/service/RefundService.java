package com.hotelbooking.refund.service;

import com.hotelbooking.refund.db.RefundEntity;
import com.hotelbooking.refund.db.RefundRepository;
import com.hotelbooking.refund.model.RefundDto;
import com.hotelbooking.refund.model.RefundState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@Slf4j
public class RefundService {

    private final RefundRepository refundRepository;
    private RefundPolicyService refundPolicyService;

    public RefundService(RefundRepository refundRepository) {
        this.refundRepository = refundRepository;
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
     * Set by Spring. Unit tests that create the service with {@code new RefundService(repository)}
     * work without it; then only the basic checks below run.
     */
    @Autowired(required = false)
    void setRefundPolicyService(RefundPolicyService refundPolicyService) {
        this.refundPolicyService = refundPolicyService;
    }

    /** Refund entered by hand. The amount must be positive and not more than the cancellation rules allow. */
    @Transactional
    @CacheEvict(value = "refunds", allEntries = true)
    public RefundDto create(RefundEntity refund) {
        if (refund.getBooking() == null || refund.getBooking().getBookingId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите бронь для возврата");
        }
        if (refund.getAmount() == null || refund.getAmount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сумма возврата должна быть больше нуля");
        }
        if (refundPolicyService != null) {
            refundPolicyService.validateManualRefund(refund);
        }
        if (refund.getState() == null) {
            refund.setState(RefundState.PENDING);
        }
        if (refund.getState() == RefundState.COMPLETED && refund.getProcessedAt() == null) {
            refund.setProcessedAt(OffsetDateTime.now());
        }
        RefundDto created = RefundDto.fromEntity(refundRepository.save(refund));
        log.info("Created refund id={}", created.refundId());
        return created;
    }

    @CacheEvict(value = "refunds", allEntries = true)
    public void delete(Long id) {
        if (!refundRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund not found: " + id);
        }
        refundRepository.deleteById(id);
        log.info("Deleted refund id={}", id);
    }
}