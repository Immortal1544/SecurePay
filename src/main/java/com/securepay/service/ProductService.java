package com.securepay.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.securepay.dto.product.ProductCreateRequest;
import com.securepay.dto.product.ProductResponse;
import com.securepay.entity.Product;
import com.securepay.exception.ResourceNotFoundException;
import com.securepay.repository.ProductRepository;

@Service
public class ProductService {

	private final ProductRepository productRepository;

	public ProductService(ProductRepository productRepository) {
		this.productRepository = productRepository;
	}

	public ProductResponse createProduct(ProductCreateRequest request) {
		Product product = new Product(
				request.getName(),
				request.getDescription(),
				request.getPrice(),
				request.getStockQuantity(),
				request.getActive());

		return toProductResponse(productRepository.save(product));
	}

	public List<ProductResponse> getAllProducts() {
		return productRepository.findAll()
				.stream()
				.map(this::toProductResponse)
				.toList();
	}

	public ProductResponse getProductById(Long id) {
		Product product = productRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

		return toProductResponse(product);
	}

	private ProductResponse toProductResponse(Product product) {
		ProductResponse response = new ProductResponse();
		response.setId(product.getId());
		response.setName(product.getName());
		response.setDescription(product.getDescription());
		response.setPrice(product.getPrice());
		response.setStockQuantity(product.getStockQuantity());
		response.setActive(product.getActive());
		response.setCreatedAt(product.getCreatedAt());
		response.setUpdatedAt(product.getUpdatedAt());
		return response;
	}
}