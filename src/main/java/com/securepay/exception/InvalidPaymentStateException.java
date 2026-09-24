package com.securepay.exception;

public class InvalidPaymentStateException extends RuntimeException {

	public InvalidPaymentStateException() {
		super("Payment is not allowed for the current order status");
	}
}
