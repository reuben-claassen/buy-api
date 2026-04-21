package com.buyapi.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.buyapi.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByCategoryIdAndActiveTrue(Long categoryId, Pageable pageable);

    @Query("""
            SELECT p FROM Product p
            WHERE p.active = true
              AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
            """)
    Page<Product> searchWithQuery(@Param("search") String search,
                                  @Param("categoryId") Long categoryId,
                                  Pageable pageable);

    @Query("""
            SELECT p FROM Product p
            WHERE p.active = true
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
            """)
    Page<Product> searchWithoutQuery(@Param("categoryId") Long categoryId,
                                     Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.seller.email = :email")
    Optional<Product> findByIdAndSellerEmail(@Param("id") Long id, @Param("email") String email);

    Page<Product> findBySellerEmailAndActiveTrue(String email, Pageable pageable);
}
