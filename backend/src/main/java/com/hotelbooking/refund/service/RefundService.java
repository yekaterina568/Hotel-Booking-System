package com.hotelbooking.refund.service;

import com.hotelbooking.refund.db.RefundEntity;
import com.hotelbooking.refund.db.RefundRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class RefundService {

    private final RefundRepository refundRepository;

    public RefundService(RefundRepository refundRepository) {
        this.refundRepository = refundRepository;
    }

    public List<RefundEntity> findAll() {
        return refundRepository.findAll();
    }

    public RefundEntity findById(Long id) {
        return refundRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Refund not found: " + id));
    }

    public RefundEntity create(RefundEntity refund) {
        return refundRepository.save(refund);
    }

    public void delete(Long id) {
        if (!refundRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund not found: " + id);
        }
        refundRepository.deleteById(id);
    }
}