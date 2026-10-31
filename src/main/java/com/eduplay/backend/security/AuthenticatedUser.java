package com.eduplay.backend.security;

import com.eduplay.backend.user.domain.Role;
import java.util.UUID;

/** Principal placed in the SecurityContext for a request carrying a valid access token. */
public record AuthenticatedUser(UUID id, Role role) {
}
