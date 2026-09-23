package com.securepay.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.securepay.dto.cart.AddToCartRequest;
import com.securepay.dto.cart.CartResponse;
import com.securepay.dto.cart.UpdateCartItemRequest;
import com.securepay.service.CartService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/cart")
public class CartController {

	private final CartService cartService;

	public CartController(CartService cartService) {
		this.cartService = cartService;
	}

	@GetMapping
	public ResponseEntity<CartResponse> getCurrentUserCart() {
		return ResponseEntity.ok(cartService.getCurrentUserCart());
	}

	@PostMapping("/items")
	public ResponseEntity<CartResponse> addToCart(
			@Valid @RequestBody AddToCartRequest request) {
		return ResponseEntity.ok(cartService.addToCart(request));
	}

	@PutMapping("/items/{cartItemId}")
	public ResponseEntity<CartResponse> updateCartItem(
			@PathVariable Long cartItemId,
			@Valid @RequestBody UpdateCartItemRequest request) {
		return ResponseEntity.ok(cartService.updateCartItem(cartItemId, request));
	}

	@DeleteMapping("/items/{cartItemId}")
	public ResponseEntity<CartResponse> removeCartItem(@PathVariable Long cartItemId) {
		return ResponseEntity.ok(cartService.removeCartItem(cartItemId));
	}

	@DeleteMapping
	public ResponseEntity<Void> clearCart() {
		cartService.clearCart();
		return ResponseEntity.noContent().build();
	}
}