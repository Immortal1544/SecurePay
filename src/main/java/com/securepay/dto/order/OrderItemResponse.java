package com.securepay.dto.order;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrderItemResponse {

	private Long id;
	private Long productId;
	private String productName;
	private BigDecimal unitPrice;
	private Integer quantity;
	private BigDecimal subtotal;
}