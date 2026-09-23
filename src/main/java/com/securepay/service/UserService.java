package com.securepay.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.securepay.dto.auth.LoginRequest;
import com.securepay.dto.auth.LoginResponse;
import com.securepay.dto.auth.UserRegistrationRequest;
import com.securepay.dto.auth.UserResponse;
import com.securepay.entity.Role;
import com.securepay.entity.User;
import com.securepay.exception.InvalidCredentialsException;
import com.securepay.exception.ResourceAlreadyExistsException;
import com.securepay.repository.UserRepository;
import com.securepay.security.JwtService;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public UserService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	public UserResponse register(UserRegistrationRequest request) {
		if (userRepository.existsByEmail(request.getEmail())) {
			throw new ResourceAlreadyExistsException(
					"A user with email " + request.getEmail() + " already exists");
		}

		User user = new User(
				request.getName(),
				request.getEmail(),
				passwordEncoder.encode(request.getPassword()),
				Role.USER
		);

		User savedUser = userRepository.save(user);
		return toUserResponse(savedUser);
	}

	public LoginResponse authenticate(LoginRequest request) {
		User user = userRepository.findByEmail(request.getEmail())
				.orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

		if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
			throw new InvalidCredentialsException("Invalid email or password");
		}

		LoginResponse response = new LoginResponse();
		response.setToken(jwtService.generateToken(user));
		response.setUserId(user.getId());
		response.setName(user.getName());
		response.setEmail(user.getEmail());
		response.setRole(user.getRole());
		return response;
	}

	private UserResponse toUserResponse(User user) {
		UserResponse response = new UserResponse();
		response.setId(user.getId());
		response.setName(user.getName());
		response.setEmail(user.getEmail());
		response.setRole(user.getRole());
		response.setCreatedAt(user.getCreatedAt());
		return response;
	}
}