package com.expensetracker.dto;

import com.expensetracker.entity.Account;
import com.expensetracker.entity.AccountType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response payload for {@code POST /api/transfers}. Surfaces both account
 * snapshots (id, name, type, post-transfer balance) so the success card can
 * render "from $X → to $Y" without a follow-up GET.
 *
 * <p>{@link #fromEntity(Account)} lets the service build the snapshots from
 * the same JPA-managed entity it just updated.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferResponse {

    private UUID transferId;
    private AccountSnapshot fromAccount;
    private AccountSnapshot toAccount;
    private BigDecimal amount;
    private String description;
    private LocalDateTime timestamp;

    /**
     * Lightweight view of an account row — id, name, type, and the balance
     * after the transfer landed.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AccountSnapshot {
        private UUID id;
        private String name;
        private AccountType type;
        private BigDecimal balance;

        public static AccountSnapshot fromEntity(Account account) {
            return AccountSnapshot.builder()
                    .id(account.getId())
                    .name(account.getName())
                    .type(account.getType())
                    .balance(account.getBalance())
                    .build();
        }
    }
}