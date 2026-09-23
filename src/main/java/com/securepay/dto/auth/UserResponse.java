package com.securepay.dto.auth;

import java.time.LocalDateTime;

import com.securepay.entity.Role;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserResponse {

	private Long id;
	private String name;
	private String email;
	private Role role;
	private LocalDateTime createdAt;
}