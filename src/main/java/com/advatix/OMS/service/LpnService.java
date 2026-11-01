package com.advatix.OMS.service;

import com.advatix.OMS.domain.CustomerOrder;
import com.advatix.OMS.domain.Lpn;
import com.advatix.OMS.domain.OrderItem;
import com.advatix.OMS.domain.OrderStatus;
import com.advatix.OMS.domain.ShipmentStatus;
import com.advatix.OMS.dto.AssignCarrierRequest;
import com.advatix.OMS.dto.AttachOrdersRequest;
import com.advatix.OMS.dto.CreateLpnRequest;
import com.advatix.OMS.dto.LpnResponse;
import com.advatix.OMS.exception.BadRequestException;
import com.advatix.OMS.exception.ResourceNotFoundException;
import com.advatix.OMS.repository.CustomerOrderRepository;
import com.advatix.OMS.repository.LpnRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LpnService {

    private final LpnRepository lpnRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final LpnNumberGenerator lpnNumberGenerator;
    private final TransactionHistoryService transactionHistoryService;

    @Transactional
    public LpnResponse createLpn(CreateLpnRequest request) {
        Lpn lpn = Lpn.builder()
                .lpnNumber(lpnNumberGenerator.generate())
                .createdAt(LocalDateTime.now())
                .createdBy(request.createdBy().trim())
                .shipmentStatus(ShipmentStatus.CREATED)
                .totalOrders(0)
                .totalQuantity(0)
                .totalWeight(BigDecimal.ZERO)
                .remarks(trimToNull(request.remarks()))
                .build();

        return toResponse(lpnRepository.save(lpn), "LPN created");
    }

    @Transactional
    public LpnResponse attachOrders(String lpnNumber, AttachOrdersRequest request) {
        Lpn lpn = resolveLpn(lpnNumber);
        validateLpnCanBeModified(lpn);

        for (String orderNumber : request.orderNumbers()) {
            CustomerOrder order = resolveOrder(orderNumber);
            validateOrderCanBeAddedToLpn(order);
            order.setLpn(lpn);
            lpn.getOrders().add(order);
            transactionHistoryService.recordActivity(
                    order,
                    order.getOrderStatusCode(),
                    "Order attached to LPN " + lpn.getLpnNumber());
        }

        recalculateTotals(lpn);
        return toResponse(lpn, "Orders attached to LPN");
    }

    @Transactional
    public LpnResponse assignCarrier(String lpnNumber, AssignCarrierRequest request) {
        Lpn lpn = resolveLpn(lpnNumber);
        validateLpnCanBeModified(lpn);

        String carrier = normalizeCarrier(request.carrierName());
        lpn.setCarrierName(carrier);
        lpn.setShipmentStatus(ShipmentStatus.CARRIER_ASSIGNED);

        for (CustomerOrder order : lpn.getOrders()) {
            transactionHistoryService.recordActivity(
                    order,
                    order.getOrderStatusCode(),
                    "Carrier " + carrier + " assigned to LPN " + lpn.getLpnNumber());
        }

        return toResponse(lpn, "Carrier assigned to LPN");
    }

    @Transactional(readOnly = true)
    public LpnResponse getLpn(String lpnNumber) {
        return toResponse(resolveLpn(lpnNumber), "LPN loaded");
    }

    private Lpn resolveLpn(String lpnNumber) {
        return lpnRepository.findByLpnNumberIgnoreCase(lpnNumber)
                .orElseThrow(() -> new ResourceNotFoundException("LPN not found: " + lpnNumber));
    }

    private CustomerOrder resolveOrder(String orderNumber) {
        return customerOrderRepository.findByOrderNumberIgnoreCase(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumber));
    }

    void validateOrderCanBeAddedToLpn(CustomerOrder order) {
        if (order.getStatus() == OrderStatus.SHIPPED) {
            throw new BadRequestException("Shipped orders cannot be added to LPN");
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.REJECTED) {
            throw new BadRequestException("Cancelled orders cannot be added to LPN");
        }
        if (order.getDirectTruckload() != null) {
            throw new BadRequestException("Orders already attached directly to Truckload cannot be added to LPN");
        }
        if (order.getLpn() != null) {
            throw new BadRequestException("Order already belongs to an LPN: " + order.getOrderNumber());
        }
    }

    String normalizeCarrier(String carrierName) {
        String normalized = carrierName == null ? "" : carrierName.trim();
        List<String> allowedCarriers = List.of("Delhivery", "Blue Dart", "FedEx");
        return allowedCarriers.stream()
                .filter(carrier -> carrier.equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Carrier must be one of: Delhivery, Blue Dart, FedEx"));
    }

    void validateCarrier(String carrierName) {
        if (normalizeCarrier(carrierName).isBlank()) {
            throw new BadRequestException("Carrier must be one of: Delhivery, Blue Dart, FedEx");
        }
    }

    void validateLpnCanBeModified(Lpn lpn) {
        if (lpn.getShipmentStatus() == ShipmentStatus.SHIPPED) {
            throw new BadRequestException("LPN cannot be modified after dispatch");
        }
    }

    void recalculateTotals(Lpn lpn) {
        int totalOrders = lpn.getOrders().size();
        int totalQuantity = lpn.getOrders().stream()
                .flatMap(order -> order.getItems().stream())
                .mapToInt(OrderItem::getQuantity)
                .sum();
        BigDecimal totalWeight = lpn.getOrders().stream()
                .map(this::orderWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        lpn.setTotalOrders(totalOrders);
        lpn.setTotalQuantity(totalQuantity);
        lpn.setTotalWeight(totalWeight);
    }

    private BigDecimal orderWeight(CustomerOrder order) {
        if (order.getPackageWeight() != null) {
            return order.getPackageWeight();
        }
        return order.getItems().stream()
                .map(item -> item.getProduct().getWeight().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LpnResponse toResponse(Lpn lpn, String message) {
        return new LpnResponse(
                lpn.getLpnNumber(),
                lpn.getCreatedAt(),
                lpn.getCreatedBy(),
                lpn.getCarrierName(),
                lpn.getShipmentStatus(),
                lpn.getTotalOrders(),
                lpn.getTotalQuantity(),
                lpn.getTotalWeight(),
                lpn.getRemarks(),
                lpn.getTruckload() == null ? null : lpn.getTruckload().getTruckloadNumber(),
                lpn.getOrders().stream().map(CustomerOrder::getOrderNumber).toList(),
                message);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
