package com.securepay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.securepay.entity.Order;
import com.securepay.entity.OrderStatus;
import com.securepay.entity.Role;
import com.securepay.entity.User;
import com.securepay.exception.ResourceNotFoundException;
import com.securepay.repository.CartItemRepository;
import com.securepay.repository.CartRepository;
import com.securepay.repository.OrderItemRepository;
import com.securepay.repository.OrderRepository;
import com.securepay.repository.ProductRepository;
import com.securepay.repository.UserRepository;

class OrderServiceAdminTest {

	private UserRepository userRepository;
	private ProductRepository productRepository;
	private CartRepository cartRepository;
	private CartItemRepository cartItemRepository;
	private OrderRepository orderRepository;
	private OrderItemRepository orderItemRepository;
	private OrderService orderService;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		productRepository = mock(ProductRepository.class);
		cartRepository = mock(CartRepository.class);
		cartItemRepository = mock(CartItemRepository.class);
		orderRepository = mock(OrderRepository.class);
		orderItemRepository = mock(OrderItemRepository.class);
		orderService = new OrderService(userRepository, productRepository, cartRepository,
				cartItemRepository, orderRepository, orderItemRepository);
		when(orderItemRepository.findByOrderId(anyLong())).thenReturn(List.of());
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void getAllOrdersForAdminReturnsNewestFirstFromRepository() {
		User owner = user(2L, "owner@example.com");
		Order newest = order(20L, owner, LocalDateTime.of(2026, 4, 2, 10, 0));
		Order older = order(10L, owner, LocalDateTime.of(2026, 4, 1, 10, 0));
		when(orderRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(newest, older));

		var responses = orderService.getAllOrdersForAdmin();

		assertEquals(List.of(20L, 10L), responses.stream().map(response -> response.getOrderId()).toList());
		verify(orderRepository).findAllByOrderByCreatedAtDesc();
	}

	@Test
	void getOrderByIdForAdminDoesNotScopeOrderToCurrentUser() {
		User anotherUser = user(2L, "another@example.com");
		Order order = order(20L, anotherUser, LocalDateTime.now());
		when(orderRepository.findById(20L)).thenReturn(Optional.of(order));
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken("admin@example.com", null, List.of()));

		var response = orderService.getOrderByIdForAdmin(20L);

		assertEquals(20L, response.getOrderId());
		verify(orderRepository).findById(20L);
		verify(orderRepository, never()).findByIdAndUserId(20L, 1L);
	}

	@Test
	void missingAdminOrderThrowsResourceNotFoundException() {
		when(orderRepository.findById(99L)).thenReturn(Optional.empty());

		ResourceNotFoundException exception = assertThrows(
				ResourceNotFoundException.class,
				() -> orderService.getOrderByIdForAdmin(99L));

		assertEquals("Order not found with id: 99", exception.getMessage());
	}

	@Test
	void customerOrderLookupRemainsScopedToCurrentUser() {
		User customer = user(1L, "customer@example.com");
		when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
		when(orderRepository.findByIdAndUserId(20L, 1L)).thenReturn(Optional.empty());
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken("customer@example.com", null, List.of()));

		assertThrows(ResourceNotFoundException.class, () -> orderService.getCurrentUserOrder(20L));
		verify(orderRepository).findByIdAndUserId(20L, 1L);
		verify(orderRepository, never()).findById(20L);
	}

	private User user(Long id, String email) {
		User user = new User("Test User", email, "password", Role.USER);
		user.setId(id);
		return user;
	}

	private Order order(Long id, User user, LocalDateTime createdAt) {
		Order order = new Order(user, BigDecimal.TEN, OrderStatus.CREATED);
		order.setId(id);
		order.setCreatedAt(createdAt);
		return order;
	}
}
