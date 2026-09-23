package com.securepay.dto.product;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductCreateRequest {

	@NotBlank(message = "Product name is required")
	private String name;

	private String description;

	@NotNull(message = "Price is required")
	@DecimalMin(value = "0.01", message = "Price must be greater than 0")
	private BigDecimal price;

	@NotNull(message = "Stock quantity is required")
	@Min(value = 0, message = "Stock quantity cannot be negative")
	private Integer stockQuantity;

	@NotNull(message = "Active status is required")
	private Boolean active;
}