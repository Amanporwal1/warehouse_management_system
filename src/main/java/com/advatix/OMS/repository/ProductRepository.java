package com.advatix.OMS.repository;

import com.advatix.OMS.domain.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findBySkuIgnoreCaseOrderByIdAsc(String sku);
}
