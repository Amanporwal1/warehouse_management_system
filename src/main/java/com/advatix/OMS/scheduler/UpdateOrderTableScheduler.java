package com.advatix.OMS.scheduler;

import com.advatix.OMS.service.CarrierUpdatesService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UpdateOrderTableScheduler {

    private final CarrierUpdatesService carrierUpdatesService;

    @Scheduled(fixedRate = 300000)
    public void updateOrderTableFromCarrierData() {
        carrierUpdatesService.processPendingCarrierUpdates();
    }
}
