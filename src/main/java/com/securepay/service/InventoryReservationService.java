package com.securepay.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.securepay.entity.CartItem;
import com.securepay.entity.Order;
import com.securepay.entity.OrderItem;
import com.securepay.entity.OrderStatus;
import com.securepay.entity.Product;
import com.securepay.exception.InactiveProductException;
import com.securepay.exception.OrderConflictException;
import com.securepay.exception.ResourceNotFoundException;
import com.securepay.repository.OrderItemRepository;
import com.securepay.repository.ProductRepository;

@Service
public class InventoryReservationService {

	private final ProductRepository productRepository;
	private final OrderItemRepository orderItemRepository;

	public InventoryReservationService(
			ProductRepository productRepository,
			OrderItemRepository orderItemRepository) {
		this.productRepository = productRepository;
		this.orderItemRepository = orderItemRepository;
	}

	@Transactional
	public Map<Long, Product> reserveCartItems(List<CartItem> cartItems) {
		Map<Long, Integer> requestedByProduct = new TreeMap<>();
		for (CartItem cartItem : cartItems) {
			Long productId = cartItem.getProduct().getId();
			try {
				requestedByProduct.merge(productId, cartItem.getQuantity(), Math::addExact);
			} catch (ArithmeticException exception) {
				throw new OrderConflictException("Requested quantity exceeds available stock");
			}
		}

		Map<Long, Product> lockedProducts = lockProducts(requestedByProduct.keySet());
		for (Map.Entry<Long, Integer> requested : requestedByProduct.entrySet()) {
			Product product = lockedProducts.get(requested.getKey());
			if (!Boolean.TRUE.equals(product.getActive())) {
				throw new InactiveProductException();
			}
			if (product.getStockQuantity() < requested.getValue()) {
				throw new OrderConflictException("Insufficient stock for product " + product.getName()
						+ ": available=" + product.getStockQuantity()
						+ ", requested=" + requested.getValue());
			}
		}

		for (Map.Entry<Long, Integer> requested : requestedByProduct.entrySet()) {
			Product product = lockedProducts.get(requested.getKey());
			product.setStockQuantity(product.getStockQuantity() - requested.getValue());
			productRepository.save(product);
		}
		return lockedProducts;
	}

	/**
	 * Releases a reservation once. A null flag represents a legacy order whose
	 * stock was deducted before the reservation column was introduced.
	 */
	@Transactional
	public void releaseReservedInventory(Order order) {
		if (order.getStatus() != OrderStatus.CREATED
				&& order.getStatus() != OrderStatus.PAYMENT_PENDING
				&& order.getStatus() != OrderStatus.PAID) {
			return;
		}
		if (Boolean.FALSE.equals(order.getInventoryReserved())) {
			return;
		}

		Map<Long, Integer> quantityByProduct = new TreeMap<>();
		List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
		for (OrderItem orderItem : orderItems) {
			Long productId = orderItem.getProduct().getId();
			try {
				quantityByProduct.merge(productId, orderItem.getQuantity(), Math::addExact);
			} catch (ArithmeticException exception) {
				throw new OrderConflictException("Reserved quantity exceeds supported stock range");
			}
		}

		Map<Long, Product> lockedProducts = lockProducts(quantityByProduct.keySet());
		for (Map.Entry<Long, Integer> reserved : quantityByProduct.entrySet()) {
			Product product = lockedProducts.get(reserved.getKey());
			try {
				product.setStockQuantity(Math.addExact(product.getStockQuantity(), reserved.getValue()));
			} catch (ArithmeticException exception) {
				throw new OrderConflictException("Released stock exceeds supported quantity range");
			}
			productRepository.save(product);
		}
		order.setInventoryReserved(false);
	}

	private Map<Long, Product> lockProducts(Iterable<Long> productIds) {
		Map<Long, Product> products = new HashMap<>();
		for (Long productId : productIds) {
			Product product = productRepository.findByIdForUpdate(productId)
					.orElseThrow(() -> new ResourceNotFoundException(
							"Product not found with id: " + productId));
			products.put(productId, product);
		}
		return products;
	}
}
