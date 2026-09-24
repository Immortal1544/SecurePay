package com.securepay.exception;

public class InvalidOrderStatusTransitionException extends RuntimeException {

	public InvalidOrderStatusTransitionException() {
		super("Order status transition is not allowed");
	}
}
