package com.securepay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.securepay.dto.cart.AddToCartRequest;
import com.securepay.dto.cart.UpdateCartItemRequest;
import com.securepay.entity.Cart;
import com.securepay.entity.CartItem;
import com.securepay.entity.Product;
import com.securepay.entity.Role;
import com.securepay.entity.User;
import com.securepay.exception.InactiveProductException;
import com.securepay.repository.CartItemRepository;
import com.securepay.repository.CartRepository;
import com.securepay.repository.ProductRepository;
import com.securepay.repository.UserRepository;

class CartServiceInactiveProductTest {

	private CartRepository cartRepository;
	private CartItemRepository cartItemRepository;
	private ProductRepository productRepository;
	private UserRepository userRepository;
	private CartService cartService;
	private User user;
	private Cart cart;

	@BeforeEach
	void setUp() {
		cartRepository = mock(CartRepository.class);
		cartItemRepository = mock(CartItemRepository.class);
		productRepository = mock(ProductRepository.class);
		userRepository = mock(UserRepository.class);
		cartService = new CartService(cartRepository, cartItemRepository, productRepository, userRepository);
		user = new User("Test User", "user@example.com", "password", Role.USER);
		user.setId(1L);
		cart = new Cart(user);
		cart.setId(2L);
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken("user@example.com", "password", List.of()));
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void activeProductCanBeAddedToCart() {
		Product product = product(10L, true);
		when(productRepository.findById(10L)).thenReturn(Optional.of(product));
		when(cartItemRepository.findByCartIdAndProductId(2L, 10L)).thenReturn(Optional.empty());
		when(cartItemRepository.findByCartId(2L)).thenReturn(List.of());
		AddToCartRequest request = addRequest(10L, 2);

		cartService.addToCart(request);

		verify(cartItemRepository).save(any(CartItem.class));
	}

	@Test
	void inactiveProductCannotBeAddedOrIncrementExistingCartItem() {
		Product product = product(10L, false);
		CartItem existingItem = new CartItem(cart, product, 3);
		when(productRepository.findById(10L)).thenReturn(Optional.of(product));
		when(cartItemRepository.findByCartIdAndProductId(2L, 10L)).thenReturn(Optional.of(existingItem));

		assertThrows(InactiveProductException.class,
				() -> cartService.addToCart(addRequest(10L, 2)));

		assertEquals(3, existingItem.getQuantity());
		verify(cartItemRepository, never()).findByCartIdAndProductId(2L, 10L);
		verify(cartItemRepository, never()).save(any(CartItem.class));
	}

	@Test
	void inactiveCartProductCannotHaveItsQuantityUpdated() {
		Product product = product(10L, false);
		CartItem item = new CartItem(cart, product, 3);
		when(cartItemRepository.findById(4L)).thenReturn(Optional.of(item));
		UpdateCartItemRequest request = new UpdateCartItemRequest();
		request.setQuantity(5);

		assertThrows(InactiveProductException.class, () -> cartService.updateCartItem(4L, request));

		assertEquals(3, item.getQuantity());
		verify(cartItemRepository, never()).save(item);
	}

	private Product product(Long id, boolean active) {
		Product product = new Product("Test product", "Description", BigDecimal.TEN, 10, active);
		product.setId(id);
		return product;
	}

	private AddToCartRequest addRequest(Long productId, int quantity) {
		AddToCartRequest request = new AddToCartRequest();
		request.setProductId(productId);
		request.setQuantity(quantity);
		return request;
	}
}
