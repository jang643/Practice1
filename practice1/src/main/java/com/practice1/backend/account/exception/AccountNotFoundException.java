package com.practice1.backend.account.exception;

import com.practice1.backend.common.exception.PracticeException;

public class AccountNotFoundException extends PracticeException {
    private static final String MESSAGE = "Account not found. id = ";
    public AccountNotFoundException(Long accountId) {
        super( MESSAGE+ accountId);
    }

    @Override
    public int getStatusCode() { return 401;}
}
