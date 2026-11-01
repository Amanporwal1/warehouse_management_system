package com.advatix.OMS.repository;

import com.advatix.OMS.domain.CustomerOrder;
import com.advatix.OMS.domain.ShippedData;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShippedDataRepository extends JpaRepository<ShippedData, Long> {

    Optional<ShippedData> findByOrder(CustomerOrder order);
}
