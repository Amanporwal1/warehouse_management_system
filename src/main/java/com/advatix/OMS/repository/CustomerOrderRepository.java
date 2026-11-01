package com.advatix.OMS.repository;

import com.advatix.OMS.domain.CustomerOrder;
import com.advatix.OMS.domain.Lpn;
import com.advatix.OMS.domain.OrderStatus;
import com.advatix.OMS.domain.Truckload;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    Optional<CustomerOrder> findByOrderNumberIgnoreCase(String orderNumber);

    List<CustomerOrder> findByStatusOrderByCreatedAtAsc(OrderStatus status);

    List<CustomerOrder> findAllByOrderByCreatedAtAsc();

    List<CustomerOrder> findByLpn(Lpn lpn);

    List<CustomerOrder> findByDirectTruckload(Truckload truckload);

    Optional<CustomerOrder> findByOrderNumber(String orderNumber);

    Optional<CustomerOrder> findByShipmentIdIgnoreCase(String shipmentId);

    Optional<CustomerOrder> findByTrackingNumberIgnoreCase(String trackingNumber);
}
