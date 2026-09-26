package com.securepay.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import com.securepay.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Product p where p.id = :productId")
	Optional<Product> findByIdForUpdate(@Param("productId") Long productId);
}
