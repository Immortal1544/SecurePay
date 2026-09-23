package com.securepay.dto.cart;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CartItemResponse {

	private Long id;
	private Long productId;
	private String productName;
	private BigDecimal price;
	private Integer quantity;
	private BigDecimal subtotal;
}