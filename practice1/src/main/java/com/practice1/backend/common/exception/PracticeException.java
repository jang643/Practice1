package com.practice1.backend.common.exception;

import lombok.Getter;

@Getter
public abstract class PracticeException extends RuntimeException {

    public PracticeException(String message) { super(message);}

    public PracticeException(String message, Throwable cause) { super(message, cause); }

    public abstract int getStatusCode();

}
