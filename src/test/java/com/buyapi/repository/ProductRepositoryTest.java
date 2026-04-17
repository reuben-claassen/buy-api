package com.buyapi.repository;

import com.buyapi.entity.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ProductRepositoryTest {

    @Autowired ProductRepository productRepository;

    private Product save(String name, boolean active) {
        return productRepository.save(Product.builder()
                .name(name).description("desc").price(new BigDecimal("5.00"))
                .stock(10).active(active).build());
    }

    @Test
    void findByActiveTrue_returnsOnlyActiveProducts() {
        save("Active Product", true);
        save("Inactive Product", false);

        Page<Product> result = productRepository.findByActiveTrue(PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Active Product");
    }

    @Test
    void search_byNameSubstring_returnsMatchingProducts() {
        save("Blue Widget", true);
        save("Red Gadget", true);
        save("Blue Gadget", false);

        Page<Product> result = productRepository.search("blue", null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Blue Widget");
    }

    @Test
    void search_noQuery_returnsAllActiveProducts() {
        save("Product A", true);
        save("Product B", true);
        save("Product C", false);

        Page<Product> result = productRepository.search(null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
    }
}
