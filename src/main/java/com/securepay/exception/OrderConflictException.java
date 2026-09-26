package com.securepay.exception;

public class OrderConflictException extends RuntimeException {

	public OrderConflictException(String message) {
		super(message);
	}
}
