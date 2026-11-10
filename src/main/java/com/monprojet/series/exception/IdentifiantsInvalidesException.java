// IdentifiantsInvalidesException.java
package com.monprojet.series.exception;

// Wrong email or password: mapped to HTTP 401
public class IdentifiantsInvalidesException extends RuntimeException {
    public IdentifiantsInvalidesException() {
        super("Email ou mot de passe incorrect.");
    }
}
