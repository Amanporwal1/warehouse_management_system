package com.advatix.OMS.repository;

import com.advatix.OMS.domain.CustomerOrder;
import com.advatix.OMS.domain.Inventory;
import com.advatix.OMS.domain.InventoryUnit;
import com.advatix.OMS.domain.InventoryUnitStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryUnitRepository extends JpaRepository<InventoryUnit, Long> {

    List<InventoryUnit> findByInventoryAndStatusOrderByIdAsc(Inventory inventory, InventoryUnitStatus status);

    List<InventoryUnit> findByAllocatedOrderAndStatus(CustomerOrder allocatedOrder, InventoryUnitStatus status);

    long countBySerialNumberStartingWithIgnoreCase(String serialNumberPrefix);
}
