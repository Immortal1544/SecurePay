package com.securepay.dto.payment;

import java.math.BigDecimal;

import com.securepay.entity.PaymentStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreatePaymentResponse {

	private Long paymentId;
	private Long orderId;
	private BigDecimal amount;
	private PaymentStatus status;
	private String razorpayOrderId;
}