package com.nexus.campus.security;

import com.nexus.campus.exception.BusinessException;

/**
 * The single definition of "is this an administrator".
 *
 * <p>The role string reaches the code from two places: the JWT filter's
 * {@code currentRole} request attribute in the controllers, and the user row's
 * {@code role} column in the services. Both used to spell the check out as
 * {@code "ADMIN".equals(...)} — nine copies, three slightly different refusal
 * shapes. Keeping the comparison and the 403 message here means a future role
 * change is one edit, not a search.</p>
 */
public final class AdminGuard {

    private AdminGuard() {
    }

    public static boolean isAdmin(String role) {
        return "ADMIN".equals(role);
    }

    /** Rejects the request with the same 403 every admin-only endpoint already returned. */
    public static void requireAdmin(String role) {
        if (!isAdmin(role)) {
            throw BusinessException.forbidden("Access denied. Admin privileges required.");
        }
    }
}
