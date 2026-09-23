package com.securepay.dto.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.securepay.entity.PaymentStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PaymentResponse {

	private Long paymentId;
	private Long orderId;
	private BigDecimal amount;
	private PaymentStatus status;
	private String razorpayOrderId;
	private String razorpayPaymentId;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}