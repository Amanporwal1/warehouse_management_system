package com.advatix.OMS.service;

import com.advatix.OMS.domain.CustomerOrder;
import com.advatix.OMS.domain.Lpn;
import com.advatix.OMS.domain.OrderStatus;
import com.advatix.OMS.domain.ShipmentStatus;
import com.advatix.OMS.domain.Truckload;
import com.advatix.OMS.dto.AssignCarrierRequest;
import com.advatix.OMS.dto.AttachLpnsRequest;
import com.advatix.OMS.dto.AttachOrdersRequest;
import com.advatix.OMS.dto.CreateTruckloadRequest;
import com.advatix.OMS.dto.TruckloadResponse;
import com.advatix.OMS.exception.BadRequestException;
import com.advatix.OMS.exception.ResourceNotFoundException;
import com.advatix.OMS.repository.CustomerOrderRepository;
import com.advatix.OMS.repository.LpnRepository;
import com.advatix.OMS.repository.TruckloadRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TruckloadService {

    private final TruckloadRepository truckloadRepository;
    private final LpnRepository lpnRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final LpnService lpnService;
    private final TransactionHistoryService transactionHistoryService;

    @Transactional
    public TruckloadResponse createTruckload(CreateTruckloadRequest request) {
        Truckload truckload = Truckload.builder()
                .truckloadNumber(generateTruckloadNumber())
                .shipmentStatus(ShipmentStatus.CREATED)
                .remarks(trimToNull(request.remarks()))
                .build();
        return toResponse(truckloadRepository.save(truckload), "Truckload created");
    }

    @Transactional
    public TruckloadResponse attachLpns(String truckloadNumber, AttachLpnsRequest request) {
        Truckload truckload = resolveTruckload(truckloadNumber);
        validateTruckloadCanBeModified(truckload);

        for (String lpnNumber : request.lpnNumbers()) {
            Lpn lpn = lpnRepository.findByLpnNumberIgnoreCase(lpnNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("LPN not found: " + lpnNumber));
            lpnService.validateLpnCanBeModified(lpn);
            if (lpn.getCarrierName() == null || lpn.getCarrierName().isBlank()) {
                throw new BadRequestException("Only carrier-assigned LPNs can be attached to Truckload");
            }
            if (lpn.getTruckload() != null) {
                throw new BadRequestException("LPN already belongs to a Truckload: " + lpn.getLpnNumber());
            }
            lpn.setTruckload(truckload);
            truckload.getLpns().add(lpn);
            for (CustomerOrder order : lpn.getOrders()) {
                transactionHistoryService.recordActivity(
                        order,
                        order.getOrderStatusCode(),
                        "Order linked to truckload " + truckload.getTruckloadNumber() + " via LPN " + lpn.getLpnNumber());
            }
        }

        return toResponse(truckload, "LPNs attached to Truckload");
    }

    @Transactional
    public TruckloadResponse attachDirectOrders(String truckloadNumber, AttachOrdersRequest request) {
        Truckload truckload = resolveTruckload(truckloadNumber);
        validateTruckloadCanBeModified(truckload);

        for (String orderNumber : request.orderNumbers()) {
            CustomerOrder order = customerOrderRepository.findByOrderNumberIgnoreCase(orderNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumber));
            validateOrderCanBeAddedDirectly(order);
            order.setDirectTruckload(truckload);
            truckload.getDirectOrders().add(order);
            transactionHistoryService.recordActivity(
                    order,
                    order.getOrderStatusCode(),
                    "Order attached directly to truckload " + truckload.getTruckloadNumber());
        }

        return toResponse(truckload, "Direct orders attached to Truckload");
    }

    @Transactional
    public TruckloadResponse assignCarrier(String truckloadNumber, AssignCarrierRequest request) {
        Truckload truckload = resolveTruckload(truckloadNumber);
        validateTruckloadCanBeModified(truckload);

        String carrier = lpnService.normalizeCarrier(request.carrierName());
        truckload.setCarrierName(carrier);
        truckload.setShipmentStatus(ShipmentStatus.CARRIER_ASSIGNED);

        for (CustomerOrder order : truckload.getDirectOrders()) {
            transactionHistoryService.recordActivity(
                    order,
                    order.getOrderStatusCode(),
                    "Carrier " + carrier + " assigned to truckload " + truckload.getTruckloadNumber());
        }
        for (Lpn lpn : truckload.getLpns()) {
            for (CustomerOrder order : lpn.getOrders()) {
                transactionHistoryService.recordActivity(
                        order,
                        order.getOrderStatusCode(),
                        "Carrier " + carrier + " assigned to truckload " + truckload.getTruckloadNumber());
            }
        }

        return toResponse(truckloadRepository.save(truckload), "Carrier assigned to Truckload");
    }

    @Transactional
    public TruckloadResponse dispatchTruckload(String truckloadNumber) {
        Truckload truckload = resolveTruckload(truckloadNumber);
        validateTruckloadCanBeModified(truckload);

        if (truckload.getCarrierName() == null || truckload.getCarrierName().isBlank()) {
            throw new BadRequestException("Carrier must be assigned before shipment dispatch");
        }
        if (truckload.getLpns().isEmpty() && truckload.getDirectOrders().isEmpty()) {
            throw new BadRequestException("Truckload must contain LPNs or direct orders before dispatch");
        }

        truckload.setShipmentStatus(ShipmentStatus.SHIPPED);
        truckload.setDispatchDate(LocalDateTime.now());

        for (Lpn lpn : truckload.getLpns()) {
            lpn.setShipmentStatus(ShipmentStatus.SHIPPED);
            for (CustomerOrder order : customerOrderRepository.findByLpn(lpn)) {
                markOrderShipped(order);
            }
        }

        for (CustomerOrder order : customerOrderRepository.findByDirectTruckload(truckload)) {
            markOrderShipped(order);
        }

        return toResponse(truckload, "Truckload dispatched and orders marked shipped");
    }

    @Transactional(readOnly = true)
    public TruckloadResponse getTruckload(String truckloadNumber) {
        return toResponse(resolveTruckload(truckloadNumber), "Truckload loaded");
    }

    private Truckload resolveTruckload(String truckloadNumber) {
        return truckloadRepository.findByTruckloadNumberIgnoreCase(truckloadNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Truckload not found: " + truckloadNumber));
    }

    private void validateOrderCanBeAddedDirectly(CustomerOrder order) {
        if (order.getStatus() == OrderStatus.SHIPPED) {
            throw new BadRequestException("Shipped orders cannot be added to Truckload");
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.REJECTED) {
            throw new BadRequestException("Cancelled orders cannot be added to Truckload");
        }
        if (order.getLpn() != null) {
            throw new BadRequestException("Orders inside LPN cannot be attached directly to Truckload");
        }
        if (order.getDirectTruckload() != null) {
            throw new BadRequestException("Order already belongs directly to a Truckload: " + order.getOrderNumber());
        }
    }

    private void validateTruckloadCanBeModified(Truckload truckload) {
        if (truckload.getShipmentStatus() == ShipmentStatus.SHIPPED) {
            throw new BadRequestException("Truckload cannot be modified after dispatch");
        }
    }

    private void markOrderShipped(CustomerOrder order) {
        order.setStatus(OrderStatus.SHIPPED);
        order.setShipmentStatus(OrderStatus.SHIPPED.name());
        order.setOrderStatusCode(OrderStatus.SHIPPED.ordinal() + 1);
        order.setUpdatedAt(LocalDateTime.now());
        transactionHistoryService.recordStatusChange(
                order,
                OrderStatus.SHIPPED,
                "Truckload dispatched and order marked shipped");
    }

    private TruckloadResponse toResponse(Truckload truckload, String message) {
        return new TruckloadResponse(
                truckload.getTruckloadNumber(),
                truckload.getCarrierName(),
                truckload.getDispatchDate(),
                truckload.getShipmentStatus(),
                truckload.getRemarks(),
                truckload.getLpns().stream().map(Lpn::getLpnNumber).toList(),
                truckload.getDirectOrders().stream().map(CustomerOrder::getOrderNumber).toList(),
                message);
    }

    private String generateTruckloadNumber() {
        return "TL-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddhhmmssSSS"));
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
