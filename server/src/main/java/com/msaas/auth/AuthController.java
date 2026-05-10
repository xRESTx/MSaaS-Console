package com.msaas.auth;

import com.msaas.user.SystemRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/lookup")
    public LookupResponse lookup(@Valid @RequestBody LookupRequest request) {
        return authService.lookup(request);
    }

    public record LookupRequest(
            @NotBlank String identifier
    ) {
    }

    public record LookupResponse(boolean exists, String identifier, String username) {
    }

    public record RegisterRequest(
            @Email @NotBlank String email,
            @Size(min = 3, max = 32)
            @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "Username can contain letters, numbers, dot, dash, and underscore")
            String username,
            @NotBlank @Size(min = 6, max = 128) String password
    ) {
    }

    public record LoginRequest(
            String identifier,
            String email,
            String username,
            @NotBlank @Size(min = 6, max = 128) String password
    ) {
    }

    public record AuthResponse(String token, UserView user) {
    }

    public record UserView(String id, String email, String username, SystemRole systemRole, boolean disabled) {
    }
}
