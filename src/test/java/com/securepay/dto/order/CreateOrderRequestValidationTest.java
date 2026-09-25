package com.securepay.dto.order;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class CreateOrderRequestValidationTest {

	private static ValidatorFactory validatorFactory;
	private static Validator validator;

	@BeforeAll
	static void setUpValidator() {
		validatorFactory = Validation.buildDefaultValidatorFactory();
		validator = validatorFactory.getValidator();
	}

	@AfterAll
	static void closeValidator() {
		validatorFactory.close();
	}

	@Test
	void rejectsMissingRequiredDeliveryFields() {
		CreateOrderRequest request = new CreateOrderRequest();

		Set<String> invalidFields = validator.validate(request).stream()
				.map(violation -> violation.getPropertyPath().toString())
				.collect(Collectors.toSet());

		assertTrue(invalidFields.containsAll(Set.of(
				"recipientName", "phoneNumber", "addressLine1", "city", "state", "postalCode", "country")));
		assertTrue(!invalidFields.contains("addressLine2"));
	}

	@Test
	void rejectsPhoneAndPostalCodesThatDoNotMatchSupportedFormats() {
		CreateOrderRequest request = validRequest();
		request.setPhoneNumber("call-me");
		request.setPostalCode("@@");

		Set<String> invalidFields = validator.validate(request).stream()
				.map(violation -> violation.getPropertyPath().toString())
				.collect(Collectors.toSet());

		assertTrue(invalidFields.containsAll(Set.of("phoneNumber", "postalCode")));
	}

	@Test
	void acceptsValidDeliveryDetailsWithoutOptionalAddressLine2() {
		CreateOrderRequest request = validRequest();

		assertTrue(validator.validate(request).isEmpty());
	}

	private CreateOrderRequest validRequest() {
		CreateOrderRequest request = new CreateOrderRequest();
		request.setRecipientName("Asha Recipient");
		request.setPhoneNumber("+919876543210");
		request.setAddressLine1("12 SecurePay Street");
		request.setCity("Bengaluru");
		request.setState("Karnataka");
		request.setPostalCode("560001");
		request.setCountry("India");
		return request;
	}
}
