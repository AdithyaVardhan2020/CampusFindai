package com.campusfind.service;

import com.campusfind.dto.RegisterRequest;
import com.campusfind.dto.UserResponse;
import com.campusfind.entity.Role;
import com.campusfind.entity.User;
import com.campusfind.exception.BadRequestException;
import com.campusfind.exception.ResourceNotFoundException;
import com.campusfind.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse registerUser(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BadRequestException("An account with email " + normalizedEmail + " already exists.");
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        // Security enforcement: Public registrations MUST ALWAYS be assigned ROLE_STUDENT.
        // Client-supplied roles are strictly ignored to prevent privilege escalation attacks.
        Role assignedRole = Role.ROLE_STUDENT;

        User user = new User(
                request.fullName().trim(),
                normalizedEmail,
                encodedPassword,
                assignedRole
        );

        User savedUser = userRepository.save(user);
        return UserResponse.fromEntity(savedUser);
    }

    /**
     * Internal secure administrator provisioning (used by environment bootstrap and admin seeding).
     * Not reachable via public registration endpoints.
     */
    public UserResponse createAdminUser(String fullName, String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BadRequestException("An account with email " + normalizedEmail + " already exists.");
        }
        String encodedPassword = passwordEncoder.encode(rawPassword);
        User user = new User(
                fullName.trim(),
                normalizedEmail,
                encodedPassword,
                Role.ROLE_ADMIN
        );
        User savedUser = userRepository.save(user);
        return UserResponse.fromEntity(savedUser);
    }

    /**
     * Checks if administrator exists by email, and provisions if absent.
     * Strictly insert-only: never overwrites or alters an existing user's credentials.
     */
    public UserResponse createAdminIfAbsent(String fullName, String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase();
        return userRepository.findByEmailIgnoreCase(normalizedEmail)
                .map(UserResponse::fromEntity)
                .orElseGet(() -> createAdminUser(fullName, normalizedEmail, rawPassword));
    }

    /**
     * Explicit, controlled administrator password reset.
     * Safely updates an existing administrator's password hash using BCrypt.
     * Strictly verifies that the account exists and holds ROLE_ADMIN to prevent modifying student accounts.
     */
    public UserResponse resetAdminPassword(String email, String newRawPassword) {
        String normalizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Administrator account not found with email: " + normalizedEmail));

        if (user.getRole() != Role.ROLE_ADMIN) {
            throw new BadRequestException("Cannot reset password via admin mechanism: user account does not have ROLE_ADMIN.");
        }

        user.setPasswordHash(passwordEncoder.encode(newRawPassword));
        User savedUser = userRepository.save(user);
        return UserResponse.fromEntity(savedUser);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        User user = getUserEntityByEmail(email);
        return UserResponse.fromEntity(user);
    }

    @Transactional(readOnly = true)
    public User getUserEntityByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserResponse::fromEntity)
                .toList();
    }

    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found with ID: " + id);
        }
        userRepository.deleteById(id);
    }
}
