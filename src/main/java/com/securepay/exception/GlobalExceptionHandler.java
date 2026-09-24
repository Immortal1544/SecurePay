package com.securepay.exception;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(InvalidWebhookException.class)
	public ResponseEntity<ErrorResponse> handleInvalidWebhook(InvalidWebhookException exception) {
		ErrorResponse errorResponse = new ErrorResponse(
				LocalDateTime.now(),
				HttpStatus.BAD_REQUEST.value(),
				"Bad Request",
				"Invalid Razorpay webhook request");

		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
	}

	@ExceptionHandler(InvalidPaymentException.class)
	public ResponseEntity<ErrorResponse> handleInvalidPayment(InvalidPaymentException exception) {
		ErrorResponse errorResponse = new ErrorResponse(
				LocalDateTime.now(),
				HttpStatus.BAD_REQUEST.value(),
				"Bad Request",
				"Invalid payment verification details");

		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
	}

	@ExceptionHandler(RazorpayPaymentException.class)
	public ResponseEntity<ErrorResponse> handleRazorpayPayment(RazorpayPaymentException exception) {
		ErrorResponse errorResponse = new ErrorResponse(
				LocalDateTime.now(),
				HttpStatus.BAD_GATEWAY.value(),
				"Bad Gateway",
				"Unable to communicate with Razorpay");

		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleResourceNotFound(
			ResourceNotFoundException exception) {
		ErrorResponse errorResponse = new ErrorResponse(
				LocalDateTime.now(),
				HttpStatus.NOT_FOUND.value(),
				"Not Found",
				exception.getMessage());

		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
	}

	@ExceptionHandler(InactiveProductException.class)
	public ResponseEntity<ErrorResponse> handleInactiveProduct(InactiveProductException exception) {
		ErrorResponse errorResponse = new ErrorResponse(
				LocalDateTime.now(),
				HttpStatus.CONFLICT.value(),
				"Conflict",
				exception.getMessage());

		return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
	}

	@ExceptionHandler(InvalidOrderStatusTransitionException.class)
	public ResponseEntity<ErrorResponse> handleInvalidOrderStatusTransition(
			InvalidOrderStatusTransitionException exception) {
		ErrorResponse errorResponse = new ErrorResponse(
				LocalDateTime.now(),
				HttpStatus.CONFLICT.value(),
				"Conflict",
				exception.getMessage());

		return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
	}

	@ExceptionHandler(ResourceAlreadyExistsException.class)
	public ResponseEntity<ErrorResponse> handleResourceAlreadyExists(
			ResourceAlreadyExistsException exception) {
		ErrorResponse errorResponse = new ErrorResponse(
				LocalDateTime.now(),
				HttpStatus.CONFLICT.value(),
				"Conflict",
				exception.getMessage());

		return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
			MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult()
				.getFieldErrors()
				.stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.collect(Collectors.joining("; "));

		ErrorResponse errorResponse = new ErrorResponse(
				LocalDateTime.now(),
				HttpStatus.BAD_REQUEST.value(),
				"Bad Request",
				message);

		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
	}
}
