package com.securepay.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.securepay.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
}