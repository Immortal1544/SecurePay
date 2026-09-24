package com.securepay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.securepay.entity.Order;
import com.securepay.entity.OrderStatus;
import com.securepay.entity.User;
import com.securepay.exception.InvalidOrderStatusTransitionException;
import com.securepay.exception.ResourceNotFoundException;
import com.securepay.repository.CartItemRepository;
import com.securepay.repository.CartRepository;
import com.securepay.repository.OrderItemRepository;
import com.securepay.repository.OrderRepository;
import com.securepay.repository.ProductRepository;
import com.securepay.repository.UserRepository;

class OrderServiceStatusTransitionTest {

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
	}

	@AfterEach
	void clearSecurityContext() {
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
	}

	@Test
	void acceptsEveryWhitelistedTransitionAndPersistsOnlyOrderStatus() {
		List<Transition> transitions = List.of(
				new Transition(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING),
				new Transition(OrderStatus.CREATED, OrderStatus.CANCELLED),
				new Transition(OrderStatus.PAYMENT_PENDING, OrderStatus.CANCELLED),
				new Transition(OrderStatus.PAID, OrderStatus.PROCESSING),
				new Transition(OrderStatus.PAID, OrderStatus.CANCELLED),
				new Transition(OrderStatus.PROCESSING, OrderStatus.SHIPPED),
				new Transition(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
				new Transition(OrderStatus.SHIPPED, OrderStatus.DELIVERED));
		long orderId = 1L;

		for (Transition transition : transitions) {
			Order order = order(orderId, transition.from());
			when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
			when(orderRepository.save(order)).thenReturn(order);
			when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());

			var response = orderService.updateOrderStatus(orderId, transition.to());

			assertEquals(transition.to(), order.getStatus());
			assertEquals(transition.to(), response.getStatus());
			assertEquals(new BigDecimal("15.00"), order.getTotalAmount());
			verify(orderRepository).save(order);
			orderId++;
		}

		verifyNoInteractions(userRepository, productRepository, cartRepository, cartItemRepository);
	}

	@Test
	void rejectsEveryTransitionOutsideTheWhitelistWithoutSavingOrChangingStatus() {
		List<Transition> invalidTransitions = List.of(
				new Transition(OrderStatus.CREATED, OrderStatus.PAID),
				new Transition(OrderStatus.CREATED, OrderStatus.SHIPPED),
				new Transition(OrderStatus.PAYMENT_PENDING, OrderStatus.PAID),
				new Transition(OrderStatus.PAYMENT_PENDING, OrderStatus.PROCESSING),
				new Transition(OrderStatus.PAID, OrderStatus.SHIPPED),
				new Transition(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
				new Transition(OrderStatus.DELIVERED, OrderStatus.CREATED),
				new Transition(OrderStatus.DELIVERED, OrderStatus.PAYMENT_PENDING),
				new Transition(OrderStatus.DELIVERED, OrderStatus.PAID),
				new Transition(OrderStatus.DELIVERED, OrderStatus.PROCESSING),
				new Transition(OrderStatus.DELIVERED, OrderStatus.SHIPPED),
				new Transition(OrderStatus.DELIVERED, OrderStatus.CANCELLED),
				new Transition(OrderStatus.CANCELLED, OrderStatus.CREATED),
				new Transition(OrderStatus.CANCELLED, OrderStatus.PAYMENT_PENDING),
				new Transition(OrderStatus.CANCELLED, OrderStatus.PAID),
				new Transition(OrderStatus.CANCELLED, OrderStatus.PROCESSING),
				new Transition(OrderStatus.CANCELLED, OrderStatus.SHIPPED),
				new Transition(OrderStatus.CANCELLED, OrderStatus.DELIVERED),
				new Transition(OrderStatus.CANCELLED, OrderStatus.CANCELLED),
				new Transition(OrderStatus.CREATED, OrderStatus.CREATED),
				new Transition(OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_PENDING),
				new Transition(OrderStatus.PAID, OrderStatus.PAID),
				new Transition(OrderStatus.PROCESSING, OrderStatus.PROCESSING),
				new Transition(OrderStatus.SHIPPED, OrderStatus.SHIPPED),
				new Transition(OrderStatus.DELIVERED, OrderStatus.DELIVERED));
		long orderId = 100L;

		for (Transition transition : invalidTransitions) {
			long currentOrderId = orderId++;
			Order order = order(currentOrderId, transition.from());
			when(orderRepository.findById(currentOrderId)).thenReturn(Optional.of(order));

			assertThrows(InvalidOrderStatusTransitionException.class,
					() -> orderService.updateOrderStatus(currentOrderId, transition.to()));

			assertEquals(transition.from(), order.getStatus());
			verify(orderRepository, never()).save(any(Order.class));
		}

		verifyNoInteractions(orderItemRepository, userRepository, productRepository,
				cartRepository, cartItemRepository);
	}

	@Test
	void nullStatusIsRejectedWithoutSaving() {
		Order order = order(500L, OrderStatus.CREATED);
		when(orderRepository.findById(500L)).thenReturn(Optional.of(order));

		assertThrows(InvalidOrderStatusTransitionException.class,
				() -> orderService.updateOrderStatus(500L, null));

		assertEquals(OrderStatus.CREATED, order.getStatus());
		verify(orderRepository, never()).save(any(Order.class));
		verifyNoInteractions(orderItemRepository, productRepository, cartRepository, cartItemRepository);
	}

	@Test
	void missingOrderThrowsResourceNotFoundException() {
		when(orderRepository.findById(999L)).thenReturn(Optional.empty());

		ResourceNotFoundException exception = assertThrows(
				ResourceNotFoundException.class,
				() -> orderService.updateOrderStatus(999L, OrderStatus.CANCELLED));

		assertEquals("Order not found with id: 999", exception.getMessage());
		verify(orderRepository, never()).save(any(Order.class));
	}

	private Order order(Long id, OrderStatus status) {
		User user = new User("Test User", "user@example.com", "password", null);
		Order order = new Order(user, new BigDecimal("15.00"), status);
		order.setId(id);
		return order;
	}

	private record Transition(OrderStatus from, OrderStatus to) {
	}
}
