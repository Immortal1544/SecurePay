package com.securepay.dto.cart;

import java.math.BigDecimal;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CartResponse {

	private Long cartId;
	private Long userId;
	private List<CartItemResponse> items;
	private BigDecimal totalAmount;
}