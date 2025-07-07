package com.practice1.backend.account.exception;

import com.practice1.backend.common.exception.PracticeException;

public class AccountNotAvailableException extends PracticeException {
    private static final String MESSAGE = "Account not available";
    public AccountNotAvailableException() { super(MESSAGE);  }

    @Override
    public int getStatusCode() { return 404;}
}
