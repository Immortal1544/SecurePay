package com.securepay.dto.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AddToCartRequest {

	@NotNull
	private Long productId;

	@NotNull
	@Min(value = 1, message = "Quantity must be at least 1")
	private Integer quantity;
}