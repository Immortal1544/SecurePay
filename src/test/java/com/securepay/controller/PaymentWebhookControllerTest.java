package com.securepay.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.securepay.config.SecurityConfig;
import com.securepay.exception.InvalidWebhookException;
import com.securepay.security.JwtAuthenticationFilter;
import com.securepay.service.PaymentService;

import jakarta.servlet.FilterChain;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class PaymentWebhookControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PaymentService paymentService;

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;

	@BeforeEach
	void continueFilterChain() throws Exception {
		doAnswer(invocation -> {
			FilterChain chain = invocation.getArgument(2);
			chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
			return null;
		}).when(jwtAuthenticationFilter).doFilter(
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any());
	}

	@Test
	void missingSignatureIsPermittedToReachHandlerAndReturnsSanitizedBadRequest() throws Exception {
		String payload = "{}";
		doThrow(new InvalidWebhookException("Invalid webhook signature"))
				.when(paymentService).processRazorpayWebhook(anyString(), isNull());

		mockMvc.perform(post("/api/payments/webhook/razorpay")
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid Razorpay webhook request"));
	}

	@Test
	void malformedWebhookReturnsSanitizedBadRequest() throws Exception {
		String payload = "{not-json";
		doThrow(new InvalidWebhookException("Malformed webhook payload", new IllegalArgumentException("details")))
				.when(paymentService).processRazorpayWebhook(anyString(), eq("signed-value"));

		mockMvc.perform(post("/api/payments/webhook/razorpay")
				.header("X-Razorpay-Signature", "signed-value")
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid Razorpay webhook request"));
	}
}
