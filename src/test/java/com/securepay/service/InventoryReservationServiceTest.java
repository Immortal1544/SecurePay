package com.securepay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import com.securepay.entity.Cart;
import com.securepay.entity.CartItem;
import com.securepay.entity.Order;
import com.securepay.entity.OrderItem;
import com.securepay.entity.OrderStatus;
import com.securepay.entity.Product;
import com.securepay.entity.User;
import com.securepay.exception.OrderConflictException;
import com.securepay.repository.OrderItemRepository;
import com.securepay.repository.OrderRepository;
import com.securepay.repository.ProductRepository;

import jakarta.persistence.LockModeType;

class InventoryReservationServiceTest {

	private ProductRepository productRepository;
	private OrderItemRepository orderItemRepository;
	private InventoryReservationService inventoryReservationService;

	@BeforeEach
	void setUp() {
		productRepository = mock(ProductRepository.class);
		orderItemRepository = mock(OrderItemRepository.class);
		inventoryReservationService = new InventoryReservationService(productRepository, orderItemRepository);
	}

	@Test
	void reservesStockUnderProductLocksAcquiredInStableOrder() {
		Product first = product(1L, 5);
		Product second = product(2L, 3);
		when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(first));
		when(productRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(second));
		List<CartItem> items = List.of(cartItem(second, 2), cartItem(first, 3));

		var reserved = inventoryReservationService.reserveCartItems(items);

		assertEquals(2, first.getStockQuantity());
		assertEquals(1, second.getStockQuantity());
		assertEquals(first, reserved.get(1L));
		assertEquals(second, reserved.get(2L));
		var lockOrder = inOrder(productRepository);
		lockOrder.verify(productRepository).findByIdForUpdate(1L);
		lockOrder.verify(productRepository).findByIdForUpdate(2L);
		verify(productRepository).save(first);
		verify(productRepository).save(second);
	}

	@Test
	void insufficientStockDoesNotMutateAnyProduct() {
		Product available = product(1L, 5);
		Product unavailable = product(2L, 1);
		when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(available));
		when(productRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(unavailable));

		assertThrows(OrderConflictException.class, () -> inventoryReservationService.reserveCartItems(
				List.of(cartItem(available, 2), cartItem(unavailable, 2))));

		assertEquals(5, available.getStockQuantity());
		assertEquals(1, unavailable.getStockQuantity());
		verify(productRepository, never()).save(available);
		verify(productRepository, never()).save(unavailable);
	}

	@Test
	void releaseRestoresStockOnceAndMarksReservationReleased() {
		User user = new User("Customer", "customer@example.com", "password", null);
		Order order = new Order(user, BigDecimal.TEN, OrderStatus.PAID);
		order.setId(10L);
		Product product = product(1L, 2);
		OrderItem item = new OrderItem(order, product, product.getName(), BigDecimal.TEN, 3,
				new BigDecimal("30"));
		when(orderItemRepository.findByOrderId(10L)).thenReturn(List.of(item));
		when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

		inventoryReservationService.releaseReservedInventory(order);
		inventoryReservationService.releaseReservedInventory(order);

		assertEquals(5, product.getStockQuantity());
		assertFalse(order.getInventoryReserved());
		verify(productRepository).save(product);
	}

	@Test
	void legacyOrderWithNullReservationFlagIsReleasedAsReserved() {
		Order order = new Order(null, BigDecimal.TEN, OrderStatus.CREATED);
		order.setId(11L);
		order.setInventoryReserved(null);
		Product product = product(3L, 4);
		OrderItem item = new OrderItem(order, product, product.getName(), BigDecimal.TEN, 1,
				BigDecimal.TEN);
		when(orderItemRepository.findByOrderId(11L)).thenReturn(List.of(item));
		when(productRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(product));

		inventoryReservationService.releaseReservedInventory(order);

		assertEquals(5, product.getStockQuantity());
		assertFalse(order.getInventoryReserved());
	}

	@Test
	void processingOrderIsNotEligibleForStockRelease() {
		Order order = new Order(null, BigDecimal.TEN, OrderStatus.PROCESSING);
		order.setId(12L);

		inventoryReservationService.releaseReservedInventory(order);

		assertTrue(order.getInventoryReserved());
		org.mockito.Mockito.verifyNoInteractions(orderItemRepository, productRepository);
	}

	@Test
	void productReservationLookupUsesPessimisticWriteLock() throws Exception {
		Lock lock = ProductRepository.class.getMethod("findByIdForUpdate", Long.class).getAnnotation(Lock.class);

		assertTrue(lock != null);
		assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
	}

	@Test
	void orderLookupUsesPessimisticWriteLockForSingleRelease() throws Exception {
		Lock lock = OrderRepository.class.getMethod("findByIdForUpdate", Long.class).getAnnotation(Lock.class);

		assertTrue(lock != null);
		assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
	}

	private Product product(Long id, int stock) {
		Product product = new Product("Product " + id, "Description", BigDecimal.TEN, stock, true);
		product.setId(id);
		return product;
	}

	private CartItem cartItem(Product product, int quantity) {
		return new CartItem(new Cart(new User("Customer", "customer@example.com", "password", null)),
				product, quantity);
	}
}
