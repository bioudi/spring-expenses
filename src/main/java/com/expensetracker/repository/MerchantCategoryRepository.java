package com.expensetracker.repository;

import com.expensetracker.entity.MerchantCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantCategoryRepository extends JpaRepository<MerchantCategory, UUID> {

    Optional<MerchantCategory> findByUserIdAndMerchantKey(UUID userId, String merchantKey);

    List<MerchantCategory> findAllByUserId(UUID userId);

    Optional<MerchantCategory> findByIdAndUserId(UUID id, UUID userId);
}
