package com.securepay.controller;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.securepay.config.SecurityConfig;
import com.securepay.dto.order.OrderResponse;
import com.securepay.entity.OrderStatus;
import com.securepay.exception.ResourceNotFoundException;
import com.securepay.security.JwtAuthenticationFilter;
import com.securepay.service.OrderService;

import jakarta.servlet.FilterChain;

@WebMvcTest(AdminOrderController.class)
@Import(SecurityConfig.class)
class AdminOrderControllerSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OrderService orderService;

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	private String authority;

	@BeforeEach
	void authenticateRequest() throws Exception {
		authority = "ROLE_ADMIN";
		doAnswer(invocation -> {
			SecurityContextHolder.getContext().setAuthentication(
					new UsernamePasswordAuthenticationToken(
						"test@example.com",
						null,
							List.of(new SimpleGrantedAuthority(authority))));
			FilterChain chain = invocation.getArgument(2);
			chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
			return null;
		}).when(jwtAuthenticationFilter).doFilter(
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any());
	}

	@Test
	void adminCanRetrieveAllOrders() throws Exception {
		when(orderService.getAllOrdersForAdmin()).thenReturn(List.of(orderResponse(2L), orderResponse(1L)));

		mockMvc.perform(get("/api/admin/orders"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].orderId").value(2));
	}

	@Test
	void adminCanRetrieveAnOrder() throws Exception {
		when(orderService.getOrderByIdForAdmin(42L)).thenReturn(orderResponse(42L));

		mockMvc.perform(get("/api/admin/orders/{orderId}", 42L))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.orderId").value(42));
	}

	@Test
	void missingAdminOrderReturnsNotFound() throws Exception {
		when(orderService.getOrderByIdForAdmin(99L))
				.thenThrow(new ResourceNotFoundException("Order not found with id: 99"));

		mockMvc.perform(get("/api/admin/orders/{orderId}", 99L))
				.andExpect(status().isNotFound());
	}

	@Test
	void regularUserCannotRetrieveAllOrdersOrAnIndividualOrder() throws Exception {
		authority = "ROLE_USER";

		mockMvc.perform(get("/api/admin/orders"))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/orders/{orderId}", 42L))
				.andExpect(status().isForbidden());
	}

	private OrderResponse orderResponse(Long orderId) {
		OrderResponse response = new OrderResponse();
		response.setOrderId(orderId);
		response.setTotalAmount(BigDecimal.TEN);
		response.setStatus(OrderStatus.CREATED);
		response.setItems(List.of());
		return response;
	}
}
