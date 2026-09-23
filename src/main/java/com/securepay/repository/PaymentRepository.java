package com.securepay.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.securepay.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	Optional<Payment> findByOrderId(Long orderId);

	Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

	Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);
}