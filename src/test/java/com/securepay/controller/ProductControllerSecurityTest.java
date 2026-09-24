package com.securepay.controller;

import static org.mockito.Mockito.doAnswer;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.securepay.config.SecurityConfig;
import com.securepay.security.JwtAuthenticationFilter;
import com.securepay.service.ProductService;

import jakarta.servlet.FilterChain;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductControllerSecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductService productService;

	@MockitoBean
	private JwtAuthenticationFilter jwtAuthenticationFilter;
	private String authority;

	@BeforeEach
	void authenticateAsRegularUser() throws Exception {
		authority = "ROLE_USER";
		doAnswer(invocation -> {
			SecurityContextHolder.getContext().setAuthentication(
					new UsernamePasswordAuthenticationToken(
							"user@example.com",
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
	void adminCanUpdateProduct() throws Exception {
		authority = "ROLE_ADMIN";
		mockMvc.perform(put("/api/products/{id}", 1L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Updated","price":10.00,"stockQuantity":1,"active":true}
						"""))
				.andExpect(status().isOk());
	}

	@Test
	void regularUserCannotUpdateProduct() throws Exception {
		mockMvc.perform(put("/api/products/{id}", 1L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Updated","price":10.00,"stockQuantity":1,"active":true}
						"""))
				.andExpect(status().isForbidden());
	}

	@Test
	void regularUserCannotDeactivateProduct() throws Exception {
		mockMvc.perform(delete("/api/products/{id}", 1L))
				.andExpect(status().isForbidden());
	}
}
