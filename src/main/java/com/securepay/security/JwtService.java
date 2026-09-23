package com.securepay.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.securepay.entity.User;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

	private final String jwtSecret;
	private final long jwtExpiration;

	public JwtService(
			@Value("${jwt.secret}") String jwtSecret,
			@Value("${jwt.expiration}") long jwtExpiration) {
		this.jwtSecret = jwtSecret;
		this.jwtExpiration = jwtExpiration;
	}

	public String generateToken(User user) {
		long issuedAt = System.currentTimeMillis();
		Date issuedAtDate = new Date(issuedAt);
		Date expirationDate = new Date(issuedAt + jwtExpiration);

		return Jwts.builder()
				.subject(user.getEmail())
				.claim("userId", user.getId())
				.claim("role", user.getRole().name())
				.issuedAt(issuedAtDate)
				.expiration(expirationDate)
				.signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
				.compact();
	}

	public String extractUsername(String token) {
		return parseClaims(token).getSubject();
	}

	public boolean isTokenValid(String token, String username) {
		try {
			return username.equals(extractUsername(token));
		} catch (io.jsonwebtoken.JwtException | IllegalArgumentException exception) {
			return false;
		}
	}

	private io.jsonwebtoken.Claims parseClaims(String token) {
		return Jwts.parser()
				.verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}
}