package com.securepay.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.securepay.dto.cart.AddToCartRequest;
import com.securepay.dto.cart.CartItemResponse;
import com.securepay.dto.cart.CartResponse;
import com.securepay.dto.cart.UpdateCartItemRequest;
import com.securepay.entity.Cart;
import com.securepay.entity.CartItem;
import com.securepay.entity.Product;
import com.securepay.entity.User;
import com.securepay.exception.InactiveProductException;
import com.securepay.exception.ResourceNotFoundException;
import com.securepay.repository.CartItemRepository;
import com.securepay.repository.CartRepository;
import com.securepay.repository.ProductRepository;
import com.securepay.repository.UserRepository;

@Service
@Transactional
public class CartService {

	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final ProductRepository productRepository;
	private final UserRepository userRepository;

	public CartService(
			CartRepository cartRepository,
			CartItemRepository cartItemRepository,
			ProductRepository productRepository,
			UserRepository userRepository) {
		this.cartRepository = cartRepository;
		this.cartItemRepository = cartItemRepository;
		this.productRepository = productRepository;
		this.userRepository = userRepository;
	}

	public CartResponse getCurrentUserCart() {
		User user = getCurrentUser();
		return cartRepository.findByUserId(user.getId())
				.map(this::toCartResponse)
				.orElseGet(() -> emptyCartResponse(user));
	}

	public CartResponse addToCart(AddToCartRequest request) {
		User user = getCurrentUser();
		Product product = productRepository.findById(request.getProductId())
				.orElseThrow(() -> new ResourceNotFoundException(
						"Product not found with id: " + request.getProductId()));

		if (!Boolean.TRUE.equals(product.getActive())) {
			throw new InactiveProductException();
		}
		Cart cart = getOrCreateCart(user);

		CartItem cartItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())
				.map(existingItem -> {
					existingItem.setQuantity(existingItem.getQuantity() + request.getQuantity());
					return existingItem;
				})
				.orElseGet(() -> new CartItem(cart, product, request.getQuantity()));

		cartItemRepository.save(cartItem);
		return toCartResponse(cart);
	}

	public CartResponse updateCartItem(Long cartItemId, UpdateCartItemRequest request) {
		User user = getCurrentUser();
		CartItem cartItem = getCartItem(cartItemId);
		verifyOwnership(cartItem, user);
		if (!Boolean.TRUE.equals(cartItem.getProduct().getActive())) {
			throw new InactiveProductException();
		}

		cartItem.setQuantity(request.getQuantity());
		cartItemRepository.save(cartItem);
		return toCartResponse(cartItem.getCart());
	}

	public CartResponse removeCartItem(Long cartItemId) {
		User user = getCurrentUser();
		CartItem cartItem = getCartItem(cartItemId);
		verifyOwnership(cartItem, user);

		Cart cart = cartItem.getCart();
		cartItemRepository.delete(cartItem);
		return toCartResponse(cart);
	}

	public void clearCart() {
		User user = getCurrentUser();
		cartRepository.findByUserId(user.getId())
				.ifPresent(cart -> cartItemRepository.deleteAll(cartItemRepository.findByCartId(cart.getId())));
	}

	private User getCurrentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null
				|| !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			throw new IllegalStateException("No authenticated user found");
		}

		return userRepository.findByEmail(authentication.getName())
				.orElseThrow(() -> new ResourceNotFoundException(
						"Authenticated user not found with email: " + authentication.getName()));
	}

	private Cart getOrCreateCart(User user) {
		return cartRepository.findByUserId(user.getId())
				.orElseGet(() -> cartRepository.save(new Cart(user)));
	}

	private CartItem getCartItem(Long cartItemId) {
		return cartItemRepository.findById(cartItemId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"Cart item not found with id: " + cartItemId));
	}

	private void verifyOwnership(CartItem cartItem, User user) {
		if (!cartItem.getCart().getUser().getId().equals(user.getId())) {
			throw new IllegalStateException("Cart item does not belong to the current user");
		}
	}

	private CartResponse toCartResponse(Cart cart) {
		List<CartItemResponse> items = cartItemRepository.findByCartId(cart.getId())
				.stream()
				.map(this::toCartItemResponse)
				.toList();

		CartResponse response = new CartResponse();
		response.setCartId(cart.getId());
		response.setUserId(cart.getUser().getId());
		response.setItems(items);
		response.setTotalAmount(items.stream()
				.map(CartItemResponse::getSubtotal)
				.reduce(BigDecimal.ZERO, BigDecimal::add));
		return response;
	}

	private CartResponse emptyCartResponse(User user) {
		CartResponse response = new CartResponse();
		response.setUserId(user.getId());
		response.setItems(List.of());
		response.setTotalAmount(BigDecimal.ZERO);
		return response;
	}

	private CartItemResponse toCartItemResponse(CartItem cartItem) {
		Product product = cartItem.getProduct();
		BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));

		CartItemResponse response = new CartItemResponse();
		response.setId(cartItem.getId());
		response.setProductId(product.getId());
		response.setProductName(product.getName());
		response.setPrice(product.getPrice());
		response.setQuantity(cartItem.getQuantity());
		response.setSubtotal(subtotal);
		return response;
	}
}
