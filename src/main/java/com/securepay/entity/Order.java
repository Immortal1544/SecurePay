package com.securepay.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "orders")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@ToString.Include
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false, precision = 19, scale = 2)
	@ToString.Include
	private BigDecimal totalAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	@ToString.Include
	private OrderStatus status;

	/**
	 * Null is treated as reserved for orders created before this column existed;
	 * the previous order creation flow already deducted their inventory.
	 */
	@Column(name = "inventory_reserved")
	private Boolean inventoryReserved = true;

	@Column(length = 120)
	private String recipientName;

	@Column(length = 16)
	private String phoneNumber;

	@Column(length = 255)
	private String addressLine1;

	@Column(length = 255)
	private String addressLine2;

	@Column(length = 100)
	private String city;

	@Column(length = 100)
	private String state;

	@Column(length = 12)
	private String postalCode;

	@Column(length = 100)
	private String country;

	@Column(nullable = false, updatable = false)
	@ToString.Include
	private LocalDateTime createdAt;

	@Column(nullable = false)
	@ToString.Include
	private LocalDateTime updatedAt;

	public Order(User user, BigDecimal totalAmount, OrderStatus status) {
		this.user = user;
		this.totalAmount = totalAmount;
		this.status = status;
	}

	public Order(
			User user,
			BigDecimal totalAmount,
			OrderStatus status,
			String recipientName,
			String phoneNumber,
			String addressLine1,
			String addressLine2,
			String city,
			String state,
			String postalCode,
			String country) {
		this(user, totalAmount, status);
		this.recipientName = recipientName;
		this.phoneNumber = phoneNumber;
		this.addressLine1 = addressLine1;
		this.addressLine2 = addressLine2;
		this.city = city;
		this.state = state;
		this.postalCode = postalCode;
		this.country = country;
	}

	@PrePersist
	protected void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	protected void onUpdate() {
		updatedAt = LocalDateTime.now();
	}
}
