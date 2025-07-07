package com.practice1.backend.account_auth.exception;

import com.practice1.backend.common.exception.PracticeException;

public class AuthException extends PracticeException {
    private static final String MESSAGE = "Account Failed to validation, count =  ";
    public AuthException(Integer failCount) {
        super(MESSAGE + failCount);
    }

    @Override
    public int getStatusCode() { return 401;}
}
