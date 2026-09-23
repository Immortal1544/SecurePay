package com.securepay.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.securepay.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

	Optional<Order> findByIdAndUserId(Long orderId, Long userId);

	List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
}