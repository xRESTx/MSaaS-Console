package com.msaas.security;

import com.msaas.user.SystemRole;

public record AuthenticatedUser(String id, String email, String username, SystemRole systemRole) {
    public boolean admin() {
        return systemRole == SystemRole.ADMIN;
    }
}
