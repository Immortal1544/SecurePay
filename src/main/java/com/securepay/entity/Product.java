package com.securepay.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "products")
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@ToString.Include
	private Long id;

	@Column(nullable = false)
	@ToString.Include
	private String name;

	@Column
	@ToString.Include
	private String description;

	@Column(nullable = false, precision = 19, scale = 2)
	@ToString.Include
	private BigDecimal price;

	@Column(nullable = false)
	@ToString.Include
	private Integer stockQuantity;

	@Column(nullable = false)
	@ToString.Include
	private Boolean active;

	@Column(nullable = false, updatable = false)
	@ToString.Include
	private LocalDateTime createdAt;

	@Column(nullable = false)
	@ToString.Include
	private LocalDateTime updatedAt;

	public Product(
			String name,
			String description,
			BigDecimal price,
			Integer stockQuantity,
			Boolean active) {
		this.name = name;
		this.description = description;
		this.price = price;
		this.stockQuantity = stockQuantity;
		this.active = active;
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