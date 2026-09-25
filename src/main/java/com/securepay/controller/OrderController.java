package com.securepay.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.securepay.dto.order.CreateOrderRequest;
import com.securepay.dto.order.OrderResponse;
import com.securepay.service.OrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

	private final OrderService orderService;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@PostMapping
	public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request));
	}

	@GetMapping
	public ResponseEntity<List<OrderResponse>> getCurrentUserOrders() {
		return ResponseEntity.ok(orderService.getCurrentUserOrders());
	}

	@GetMapping("/{orderId}")
	public ResponseEntity<OrderResponse> getCurrentUserOrder(@PathVariable Long orderId) {
		return ResponseEntity.ok(orderService.getCurrentUserOrder(orderId));
	}
}
