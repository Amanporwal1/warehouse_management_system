package com.advatix.OMS.repository;

import com.advatix.OMS.domain.Inventory;
import com.advatix.OMS.domain.Product;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProduct(Product product);
}
