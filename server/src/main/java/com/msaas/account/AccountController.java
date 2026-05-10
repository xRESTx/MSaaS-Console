package com.msaas.account;

import com.msaas.auth.AuthController.AuthResponse;
import com.msaas.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PatchMapping("/username")
    public AuthResponse updateUsername(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody UpdateUsernameRequest request) {
        return accountService.updateUsername(user.id(), request);
    }

    @PostMapping("/password")
    public AuthResponse updatePassword(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody UpdatePasswordRequest request) {
        return accountService.updatePassword(user.id(), request);
    }

    public record UpdateUsernameRequest(
            @NotBlank
            @Size(min = 3, max = 32)
            @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "Username can contain letters, numbers, dot, dash, and underscore")
            String username
    ) {
    }

    public record UpdatePasswordRequest(
            @NotBlank @Size(min = 6, max = 128) String currentPassword,
            @NotBlank @Size(min = 6, max = 128) String newPassword
    ) {
    }
}
