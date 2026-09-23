package com.securepay.dto.auth;

import com.securepay.entity.Role;

import lombok.Data;

import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class LoginResponse {

	private String token;
	private Long userId;
	private String name;
	private String email;
	private Role role;
}