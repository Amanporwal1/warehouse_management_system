package com.advatix.OMS.repository;

import com.advatix.OMS.domain.CarrierSentData;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarrierRepository extends JpaRepository<CarrierSentData, Long> {

    List<CarrierSentData> findByWebhookOrderByStatusTimeAsc(Integer webhook);

    long countByWebhook(Integer webhook);
}
