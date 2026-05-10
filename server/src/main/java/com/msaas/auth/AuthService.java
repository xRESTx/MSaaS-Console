package com.msaas.auth;

import com.msaas.auth.AuthController.AuthResponse;
import com.msaas.auth.AuthController.LoginRequest;
import com.msaas.auth.AuthController.LookupRequest;
import com.msaas.auth.AuthController.LookupResponse;
import com.msaas.auth.AuthController.RegisterRequest;
import com.msaas.auth.AuthController.UserView;
import com.msaas.common.ApiException;
import com.msaas.config.AppProperties;
import com.msaas.security.JwtService;
import com.msaas.user.AppUser;
import com.msaas.user.SystemRole;
import com.msaas.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties properties;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, AppProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Email is already registered");
        }
        String username = issueUsername(request.username(), email);
        AppUser user = new AppUser(email, passwordEncoder.encode(request.password()), username);
        if (bootstrapAdminEmail().equals(email)) {
            user.setSystemRole(SystemRole.ADMIN);
        }
        user = userRepository.save(user);
        return response(user);
    }

    public AuthResponse login(LoginRequest request) {
        String identifier = loginIdentifier(request);
        AppUser user = findByIdentifier(identifier)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (user.isDisabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "User is disabled");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        user = ensureBootstrapAdmin(user);
        user = ensureUsername(user);
        return response(user);
    }

    public LookupResponse lookup(LookupRequest request) {
        String identifier = request.identifier().trim();
        return findByIdentifier(identifier)
                .map(this::ensureUsername)
                .map(user -> new LookupResponse(true, identifier, user.getUsername()))
                .orElseGet(() -> new LookupResponse(false, identifier, null));
    }

    private AuthResponse response(AppUser user) {
        return new AuthResponse(
                jwtService.createToken(user.getId(), user.getEmail(), user.getUsername(), user.getSystemRole()),
                new UserView(user.getId(), user.getEmail(), user.getUsername(), user.getSystemRole(), user.isDisabled())
        );
    }

    private AppUser ensureBootstrapAdmin(AppUser user) {
        if (bootstrapAdminEmail().equals(user.getEmail()) && user.getSystemRole() != SystemRole.ADMIN) {
            user.setSystemRole(SystemRole.ADMIN);
            return userRepository.save(user);
        }
        return user;
    }

    private AppUser ensureUsername(AppUser user) {
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            user.setUsername(issueUsername(null, user.getEmail()));
            return userRepository.save(user);
        }
        return user;
    }

    private java.util.Optional<AppUser> findByIdentifier(String identifier) {
        if (identifier.contains("@")) {
            return userRepository.findByEmail(normalizeEmail(identifier));
        }
        return userRepository.findByUsername(normalizeUsername(identifier));
    }

    private String loginIdentifier(LoginRequest request) {
        String identifier = request.identifier();
        if (identifier == null || identifier.isBlank()) {
            identifier = request.username();
        }
        if (identifier == null || identifier.isBlank()) {
            identifier = request.email();
        }
        if (identifier == null || identifier.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return identifier.trim();
    }

    private String issueUsername(String requestedUsername, String email) {
        String base = requestedUsername == null || requestedUsername.isBlank()
                ? usernameBaseFromEmail(email)
                : normalizeUsername(requestedUsername);
        if (base.length() < 3) {
            base = (base + "user").substring(0, 4);
        }
        if (base.length() > 28) {
            base = base.substring(0, 28);
        }
        if (requestedUsername != null && !requestedUsername.isBlank()) {
            if (userRepository.existsByUsername(base)) {
                throw ApiException.conflict("Username is already taken");
            }
            return base;
        }
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            String tail = "-" + suffix++;
            String prefix = base.length() + tail.length() > 32 ? base.substring(0, 32 - tail.length()) : base;
            candidate = prefix + tail;
        }
        return candidate;
    }

    private String usernameBaseFromEmail(String email) {
        String local = email == null ? "user" : email.split("@", 2)[0];
        String normalized = local.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "-");
        normalized = normalized.replaceAll("^[._-]+|[._-]+$", "");
        return normalized.isBlank() ? "user" : normalized;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String bootstrapAdminEmail() {
        String email = properties.getAdmin().getBootstrapEmail();
        return email == null ? "" : normalizeEmail(email);
    }
}
