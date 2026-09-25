package com.securepay.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateOrderRequest {

	@NotBlank(message = "Recipient name is required")
	@Size(max = 120, message = "Recipient name must not exceed 120 characters")
	private String recipientName;

	@NotBlank(message = "Phone number is required")
	@Pattern(regexp = "^\\+?[1-9][0-9]{7,14}$", message = "Phone number must contain 8 to 15 digits and may start with +")
	private String phoneNumber;

	@NotBlank(message = "Address line 1 is required")
	@Size(max = 255, message = "Address line 1 must not exceed 255 characters")
	private String addressLine1;

	@Size(max = 255, message = "Address line 2 must not exceed 255 characters")
	private String addressLine2;

	@NotBlank(message = "City is required")
	@Size(max = 100, message = "City must not exceed 100 characters")
	private String city;

	@NotBlank(message = "State is required")
	@Size(max = 100, message = "State must not exceed 100 characters")
	private String state;

	@NotBlank(message = "Postal code is required")
	@Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 -]{1,10}[A-Za-z0-9]$", message = "Postal code must be 3 to 12 characters using letters, digits, spaces, or hyphens")
	private String postalCode;

	@NotBlank(message = "Country is required")
	@Size(max = 100, message = "Country must not exceed 100 characters")
	private String country;
}
