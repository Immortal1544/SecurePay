package com.securepay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
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

import com.securepay.dto.order.CreateOrderRequest;
import com.securepay.entity.Cart;
import com.securepay.entity.CartItem;
import com.securepay.entity.Order;
import com.securepay.entity.OrderStatus;
import com.securepay.entity.Product;
import com.securepay.entity.Role;
import com.securepay.entity.User;
import com.securepay.repository.CartItemRepository;
import com.securepay.repository.CartRepository;
import com.securepay.repository.OrderItemRepository;
import com.securepay.repository.OrderRepository;
import com.securepay.repository.ProductRepository;
import com.securepay.repository.UserRepository;

class OrderServiceDeliveryDetailsTest {

	private UserRepository userRepository;
	private CartRepository cartRepository;
	private CartItemRepository cartItemRepository;
	private ProductRepository productRepository;
	private OrderRepository orderRepository;
	private OrderItemRepository orderItemRepository;
	private OrderService orderService;
	private User user;
	private Order savedOrder;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		cartRepository = mock(CartRepository.class);
		cartItemRepository = mock(CartItemRepository.class);
		productRepository = mock(ProductRepository.class);
		orderRepository = mock(OrderRepository.class);
		orderItemRepository = mock(OrderItemRepository.class);
		InventoryReservationService inventoryReservationService = new InventoryReservationService(
				productRepository, orderItemRepository);
		orderService = new OrderService(userRepository, cartRepository,
				cartItemRepository, orderRepository, orderItemRepository, inventoryReservationService);

		user = new User("Original Name", "customer@example.com", "password", Role.USER);
		user.setId(7L);
		Cart cart = new Cart(user);
		cart.setId(8L);
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken("customer@example.com", "", List.of()));
		when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));
		when(cartRepository.findByUserId(7L)).thenReturn(Optional.of(cart));
		Product product = new Product("Snapshot Product", "Description", new BigDecimal("12.50"), 5, true);
		product.setId(9L);
		when(productRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(product));
		when(cartItemRepository.findByCartId(8L)).thenReturn(List.of(new CartItem(cart, product, 2)));
		when(orderItemRepository.findByOrderId(anyLong())).thenReturn(List.of());
		when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
			savedOrder = invocation.getArgument(0);
			savedOrder.setId(50L);
			return savedOrder;
		});
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void savesDeliverySnapshotOnOrderAndReturnsItAfterUserProfileChanges() {
		CreateOrderRequest request = request();

		var created = orderService.createOrder(request);

		assertEquals("Asha Recipient", savedOrder.getRecipientName());
		assertEquals(true, savedOrder.getInventoryReserved());
		assertEquals("+919876543210", savedOrder.getPhoneNumber());
		assertEquals("12 SecurePay Street", savedOrder.getAddressLine1());
		assertEquals("Apartment 4B", savedOrder.getAddressLine2());
		assertEquals("Bengaluru", savedOrder.getCity());
		assertEquals("Karnataka", savedOrder.getState());
		assertEquals("560001", savedOrder.getPostalCode());
		assertEquals("India", savedOrder.getCountry());
		assertEquals("Asha Recipient", created.getRecipientName());
		assertEquals("12 SecurePay Street", created.getAddressLine1());
		assertEquals(new BigDecimal("25.00"), created.getTotalAmount());
		verify(productRepository).save(any(Product.class));
		verify(cartItemRepository).deleteAll(any());

		user.setName("Changed User Name");
		user.setEmail("changed@example.com");
		when(orderRepository.findByIdAndUserId(50L, 7L)).thenReturn(Optional.of(savedOrder));

		var historyResponse = orderService.getCurrentUserOrder(50L);

		assertEquals("Asha Recipient", historyResponse.getRecipientName());
		assertEquals("+919876543210", historyResponse.getPhoneNumber());
		assertEquals("12 SecurePay Street", historyResponse.getAddressLine1());
		assertEquals("Apartment 4B", historyResponse.getAddressLine2());
		assertEquals("Bengaluru", historyResponse.getCity());
		assertEquals("Karnataka", historyResponse.getState());
		assertEquals("560001", historyResponse.getPostalCode());
		assertEquals("India", historyResponse.getCountry());
		assertEquals(OrderStatus.CREATED, historyResponse.getStatus());
	}

	private CreateOrderRequest request() {
		CreateOrderRequest request = new CreateOrderRequest();
		request.setRecipientName(" Asha Recipient ");
		request.setPhoneNumber("+919876543210");
		request.setAddressLine1(" 12 SecurePay Street ");
		request.setAddressLine2(" Apartment 4B ");
		request.setCity(" Bengaluru ");
		request.setState(" Karnataka ");
		request.setPostalCode("560001");
		request.setCountry(" India ");
		return request;
	}
}
