package com.msaas.account;

import com.msaas.account.AccountController.UpdatePasswordRequest;
import com.msaas.account.AccountController.UpdateUsernameRequest;
import com.msaas.auth.AuthController.AuthResponse;
import com.msaas.auth.AuthController.UserView;
import com.msaas.common.ApiException;
import com.msaas.security.JwtService;
import com.msaas.user.AppUser;
import com.msaas.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AccountService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse updateUsername(String userId, UpdateUsernameRequest request) {
        AppUser user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        String username = normalizeUsername(request.username());
        if (!username.equals(user.getUsername()) && userRepository.existsByUsername(username)) {
            throw ApiException.conflict("Username is already taken");
        }
        user.setUsername(username);
        return response(userRepository.save(user));
    }

    public AuthResponse updatePassword(String userId, UpdatePasswordRequest request) {
        AppUser user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        return response(userRepository.save(user));
    }

    private AuthResponse response(AppUser user) {
        return new AuthResponse(
                jwtService.createToken(user.getId(), user.getEmail(), user.getUsername(), user.getSystemRole()),
                new UserView(user.getId(), user.getEmail(), user.getUsername(), user.getSystemRole(), user.isDisabled())
        );
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
