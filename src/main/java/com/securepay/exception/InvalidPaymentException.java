package com.securepay.exception;

public class InvalidPaymentException extends RuntimeException {

	public InvalidPaymentException(String message) {
		super(message);
	}

	public InvalidPaymentException(String message, Throwable cause) {
		super(message, cause);
	}
}