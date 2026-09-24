package com.securepay.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.securepay.dto.order.OrderResponse;
import com.securepay.dto.order.UpdateOrderStatusRequest;
import com.securepay.service.OrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

	private final OrderService orderService;

	public AdminOrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@GetMapping
	public ResponseEntity<List<OrderResponse>> getAllOrders() {
		return ResponseEntity.ok(orderService.getAllOrdersForAdmin());
	}

	@GetMapping("/{orderId}")
	public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long orderId) {
		return ResponseEntity.ok(orderService.getOrderByIdForAdmin(orderId));
	}

	@PutMapping("/{orderId}/status")
	public ResponseEntity<OrderResponse> updateOrderStatus(
			@PathVariable Long orderId,
			@Valid @RequestBody UpdateOrderStatusRequest request) {
		return ResponseEntity.ok(orderService.updateOrderStatus(orderId, request.getStatus()));
	}
}
