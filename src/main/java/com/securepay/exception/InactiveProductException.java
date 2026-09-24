package com.securepay.exception;

public class InactiveProductException extends RuntimeException {

	public InactiveProductException() {
		super("Inactive products cannot be added to cart or purchased");
	}
}
