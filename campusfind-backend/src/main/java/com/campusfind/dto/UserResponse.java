package com.campusfind.dto;

import com.campusfind.entity.Role;
import com.campusfind.entity.User;

import java.time.Instant;

/**
 * Public user representation. Never exposes passwords, salt, or password hashes.
 */
public record UserResponse(
        Long id,
        String fullName,
        String email,
        Role role,
        Instant createdAt
) {
    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
