package com.practice1.backend.account.exception;

public class AccountNotAvailableException extends RuntimeException{
    public AccountNotAvailableException() {
        super("this account is not available");
    }
}
