package com.securepay.dto.payment;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RazorpayOrderResponse {

	private Long paymentId;
	private Long orderId;
	private BigDecimal amount;
	private String currency;
	private String razorpayOrderId;
	private String razorpayKeyId;
}