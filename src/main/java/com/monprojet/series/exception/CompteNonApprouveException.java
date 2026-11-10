// CompteNonApprouveException.java
package com.monprojet.series.exception;

// Valid credentials but the account is pending or refused: mapped to HTTP 403
public class CompteNonApprouveException extends RuntimeException {
    public CompteNonApprouveException(String message) {
        super(message);
    }
}
