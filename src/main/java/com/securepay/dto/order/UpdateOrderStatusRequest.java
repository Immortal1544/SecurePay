package com.securepay.dto.order;

import com.securepay.entity.OrderStatus;

import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateOrderStatusRequest {

	@NotNull(message = "Order status is required")
	private OrderStatus status;
}
