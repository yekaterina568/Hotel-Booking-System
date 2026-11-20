package com.hotelbooking.refund.service;

import com.hotelbooking.refund.db.RefundEntity;
import com.hotelbooking.refund.db.RefundRepository;
import com.hotelbooking.refund.model.RefundDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Slf4j
public class RefundService {

    private final RefundRepository refundRepository;

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

    @CacheEvict(value = "refunds", allEntries = true)
    public RefundDto create(RefundEntity refund) {
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