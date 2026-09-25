package com.securepay.controller;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.securepay.dto.payment.PaymentResponse;
import com.securepay.entity.PaymentStatus;
import com.securepay.security.JwtAuthenticationFilter;
import com.securepay.service.PaymentService;

import jakarta.servlet.FilterChain;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class PaymentControllerRefundSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PaymentService paymentService;

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	private String authority;

	@BeforeEach
	void authenticateRequest() throws Exception {
		authority = "ROLE_ADMIN";
		doAnswer(invocation -> {
			SecurityContextHolder.getContext().setAuthentication(
					new UsernamePasswordAuthenticationToken(
							"admin@example.com",
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
	void adminCanInitiateRefund() throws Exception {
		PaymentResponse response = response(PaymentStatus.REFUNDED);
		when(paymentService.refundOrderPayment(42L)).thenReturn(response);

		mockMvc.perform(post("/api/payments/orders/{orderId}/refund", 42L))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REFUNDED"));
	}

	@Test
	void regularUserCannotInitiateRefundOrReadAdminPaymentStatus() throws Exception {
		authority = "ROLE_USER";

		mockMvc.perform(post("/api/payments/orders/{orderId}/refund", 42L))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/payments/admin/orders/{orderId}", 42L))
				.andExpect(status().isForbidden());
	}

	private PaymentResponse response(PaymentStatus status) {
		PaymentResponse response = new PaymentResponse();
		response.setPaymentId(7L);
		response.setOrderId(42L);
		response.setAmount(BigDecimal.TEN);
		response.setStatus(status);
		return response;
	}
}
