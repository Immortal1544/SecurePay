package com.securepay.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.securepay.dto.payment.CreatePaymentResponse;
import com.securepay.dto.payment.PaymentResponse;
import com.securepay.dto.payment.RazorpayOrderResponse;
import com.securepay.dto.payment.VerifyPaymentRequest;
import com.securepay.service.PaymentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

	private final PaymentService paymentService;

	public PaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@PostMapping("/webhook/razorpay")
	public ResponseEntity<Void> handleRazorpayWebhook(
			@RequestBody String rawPayload,
			@RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
		paymentService.processRazorpayWebhook(rawPayload, signature);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/orders/{orderId}")
	public ResponseEntity<CreatePaymentResponse> createPayment(@PathVariable Long orderId) {
		return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createPayment(orderId));
	}

	@PostMapping("/orders/{orderId}/razorpay")
	public ResponseEntity<RazorpayOrderResponse> createRazorpayOrder(@PathVariable Long orderId) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(paymentService.createRazorpayOrder(orderId));
	}

	@PostMapping("/orders/{orderId}/verify")
	public ResponseEntity<PaymentResponse> verifyPayment(
			@PathVariable Long orderId,
			@Valid @org.springframework.web.bind.annotation.RequestBody VerifyPaymentRequest request) {
		return ResponseEntity.ok(paymentService.verifyPayment(orderId, request));
	}

	@GetMapping("/orders/{orderId}")
	public ResponseEntity<PaymentResponse> getPaymentForOrder(@PathVariable Long orderId) {
		return ResponseEntity.ok(paymentService.getPaymentForOrder(orderId));
	}

	@GetMapping
	public ResponseEntity<List<PaymentResponse>> getCurrentUserPayments() {
		return ResponseEntity.ok(paymentService.getCurrentUserPayments());
	}
}