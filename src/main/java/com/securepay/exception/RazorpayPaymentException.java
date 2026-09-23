package com.securepay.exception;

public class RazorpayPaymentException extends RuntimeException {

	public RazorpayPaymentException(String message) {
		super(message);
	}

	public RazorpayPaymentException(String message, Throwable cause) {
		super(message, cause);
	}
}