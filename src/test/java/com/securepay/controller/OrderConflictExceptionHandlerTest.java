package com.securepay.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.securepay.exception.ErrorResponse;
import com.securepay.exception.GlobalExceptionHandler;
import com.securepay.exception.OrderConflictException;

class OrderConflictExceptionHandlerTest {

	@Test
	void orderConflictsReturnSanitizedHttp409Response() {
		ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler()
				.handleOrderConflict(new OrderConflictException("Insufficient stock"));

		assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
		assertEquals(409, response.getBody().getStatus());
		assertEquals("Insufficient stock", response.getBody().getMessage());
	}
}
