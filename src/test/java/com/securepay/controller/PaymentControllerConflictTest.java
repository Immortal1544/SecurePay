package com.securepay.controller;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.securepay.exception.InvalidPaymentStateException;
import com.securepay.security.JwtAuthenticationFilter;
import com.securepay.service.PaymentService;

import jakarta.servlet.FilterChain;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class PaymentControllerConflictTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PaymentService paymentService;

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@BeforeEach
	void authenticateRequest() throws Exception {
		doAnswer(invocation -> {
			SecurityContextHolder.getContext().setAuthentication(
					new UsernamePasswordAuthenticationToken(
							"customer@example.com",
							null,
							List.of(new SimpleGrantedAuthority("ROLE_USER"))));
			FilterChain chain = invocation.getArgument(2);
			chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
			return null;
		}).when(jwtAuthenticationFilter).doFilter(
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any());
	}

	@Test
	void ineligibleOrderPaymentCreationReturnsConflict() throws Exception {
		when(paymentService.createPayment(42L)).thenThrow(new InvalidPaymentStateException());

		mockMvc.perform(post("/api/payments/orders/{orderId}", 42L))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message")
						.value("Payment is not allowed for the current order status"));
	}
}
