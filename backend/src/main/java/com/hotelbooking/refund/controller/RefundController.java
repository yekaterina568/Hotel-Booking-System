package com.hotelbooking.refund.controller;
import static com.hotelbooking.api.ApiDtos.*;
import com.hotelbooking.refund.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/refunds") @RequiredArgsConstructor
public class RefundController {
    private final RefundService service;

    @GetMapping public List<RefundResponse> getAll() { return service.findAll(); }
    @GetMapping("/{id}") public RefundResponse getById(@PathVariable Long id) { return service.findById(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public RefundResponse create(@Valid @RequestBody RefundRequest request) { return service.create(request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { service.delete(id); }

}
