package com.hotelbooking.review.controller;

import com.hotelbooking.review.model.ReviewDto;
import com.hotelbooking.review.model.ReviewRequest;
import com.hotelbooking.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/reviews")
public class ReviewAdminController {
    private final ReviewService reviews;

    public ReviewAdminController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    public List<ReviewDto> findAll() {
        return reviews.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewDto create(@RequestBody @Valid ReviewRequest request) {
        return reviews.create(request);
    }

    @PutMapping("/{id}/status")
    public ReviewDto changeStatus(@PathVariable Long id, @RequestBody @Valid ReviewRequest.StatusUpdate request) {
        return reviews.changeStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        reviews.delete(id);
    }
}
