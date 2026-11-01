package com.advatix.OMS.service;

import com.advatix.OMS.domain.CustomerOrder;
import com.advatix.OMS.domain.OrderStatus;
import com.advatix.OMS.domain.TransactionHistory;
import com.advatix.OMS.dto.OrderLatestStatusResponse;
import com.advatix.OMS.dto.TransactionHistoryResponse;
import com.advatix.OMS.exception.ResourceNotFoundException;
import com.advatix.OMS.repository.TransactionHistoryRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionHistoryService {

    private static final int FIRST_CARRIER_STATUS_CODE = OrderStatus.IN_TRANSIT.ordinal() + 1;
    private static final int LAST_CARRIER_STATUS_CODE = OrderStatus.DELIVERED.ordinal() + 1;

    private final TransactionHistoryRepository transactionHistoryRepository;

    @Transactional
    public TransactionHistory recordStatusChange(
            CustomerOrder order,
            OrderStatus status,
            String description
    ) {
        TransactionHistory transactionHistory = TransactionHistory.builder()
                .order(order)
                .orderNumber(order.getOrderNumber())
                .statusCode(status.ordinal() + 1)
                .description(description)
                .build();

        return transactionHistoryRepository.save(transactionHistory);
    }

    @Transactional
    public TransactionHistory recordActivity(
            CustomerOrder order,
            Integer statusCode,
            String description
    ) {
        TransactionHistory transactionHistory = TransactionHistory.builder()
                .order(order)
                .orderNumber(order.getOrderNumber())
                .statusCode(statusCode == null ? OrderStatus.CREATED.ordinal() + 1 : statusCode)
                .description(description)
                .build();

        return transactionHistoryRepository.save(transactionHistory);
    }

    @Transactional
    public void recordCarrierStatusProgress(
            CustomerOrder order,
            Integer statusCode,
            String shipmentId
    ) {
        if (!isCarrierStatusCode(statusCode)) {
            recordActivity(order, statusCode, "Carrier webhook update received for shipment " + shipmentId);
            return;
        }

        for (int carrierStatusCode : IntStream.rangeClosed(FIRST_CARRIER_STATUS_CODE, statusCode).toArray()) {
            if (transactionHistoryRepository.existsByOrderOrderNumberIgnoreCaseAndStatusCode(
                    order.getOrderNumber(), carrierStatusCode)) {
                continue;
            }

            String statusLabel = resolveStatus(carrierStatusCode);
            String description = carrierStatusCode == statusCode
                    ? "Carrier webhook update received for shipment " + shipmentId + ": " + statusLabel
                    : "Carrier status backfilled for shipment " + shipmentId + ": " + statusLabel;
            recordActivity(order, carrierStatusCode, description);
        }
    }

    @Transactional(readOnly = true)
    public List<TransactionHistoryResponse> getHistoryByOrderNumber(String orderNumber) {
        return deduplicateByStatus(
                transactionHistoryRepository.findByOrderOrderNumberIgnoreCaseOrderByPerformedAtDesc(orderNumber),
                true)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionHistoryResponse> getTimelineByOrderNumber(String orderNumber) {
        return deduplicateByStatus(
                transactionHistoryRepository.findByOrderOrderNumberIgnoreCaseOrderByPerformedAtAsc(orderNumber),
                false)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderLatestStatusResponse getLatestStatus(String orderNumber) {
        TransactionHistory latestTransaction = transactionHistoryRepository
                .findFirstByOrderOrderNumberIgnoreCaseOrderByPerformedAtDesc(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction history not found for order: " + orderNumber));

        return new OrderLatestStatusResponse(
                latestTransaction.getOrderNumber(),
                resolveStatus(latestTransaction.getStatusCode()),
                latestTransaction.getStatusCode(),
                latestTransaction.getDescription(),
                latestTransaction.getPerformedAt());
    }

    private TransactionHistoryResponse toResponse(TransactionHistory transactionHistory) {
        return new TransactionHistoryResponse(
                transactionHistory.getPerformedAt(),
                resolveStatus(transactionHistory.getStatusCode()));
    }

    private String resolveStatus(Integer statusCode) {
        if (statusCode == null || statusCode <= 0 || statusCode > OrderStatus.values().length) {
            return "UNKNOWN";
        }
        return OrderStatus.values()[statusCode - 1].name();
    }

    public boolean isCarrierStatusCode(Integer statusCode) {
        return statusCode != null
                && statusCode >= FIRST_CARRIER_STATUS_CODE
                && statusCode <= LAST_CARRIER_STATUS_CODE;
    }

    private List<TransactionHistory> deduplicateByStatus(List<TransactionHistory> histories, boolean newestFirst) {
        Map<Integer, TransactionHistory> uniqueStatuses = new LinkedHashMap<>();
        for (TransactionHistory history : histories) {
            Integer statusCode = history.getStatusCode();
            if (newestFirst) {
                uniqueStatuses.putIfAbsent(statusCode, history);
                continue;
            }

            if (uniqueStatuses.containsKey(statusCode)) {
                uniqueStatuses.remove(statusCode);
            }
            uniqueStatuses.put(statusCode, history);
        }
        return List.copyOf(uniqueStatuses.values());
    }
}
