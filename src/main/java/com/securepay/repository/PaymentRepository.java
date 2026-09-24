package com.securepay.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.securepay.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	Optional<Payment> findByOrderId(Long orderId);

	Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Payment p where p.razorpayOrderId = :razorpayOrderId")
	Optional<Payment> findByRazorpayOrderIdForUpdate(@Param("razorpayOrderId") String razorpayOrderId);

	Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);
}
