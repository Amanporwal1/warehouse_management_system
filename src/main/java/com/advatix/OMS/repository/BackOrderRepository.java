package com.advatix.OMS.repository;

import com.advatix.OMS.domain.BackOrder;
import com.advatix.OMS.domain.CustomerOrder;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackOrderRepository extends JpaRepository<BackOrder, Long> {

    List<BackOrder> findByOrderOrderByCreatedAtAsc(CustomerOrder order);
}
