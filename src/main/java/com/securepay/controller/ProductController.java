package com.securepay.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.securepay.dto.product.ProductCreateRequest;
import com.securepay.dto.product.ProductResponse;
import com.securepay.dto.product.ProductUpdateRequest;
import com.securepay.service.ProductService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
public class ProductController {

	private final ProductService productService;

	public ProductController(ProductService productService) {
		this.productService = productService;
	}

	@PostMapping
	public ResponseEntity<ProductResponse> createProduct(
			@Valid @RequestBody ProductCreateRequest request) {
		ProductResponse response = productService.createProduct(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping
	public ResponseEntity<List<ProductResponse>> getAllProducts() {
		return ResponseEntity.ok(productService.getAllProducts());
	}

	@GetMapping("/{id}")
	public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
		return ResponseEntity.ok(productService.getProductById(id));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ProductResponse> updateProduct(
			@PathVariable Long id,
			@Valid @RequestBody ProductUpdateRequest request) {
		return ResponseEntity.ok(productService.updateProduct(id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ProductResponse> deactivateProduct(@PathVariable Long id) {
		return ResponseEntity.ok(productService.deactivateProduct(id));
	}
}
