package com.securepay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.securepay.dto.product.ProductUpdateRequest;
import com.securepay.entity.Product;
import com.securepay.exception.ResourceNotFoundException;
import com.securepay.repository.ProductRepository;

class ProductServiceTest {

	private ProductRepository productRepository;
	private ProductService productService;

	@BeforeEach
	void setUp() {
		productRepository = mock(ProductRepository.class);
		productService = new ProductService(productRepository);
	}

	@Test
	void updateProductUpdatesAndReturnsProduct() {
		Product product = product(7L, true);
		when(productRepository.findById(7L)).thenReturn(Optional.of(product));
		when(productRepository.save(product)).thenReturn(product);
		ProductUpdateRequest request = updateRequest();

		var response = productService.updateProduct(7L, request);

		assertEquals(7L, response.getId());
		assertEquals("Updated name", response.getName());
		assertEquals("Updated description", response.getDescription());
		assertEquals(new BigDecimal("24.50"), response.getPrice());
		assertEquals(12, response.getStockQuantity());
		assertEquals(false, response.getActive());
		verify(productRepository).save(product);
	}

	@Test
	void updateProductForMissingProductThrowsNotFound() {
		when(productRepository.findById(99L)).thenReturn(Optional.empty());

		ResourceNotFoundException exception = assertThrows(
				ResourceNotFoundException.class,
				() -> productService.updateProduct(99L, updateRequest()));

		assertEquals("Product not found with id: 99", exception.getMessage());
		verify(productRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void deactivateProductKeepsRecordAndSetsInactive() {
		Product product = product(8L, true);
		when(productRepository.findById(8L)).thenReturn(Optional.of(product));
		when(productRepository.save(product)).thenReturn(product);

		var response = productService.deactivateProduct(8L);

		assertEquals(8L, response.getId());
		assertFalse(response.getActive());
		assertEquals(8L, product.getId());
		verify(productRepository).save(product);
		verify(productRepository, never()).deleteById(8L);
		verify(productRepository, never()).delete(product);
	}

	@Test
	void deactivateMissingProductThrowsNotFound() {
		when(productRepository.findById(99L)).thenReturn(Optional.empty());

		ResourceNotFoundException exception = assertThrows(
				ResourceNotFoundException.class,
				() -> productService.deactivateProduct(99L));

		assertEquals("Product not found with id: 99", exception.getMessage());
		verify(productRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	private Product product(Long id, Boolean active) {
		Product product = new Product("Original name", "Original description", BigDecimal.TEN, 2, active);
		product.setId(id);
		return product;
	}

	private ProductUpdateRequest updateRequest() {
		ProductUpdateRequest request = new ProductUpdateRequest();
		request.setName("Updated name");
		request.setDescription("Updated description");
		request.setPrice(new BigDecimal("24.50"));
		request.setStockQuantity(12);
		request.setActive(false);
		return request;
	}
}
