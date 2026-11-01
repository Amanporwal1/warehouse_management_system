package com.advatix.OMS.service;

import com.advatix.OMS.domain.CarrierSentData;
import com.advatix.OMS.domain.CustomerOrder;
import com.advatix.OMS.domain.OrderStatus;
import com.advatix.OMS.dto.CarrierDataRequest;
import com.advatix.OMS.dto.CarrierDataResponse;
import com.advatix.OMS.dto.CarrierSyncResponse;
import com.advatix.OMS.repository.CarrierRepository;
import com.advatix.OMS.repository.CarrierStatusMappingRepository;
import com.advatix.OMS.repository.CustomerOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
@Slf4j
public class CarrierUpdatesService {

    private final CarrierRepository carrierRepository;
    private final CarrierStatusMappingRepository carrierStatusMappingRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final TransactionHistoryService transactionHistoryService;

    @Transactional
    public CarrierDataResponse createCarrierStatus(CarrierDataRequest request) {
        CarrierSentData carrierSentData = saveCarrierData(request);
        return toResponse(carrierRepository.save(carrierSentData), "Carrier data inserted");
    }

    @Transactional
    public CarrierSyncResponse createCarrierStatusAndUpdateOrder() {
        return syncPendingCarrierUpdatesOnDemand();
    }

    @Transactional
    public void processPendingCarrierUpdates() {
        processPendingCarrierUpdatesInternal();
    }

    private CarrierSyncResponse processCarrierUpdate(CarrierSentData carrierUpdate) {
        try {
            Integer statusCode = resolveCarrierStatusCode(carrierUpdate.getCarrierStatus());

            if (statusCode == null || !carrierStatusMappingRepository.existsByStatusCode(statusCode)) {
                log.warn("Skipping carrier update {} because carrier status '{}' is not mapped",
                        carrierUpdate.getId(), carrierUpdate.getCarrierStatus());
                return toSyncResponse(carrierUpdate, false, "Carrier status is not mapped");
            }

            Optional<CustomerOrder> optionalOrder = customerOrderRepository
                    .findByShipmentIdIgnoreCase(carrierUpdate.getShipmentId())
                    .or(() -> customerOrderRepository.findByTrackingNumberIgnoreCase(carrierUpdate.getShipmentId()))
                    .or(() -> customerOrderRepository.findByOrderNumberIgnoreCase(carrierUpdate.getShipmentId()));

            if (optionalOrder.isEmpty()) {
                log.warn("Skipping carrier update {} because no order was found for shipment id '{}'",
                        carrierUpdate.getId(), carrierUpdate.getShipmentId());
                return toSyncResponse(carrierUpdate, false, "Order was not found");
            }

            CustomerOrder order = optionalOrder.get();
            boolean shouldAdvanceOrderStatus = shouldAdvanceCarrierStatus(order, statusCode);
            if (shouldAdvanceOrderStatus) {
                order.setShipmentStatus(resolveCarrierStatusLabel(statusCode));
                order.setOrderStatusCode(statusCode);
                order.setUpdatedAt(LocalDateTime.now());
                updateOrderStatusIfMappedStatusExists(order, statusCode);
            } else {
                log.info("Skipping carrier status regression for order {} from {} to {}",
                        order.getOrderNumber(), order.getOrderStatusCode(), statusCode);
            }

            carrierUpdate.setWebhook(1);
            customerOrderRepository.save(order);
            carrierRepository.save(carrierUpdate);
            transactionHistoryService.recordCarrierStatusProgress(
                    order,
                    statusCode,
                    carrierUpdate.getShipmentId());

            log.info("Updated order {} from carrier update {} using status {} ({})",
                    order.getOrderNumber(), carrierUpdate.getId(), resolveCarrierStatusLabel(statusCode), statusCode);
            return toSyncResponse(carrierUpdate, true, "Order status updated");
        } catch (Exception ex) {
            log.error("Error processing carrier update {}", carrierUpdate.getId(), ex);
            return toSyncResponse(carrierUpdate, false, "Error while updating order");
        }
    }

    public CarrierDataResponse toResponse(CarrierSentData carrierSentData, String message) {

        return new CarrierDataResponse(
                carrierSentData.getShipmentId(),
                carrierSentData.getCarrierStatus(),
                message
        );
    }

    private CarrierSentData saveCarrierData(CarrierDataRequest request) {
        LocalDateTime now = LocalDateTime.now();
        CarrierSentData carrierSentData = CarrierSentData.builder()
                .shipmentId(request.shipmentId().trim())
                .carrierStatus(request.carrierStatus().trim())
                .statusTime(now)
                .webhook(0)
                .build();
        return carrierRepository.save(carrierSentData);
    }

    private void updateOrderStatusIfMappedStatusExists(CustomerOrder order, Integer statusCode) {
        try {
            if (statusCode == null || statusCode <= 0 || statusCode > OrderStatus.values().length) {
                throw new IllegalArgumentException("Unsupported status code: " + statusCode);
            }
            order.setStatus(OrderStatus.values()[statusCode - 1]);
        } catch (IllegalArgumentException ex) {
            log.warn("Carrier status code '{}' has no matching OrderStatus enum; only status code was updated", statusCode);
        }
    }

    private boolean shouldAdvanceCarrierStatus(CustomerOrder order, Integer incomingStatusCode) {
        Integer currentStatusCode = order.getOrderStatusCode();
        if (!transactionHistoryService.isCarrierStatusCode(incomingStatusCode)) {
            return true;
        }
        if (!transactionHistoryService.isCarrierStatusCode(currentStatusCode)) {
            return true;
        }
        return incomingStatusCode >= currentStatusCode;
    }

    private Integer resolveCarrierStatusCode(String carrierStatus) {
        if (carrierStatus == null) {
            return null;
        }
        return switch (carrierStatus.trim().toUpperCase()) {
            case "IN_TRANSIT" -> 10;
            case "OUT_FOR_DELIVERY" -> 11;
            case "DELIVERED" -> 12;
            default -> null;
        };
    }

    private String resolveCarrierStatusLabel(Integer statusCode) {
        if (statusCode == null) {
            return null;
        }
        return switch (statusCode) {
            case 10 -> "IN_TRANSIT";
            case 11 -> "OUT_FOR_DELIVERY";
            case 12 -> "DELIVERED";
            default -> null;
        };
    }

    private CarrierSyncResponse toSyncResponse(CarrierSentData carrierUpdate, boolean orderUpdated, String message) {
        return new CarrierSyncResponse(
                carrierUpdate.getId(),
                carrierUpdate.getShipmentId(),
                carrierUpdate.getCarrierStatus(),
                orderUpdated ? 1 : carrierUpdate.getWebhook(),
                message);
    }

    private CarrierSyncResponse syncPendingCarrierUpdatesOnDemand() {
        PendingCarrierUpdateSummary summary = processPendingCarrierUpdatesInternal();
        if (summary.totalPending() == 0) {
            return new CarrierSyncResponse(null, null, null, 0, "No pending carrier updates found");
        }

        String message = "Processed %d pending carrier update(s): %d updated, %d still pending"
                .formatted(summary.totalPending(), summary.updatedCount(), summary.remainingPending());
        return new CarrierSyncResponse(null, null, null, summary.updatedCount() > 0 ? 1 : 0, message);
    }

    private PendingCarrierUpdateSummary processPendingCarrierUpdatesInternal() {
        List<CarrierSentData> carrierUpdates = carrierRepository.findByWebhookOrderByStatusTimeAsc(0);
        if (carrierUpdates.isEmpty()) {
            return new PendingCarrierUpdateSummary(0, 0, 0);
        }

        log.info("Processing {} carrier update(s)", carrierUpdates.size());
        int updatedCount = 0;
        for (CarrierSentData carrierUpdate : carrierUpdates) {
            CarrierSyncResponse response = processCarrierUpdate(carrierUpdate);
            if (Integer.valueOf(1).equals(response.webhook())) {
                updatedCount++;
            }
        }
        long remainingPending = carrierRepository.countByWebhook(0);
        return new PendingCarrierUpdateSummary(carrierUpdates.size(), updatedCount, (int) remainingPending);
    }
    private record PendingCarrierUpdateSummary(int totalPending, int updatedCount, int remainingPending) {
    }
}
