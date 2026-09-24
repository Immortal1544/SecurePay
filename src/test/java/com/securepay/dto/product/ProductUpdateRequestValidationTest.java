package com.securepay.dto.product;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class ProductUpdateRequestValidationTest {

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
	void rejectsBlankNameAndInvalidPriceStockAndMissingRequiredFields() {
		ProductUpdateRequest request = new ProductUpdateRequest();
		request.setName("  ");
		request.setPrice(new BigDecimal("0.00"));
		request.setStockQuantity(-1);

		Set<String> invalidFields = validator.validate(request).stream()
				.map(violation -> violation.getPropertyPath().toString())
				.collect(Collectors.toSet());

		assertTrue(invalidFields.containsAll(Set.of("name", "price", "stockQuantity", "active")));
	}

	@Test
	void rejectsNullRequiredFields() {
		ProductUpdateRequest request = new ProductUpdateRequest();

		Set<String> invalidFields = validator.validate(request).stream()
				.map(violation -> violation.getPropertyPath().toString())
				.collect(Collectors.toSet());

		assertTrue(invalidFields.containsAll(Set.of("name", "price", "stockQuantity", "active")));
	}

	@Test
	void acceptsValidUpdateRequest() {
		ProductUpdateRequest request = new ProductUpdateRequest();
		request.setName("Valid product");
		request.setPrice(new BigDecimal("0.01"));
		request.setStockQuantity(0);
		request.setActive(true);

		assertTrue(validator.validate(request).isEmpty());
	}
}
