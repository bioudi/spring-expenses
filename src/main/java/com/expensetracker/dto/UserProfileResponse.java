package com.expensetracker.dto;

import com.expensetracker.entity.User;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileResponse {

    private String email;
    private String displayName;
    private String apiKey;
    private LocalDateTime createdAt;

    public static UserProfileResponse fromEntity(User user) {
        return UserProfileResponse.builder()
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .apiKey(user.getApiKey())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
