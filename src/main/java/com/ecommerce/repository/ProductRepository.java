package com.ecommerce.repository;

import com.ecommerce.entity.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);

    /** Loads the product with images/variants/ attributes in one shot for the edit page. */
    @EntityGraph(attributePaths = {"images", "variants", "attributes", "category"})
    Optional<Product> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"images", "category"})
    List<Product> findAllByOrderByIdDesc();
}
