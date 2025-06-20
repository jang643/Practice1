package com.practice1.backend.account_auth.exception;

public class AuthException extends RuntimeException {

    public AuthException(Integer failCount) {
        super("Account Failed to validation, count =  " + failCount);
    }
}
