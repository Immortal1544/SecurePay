package com.securepay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

import com.securepay.entity.Cart;
import com.securepay.entity.CartItem;
import com.securepay.entity.Order;
import com.securepay.entity.OrderItem;
import com.securepay.entity.OrderStatus;
import com.securepay.entity.Product;
import com.securepay.entity.Role;
import com.securepay.entity.User;
import com.securepay.dto.order.CreateOrderRequest;
import com.securepay.exception.InactiveProductException;
import com.securepay.repository.CartItemRepository;
import com.securepay.repository.CartRepository;
import com.securepay.repository.OrderItemRepository;
import com.securepay.repository.OrderRepository;
import com.securepay.repository.ProductRepository;
import com.securepay.repository.UserRepository;

class OrderServiceInactiveProductTest {

	private UserRepository userRepository;
	private ProductRepository productRepository;
	private CartRepository cartRepository;
	private CartItemRepository cartItemRepository;
	private OrderRepository orderRepository;
	private OrderItemRepository orderItemRepository;
	private OrderService orderService;
	private User user;
	private Cart cart;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		productRepository = mock(ProductRepository.class);
		cartRepository = mock(CartRepository.class);
		cartItemRepository = mock(CartItemRepository.class);
		orderRepository = mock(OrderRepository.class);
		orderItemRepository = mock(OrderItemRepository.class);
		InventoryReservationService inventoryReservationService = new InventoryReservationService(
				productRepository, orderItemRepository);
		orderService = new OrderService(userRepository, cartRepository,
				cartItemRepository, orderRepository, orderItemRepository, inventoryReservationService);
		user = new User("Test User", "user@example.com", "password", Role.USER);
		user.setId(1L);
		cart = new Cart(user);
		cart.setId(2L);
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken("user@example.com", "password", List.of()));
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
		when(orderItemRepository.findByOrderId(anyLong())).thenReturn(List.of());
		when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void createsOrderAndReducesStockForActiveProducts() {
		Product product = product(10L, true, 8);
		when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(product));
		CartItem item = new CartItem(cart, product, 3);
		when(cartItemRepository.findByCartId(2L)).thenReturn(List.of(item));

		var response = orderService.createOrder(validRequest());

		assertEquals(new BigDecimal("30"), response.getTotalAmount());
		assertEquals(5, product.getStockQuantity());
		verify(orderRepository).save(any(Order.class));
		verify(productRepository).save(product);
		verify(cartItemRepository).deleteAll(List.of(item));
	}

	@Test
	void inactiveProductRejectsOrderBeforePersistenceStockChangesOrCartClearing() {
		Product activeProduct = product(10L, true, 8);
		Product inactiveProduct = product(11L, false, 8);
		when(productRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(activeProduct));
		when(productRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(inactiveProduct));
		CartItem activeItem = new CartItem(cart, activeProduct, 3);
		CartItem inactiveItem = new CartItem(cart, inactiveProduct, 2);
		List<CartItem> items = List.of(activeItem, inactiveItem);
		when(cartItemRepository.findByCartId(2L)).thenReturn(items);

		assertThrows(InactiveProductException.class, () -> orderService.createOrder(validRequest()));

		assertEquals(8, activeProduct.getStockQuantity());
		assertEquals(8, inactiveProduct.getStockQuantity());
		verify(orderRepository, never()).save(any(Order.class));
		verify(orderItemRepository, never()).save(any());
		verify(productRepository, never()).save(any(Product.class));
		verify(cartItemRepository, never()).deleteAll(items);
	}

	@Test
	void insufficientStockRejectsOrderBeforePersistenceOrCartClearing() {
		Product product = product(12L, true, 1);
		when(productRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(product));
		CartItem item = new CartItem(cart, product, 3);
		when(cartItemRepository.findByCartId(2L)).thenReturn(List.of(item));

		assertThrows(com.securepay.exception.OrderConflictException.class,
				() -> orderService.createOrder(validRequest()));

		assertEquals(1, product.getStockQuantity());
		verify(orderRepository, never()).save(any(Order.class));
		verify(orderItemRepository, never()).save(any());
		verify(productRepository, never()).save(any(Product.class));
		verify(cartItemRepository, never()).deleteAll(List.of(item));
	}

	@Test
	void cancellingPendingOrderReleasesReservedStockExactlyOnce() {
		Product product = product(13L, true, 4);
		Order order = new Order(user, BigDecimal.valueOf(20), OrderStatus.PAYMENT_PENDING);
		order.setId(50L);
		OrderItem orderItem = new OrderItem(order, product, product.getName(), product.getPrice(), 2,
				BigDecimal.valueOf(20));
		when(orderRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(order));
		when(orderItemRepository.findByOrderId(50L)).thenReturn(List.of(orderItem));
		when(productRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(product));
		when(orderRepository.save(order)).thenReturn(order);

		var response = orderService.updateOrderStatus(50L, OrderStatus.CANCELLED);

		assertEquals(OrderStatus.CANCELLED, response.getStatus());
		assertEquals(6, product.getStockQuantity());
		assertEquals(false, order.getInventoryReserved());
		verify(productRepository).save(product);
		verify(orderRepository).save(order);

		assertThrows(com.securepay.exception.InvalidOrderStatusTransitionException.class,
				() -> orderService.updateOrderStatus(50L, OrderStatus.CANCELLED));
		assertEquals(6, product.getStockQuantity());
		verify(productRepository).save(product);
	}

	private Product product(Long id, boolean active, int stock) {
		Product product = new Product("Test product " + id, "Description", BigDecimal.TEN, stock, active);
		product.setId(id);
		return product;
	}

	private CreateOrderRequest validRequest() {
		CreateOrderRequest request = new CreateOrderRequest();
		request.setRecipientName("Test Recipient");
		request.setPhoneNumber("+919876543210");
		request.setAddressLine1("12 SecurePay Street");
		request.setCity("Bengaluru");
		request.setState("Karnataka");
		request.setPostalCode("560001");
		request.setCountry("India");
		return request;
	}
}
