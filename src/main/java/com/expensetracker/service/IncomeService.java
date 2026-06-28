package com.expensetracker.service;

import com.expensetracker.dto.IncomeRequest;
import com.expensetracker.dto.IncomeResponse;
import com.expensetracker.entity.Income;
import com.expensetracker.entity.User;
import com.expensetracker.exception.IncomeNotFoundException;
import com.expensetracker.repository.AccountRepository;
import com.expensetracker.repository.IncomeRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncomeService {

    private final IncomeRepository incomeRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final EntityManager entityManager;

    @Transactional
    public IncomeResponse createIncome(IncomeRequest request, UUID userId) {
        validateAccountIfProvided(request.getAccountId(), userId);

        User userRef = entityManager.getReference(User.class, userId);

        Income income = Income.builder()
                .name(request.getName())
                .type(request.getType())
                .category(request.getCategory())
                .amount(request.getAmount())
                .accountId(request.getAccountId())
                .user(userRef)
                .timestamp(resolveTimestamp(request))
                .notes(request.getNotes())
                .build();

        Income saved = incomeRepository.save(income);

        // Auto-add amount to account balance if accountId is provided
        if (request.getAccountId() != null) {
            accountService.adjustBalance(request.getAccountId(), request.getAmount());
        }

        log.info("Income created: id={}, name='{}', amount={}, accountId={}",
                saved.getId(), saved.getName(), saved.getAmount(), saved.getAccountId());
        return IncomeResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<IncomeResponse> getIncomes(UUID userId) {
        return incomeRepository.findByUserIdOrderByTimestampDesc(userId)
                .stream()
                .map(IncomeResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public IncomeResponse getIncomeById(UUID id, UUID userId) {
        Income income = incomeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IncomeNotFoundException(id));
        return IncomeResponse.fromEntity(income);
    }

    @Transactional
    public IncomeResponse updateIncome(UUID id, IncomeRequest request, UUID userId) {
        Income income = incomeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IncomeNotFoundException(id));

        validateAccountIfProvided(request.getAccountId(), userId);

        // Reverse old account balance adjustment before updating
        if (income.getAccountId() != null) {
            accountService.adjustBalance(income.getAccountId(), income.getAmount().negate());
        }

        income.setName(request.getName());
        income.setType(request.getType());
        income.setCategory(request.getCategory());
        income.setAmount(request.getAmount());
        income.setAccountId(request.getAccountId());
        income.setTimestamp(resolveTimestamp(request));
        if (request.getNotes() != null) {
            income.setNotes(request.getNotes());
        }

        Income saved = incomeRepository.save(income);

        // Apply new account balance adjustment
        if (request.getAccountId() != null) {
            accountService.adjustBalance(request.getAccountId(), request.getAmount());
        }

        log.info("Income updated: id={}, name='{}', amount={}, accountId={}",
                saved.getId(), saved.getName(), saved.getAmount(), saved.getAccountId());
        return IncomeResponse.fromEntity(saved);
    }

    @Transactional
    public IncomeResponse deleteIncome(UUID id, UUID userId) {
        Income income = incomeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IncomeNotFoundException(id));

        // Reverse account balance adjustment
        if (income.getAccountId() != null) {
            accountService.adjustBalance(income.getAccountId(), income.getAmount().negate());
        }

        incomeRepository.delete(income);
        log.info("Income deleted: id={}, name='{}', amount={}", id, income.getName(), income.getAmount());
        return IncomeResponse.fromEntity(income);
    }

    private void validateAccountIfProvided(UUID accountId, UUID userId) {
        if (accountId == null) {
            return;
        }
        accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account not found with id: " + accountId
                ));
    }

    private LocalDateTime resolveTimestamp(IncomeRequest request) {
        return request.getTimestamp() != null ? request.getTimestamp() : LocalDateTime.now();
    }
}
