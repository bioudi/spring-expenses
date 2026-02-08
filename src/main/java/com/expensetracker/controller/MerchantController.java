package com.expensetracker.controller;

import com.expensetracker.entity.MerchantCategory;
import com.expensetracker.service.CategorizationService;
import com.expensetracker.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final CategorizationService categorizationService;

    @GetMapping
    public ResponseEntity<List<MerchantCategory>> getAllMappings() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(categorizationService.getAllMappings(userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MerchantCategory> updateMapping(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String category = body.get("category");
        if (category == null || category.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(categorizationService.updateMapping(id, category, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMapping(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        categorizationService.deleteMapping(id, userId);
        return ResponseEntity.noContent().build();
    }
}
