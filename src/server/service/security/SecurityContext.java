package service.security;

import model.dto.auth.LoginResult;
import model.dto.auth.AuthenticationResult;
import service.exception.ServiceException;

/**
 * Manages the security context for the current executing thread.
 * This class holds the authenticated user's session (LoginResult) allowing
 * the business layer to authorize requests without requiring token parameters.
 */
public class SecurityContext {
    private static final ThreadLocal<AuthenticationResult> currentUser = new ThreadLocal<>();

    public static void set(AuthenticationResult login) {
        currentUser.set(login);
    }

    public static AuthenticationResult get() {
        return currentUser.get();
    }

    public static void clear() {
        currentUser.remove();
    }

    /**
     * Asserts that the current thread runs within an authenticated context.
     * @throws ServiceException if no valid session exists.
     */
    public static void assertAuthenticated() throws ServiceException {
        if (get() == null) {
            throw new ServiceException("Unauthorized: Missing or invalid authentication token.");
        }
    }
}
