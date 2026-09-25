package com.securepay.dto.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.securepay.entity.OrderStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrderResponse {

	private Long orderId;
	private BigDecimal totalAmount;
	private OrderStatus status;
	private LocalDateTime createdAt;
	private String recipientName;
	private String phoneNumber;
	private String addressLine1;
	private String addressLine2;
	private String city;
	private String state;
	private String postalCode;
	private String country;
	private List<OrderItemResponse> items;
}
