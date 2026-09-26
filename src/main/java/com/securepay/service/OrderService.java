package com.securepay.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.securepay.dto.order.OrderItemResponse;
import com.securepay.dto.order.OrderResponse;
import com.securepay.dto.order.CreateOrderRequest;
import com.securepay.entity.Cart;
import com.securepay.entity.CartItem;
import com.securepay.entity.Order;
import com.securepay.entity.OrderItem;
import com.securepay.entity.OrderStatus;
import com.securepay.entity.Product;
import com.securepay.entity.User;
import com.securepay.exception.InvalidOrderStatusTransitionException;
import com.securepay.exception.OrderConflictException;
import com.securepay.exception.ResourceNotFoundException;
import com.securepay.repository.CartItemRepository;
import com.securepay.repository.CartRepository;
import com.securepay.repository.OrderItemRepository;
import com.securepay.repository.OrderRepository;
import com.securepay.repository.UserRepository;

@Service
public class OrderService {

	private final UserRepository userRepository;
	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final InventoryReservationService inventoryReservationService;

	public OrderService(
			UserRepository userRepository,
			CartRepository cartRepository,
			CartItemRepository cartItemRepository,
			OrderRepository orderRepository,
			OrderItemRepository orderItemRepository,
			InventoryReservationService inventoryReservationService) {
		this.userRepository = userRepository;
		this.cartRepository = cartRepository;
		this.cartItemRepository = cartItemRepository;
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.inventoryReservationService = inventoryReservationService;
	}

	@Transactional
	public OrderResponse createOrder(CreateOrderRequest request) {
		User user = getCurrentUser();
		Cart cart = cartRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Cart not found for current user"));
		List<CartItem> cartItems = cartItemRepository.findByCartId(cart.getId());

		if (cartItems.isEmpty()) {
			throw new OrderConflictException("Cannot create order from an empty cart");
		}

		Map<Long, Product> reservedProducts = inventoryReservationService.reserveCartItems(cartItems);
		BigDecimal totalAmount = BigDecimal.ZERO;
		for (CartItem cartItem : cartItems) {
			Product product = reservedProducts.get(cartItem.getProduct().getId());
			totalAmount = totalAmount.add(calculateSubtotal(product.getPrice(), cartItem.getQuantity()));
		}

		Order order = orderRepository.save(new Order(
				user,
				totalAmount,
				OrderStatus.CREATED,
				normalize(request.getRecipientName()),
				normalize(request.getPhoneNumber()),
				normalize(request.getAddressLine1()),
				normalizeOptional(request.getAddressLine2()),
				normalize(request.getCity()),
				normalize(request.getState()),
				normalize(request.getPostalCode()),
				normalize(request.getCountry())));
		for (CartItem cartItem : cartItems) {
			Product product = reservedProducts.get(cartItem.getProduct().getId());
			int quantity = cartItem.getQuantity();
			BigDecimal subtotal = calculateSubtotal(product.getPrice(), quantity);

			orderItemRepository.save(new OrderItem(
					order,
					product,
					product.getName(),
					product.getPrice(),
					quantity,
					subtotal));

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

	@Transactional
	public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
		Order order = orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

		if (!isAllowedStatusTransition(order.getStatus(), newStatus)) {
			throw new InvalidOrderStatusTransitionException();
		}

		if (newStatus == OrderStatus.CANCELLED && order.getStatus() != OrderStatus.PROCESSING) {
			inventoryReservationService.releaseReservedInventory(order);
		}
		order.setStatus(newStatus);
		return toOrderResponse(orderRepository.save(order));
	}

	private boolean isAllowedStatusTransition(OrderStatus currentStatus, OrderStatus newStatus) {
		if (newStatus == null) {
			return false;
		}

		return switch (currentStatus) {
		case CREATED -> newStatus == OrderStatus.PAYMENT_PENDING || newStatus == OrderStatus.CANCELLED;
		case PAYMENT_PENDING -> newStatus == OrderStatus.CANCELLED;
		case PAID -> newStatus == OrderStatus.PROCESSING || newStatus == OrderStatus.CANCELLED;
		case PROCESSING -> newStatus == OrderStatus.SHIPPED || newStatus == OrderStatus.CANCELLED;
		case SHIPPED -> newStatus == OrderStatus.DELIVERED;
		case DELIVERED, CANCELLED -> false;
		};
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
		response.setRecipientName(order.getRecipientName());
		response.setPhoneNumber(order.getPhoneNumber());
		response.setAddressLine1(order.getAddressLine1());
		response.setAddressLine2(order.getAddressLine2());
		response.setCity(order.getCity());
		response.setState(order.getState());
		response.setPostalCode(order.getPostalCode());
		response.setCountry(order.getCountry());
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

	private String normalize(String value) {
		return value.trim();
	}

	private String normalizeOptional(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
