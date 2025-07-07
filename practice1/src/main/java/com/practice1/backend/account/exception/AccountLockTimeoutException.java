package com.practice1.backend.account.exception;

import com.practice1.backend.common.exception.PracticeException;

public class AccountLockTimeoutException extends PracticeException {
    private static final String MESSAGE = "Transaction failed due to database lock timeout";

    public AccountLockTimeoutException() {
        super(MESSAGE);
    }

    @Override
    public int getStatusCode() {
        return 409;
    }
}
