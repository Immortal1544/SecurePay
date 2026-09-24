package com.securepay.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.securepay.dto.order.OrderItemResponse;
import com.securepay.dto.order.OrderResponse;
import com.securepay.entity.Cart;
import com.securepay.entity.CartItem;
import com.securepay.entity.Order;
import com.securepay.entity.OrderItem;
import com.securepay.entity.OrderStatus;
import com.securepay.entity.Product;
import com.securepay.entity.User;
import com.securepay.exception.InactiveProductException;
import com.securepay.exception.ResourceNotFoundException;
import com.securepay.repository.CartItemRepository;
import com.securepay.repository.CartRepository;
import com.securepay.repository.OrderItemRepository;
import com.securepay.repository.OrderRepository;
import com.securepay.repository.ProductRepository;
import com.securepay.repository.UserRepository;

@Service
public class OrderService {

	private final UserRepository userRepository;
	private final ProductRepository productRepository;
	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;

	public OrderService(
			UserRepository userRepository,
			ProductRepository productRepository,
			CartRepository cartRepository,
			CartItemRepository cartItemRepository,
			OrderRepository orderRepository,
			OrderItemRepository orderItemRepository) {
		this.userRepository = userRepository;
		this.productRepository = productRepository;
		this.cartRepository = cartRepository;
		this.cartItemRepository = cartItemRepository;
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
	}

	@Transactional
	public OrderResponse createOrder() {
		User user = getCurrentUser();
		Cart cart = cartRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Cart not found for current user"));
		List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());

		if (cartItems.isEmpty()) {
			throw new IllegalStateException("Cannot create order from an empty cart");
		}

		BigDecimal totalAmount = BigDecimal.ZERO;
		for (CartItem cartItem : cartItems) {
			Product product = cartItem.getProduct();
			if (!Boolean.TRUE.equals(product.getActive())) {
				throw new InactiveProductException();
			}

			int requestedQuantity = cartItem.getQuantity();
			int availableQuantity = product.getStockQuantity();
			if (availableQuantity < requestedQuantity) {
				throw new IllegalStateException("Insufficient stock for product " + product.getName()
						+ ": available=" + availableQuantity + ", requested=" + requestedQuantity);
			}

			totalAmount = totalAmount.add(calculateSubtotal(product.getPrice(), requestedQuantity));
		}

		Order order = orderRepository.save(new Order(user, totalAmount, OrderStatus.CREATED));
		for (CartItem cartItem : cartItems) {
			Product product = cartItem.getProduct();
			int quantity = cartItem.getQuantity();
			BigDecimal subtotal = calculateSubtotal(product.getPrice(), quantity);

			orderItemRepository.save(new OrderItem(
					order,
					product,
					product.getName(),
					product.getPrice(),
					quantity,
					subtotal));

			product.setStockQuantity(product.getStockQuantity() - quantity);
			productRepository.save(product);
		}

		cartItemRepository.deleteAll(cartItems);
		return toOrderResponse(order);
	}

	@Transactional
	public List<OrderResponse> getCurrentUserOrders() {
		User user = getCurrentUser();
		return orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
				.stream()
				.map(this::toOrderResponse)
				.toList();
	}

	@Transactional
	public OrderResponse getCurrentUserOrder(Long orderId) {
		User user = getCurrentUser();
		Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
				.orElseThrow(() -> new ResourceNotFoundException(
						"Order not found with id: " + orderId));

		return toOrderResponse(order);
	}

	@Transactional(readOnly = true)
	public List<OrderResponse> getAllOrdersForAdmin() {
		return orderRepository.findAllByOrderByCreatedAtDesc()
				.stream()
				.map(this::toOrderResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public OrderResponse getOrderByIdForAdmin(Long orderId) {
		Order order = orderRepository.findById(orderId)
				.orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

		return toOrderResponse(order);
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

	private OrderResponse toOrderResponse(Order order) {
		OrderResponse response = new OrderResponse();
		response.setOrderId(order.getId());
		response.setTotalAmount(order.getTotalAmount());
		response.setStatus(order.getStatus());
		response.setCreatedAt(order.getCreatedAt());
		response.setItems(orderItemRepository.findByOrderId(order.getId())
				.stream()
				.map(this::toOrderItemResponse)
				.toList());
		return response;
	}

	private OrderItemResponse toOrderItemResponse(OrderItem orderItem) {
		OrderItemResponse response = new OrderItemResponse();
		response.setId(orderItem.getId());
		response.setProductId(orderItem.getProduct().getId());
		response.setProductName(orderItem.getProductName());
		response.setUnitPrice(orderItem.getUnitPrice());
		response.setQuantity(orderItem.getQuantity());
		response.setSubtotal(orderItem.getSubtotal());
		return response;
	}

	private BigDecimal calculateSubtotal(BigDecimal unitPrice, int quantity) {
		return unitPrice.multiply(BigDecimal.valueOf(quantity));
	}
}
