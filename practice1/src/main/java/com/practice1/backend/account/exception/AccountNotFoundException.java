package com.practice1.backend.account.exception;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(Long accountId) {
        super("Account not found. id = " + accountId);
    }
}
