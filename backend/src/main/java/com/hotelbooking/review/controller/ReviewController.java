package com.hotelbooking.review.controller;
import static com.hotelbooking.api.ApiDtos.*;
import com.hotelbooking.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/reviews") @RequiredArgsConstructor
public class ReviewController {
    private final ReviewService service;

    @GetMapping public List<ReviewResponse> getAll() { return service.findAll(); }
    @GetMapping("/{id}") public ReviewResponse getById(@PathVariable Long id) { return service.findById(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ReviewResponse create(@Valid @RequestBody ReviewRequest request) { return service.create(request); }

}
