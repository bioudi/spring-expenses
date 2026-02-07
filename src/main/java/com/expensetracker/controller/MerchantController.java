package com.expensetracker.controller;

import com.expensetracker.entity.MerchantCategory;
import com.expensetracker.service.CategorizationService;
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
        return ResponseEntity.ok(categorizationService.getAllMappings());
    }

    @PutMapping("/{id}")
    public ResponseEntity<MerchantCategory> updateMapping(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String category = body.get("category");
        if (category == null || category.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(categorizationService.updateMapping(id, category));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMapping(@PathVariable UUID id) {
        categorizationService.deleteMapping(id);
        return ResponseEntity.noContent().build();
    }
}
