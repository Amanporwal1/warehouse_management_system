package com.advatix.OMS.service;

import com.advatix.OMS.domain.BackOrder;
import com.advatix.OMS.domain.CarrierSentData;
import com.advatix.OMS.domain.CustomerOrder;
import com.advatix.OMS.domain.Inventory;
import com.advatix.OMS.domain.InventoryUnit;
import com.advatix.OMS.domain.InventoryUnitStatus;
import com.advatix.OMS.domain.OrderItem;
import com.advatix.OMS.domain.OrderStatus;
import com.advatix.OMS.domain.Product;
import com.advatix.OMS.domain.ShippingAddress;
import com.advatix.OMS.domain.ShippedData;
import com.advatix.OMS.dto.OrderItemRequest;
import com.advatix.OMS.dto.OrderItemResponse;
import com.advatix.OMS.dto.PickOrderRequest;
import com.advatix.OMS.dto.PickOrderResponse;
import com.advatix.OMS.dto.PickedInventoryResponse;
import com.advatix.OMS.dto.PackOrderRequest;
import com.advatix.OMS.dto.PackOrderResponse;
import com.advatix.OMS.dto.OrderRequest;
import com.advatix.OMS.dto.OrderResponse;
import com.advatix.OMS.dto.OrderTransactionResponse;
import com.advatix.OMS.dto.ShippingLabelResponse;
import com.advatix.OMS.dto.ShippingOrderTableResponse;
import com.advatix.OMS.exception.BadRequestException;
import com.advatix.OMS.exception.ResourceNotFoundException;
import com.advatix.OMS.repository.BackOrderRepository;
import com.advatix.OMS.repository.CarrierSentDataRepository;
import com.advatix.OMS.repository.CustomerOrderRepository;
import com.advatix.OMS.repository.InventoryRepository;
import com.advatix.OMS.repository.InventoryUnitRepository;
import com.advatix.OMS.repository.ProductRepository;
import com.advatix.OMS.repository.ShippedDataRepository;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String DEFAULT_CARRIER = "OMS EXPRESS";
    private static final String DEFAULT_SERVICE_LEVEL = "GROUND";
    private static final String SHIP_FROM_ADDRESS = "OMS Fulfillment Center, WH-A1, Bengaluru, Karnataka 560001, India";
    private static final String LABEL_IMAGE_CONTENT_TYPE = "image/png";

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryUnitRepository inventoryUnitRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final BackOrderRepository backOrderRepository;
    private final ShippedDataRepository shippedDataRepository;
    private final CarrierSentDataRepository carrierSentDataRepository;
    private final AddressValidationService addressValidationService;
    private final TransactionHistoryService transactionHistoryService;

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        if (!addressValidationService.isServiceable(request.shippingAddress())) {
            throw new BadRequestException("Address validation failed: shipping address is not serviceable");
        }

        List<OrderItem> itemsToPersist = new ArrayList<>();
        List<OrderItemResponse> itemResponses = new ArrayList<>();
        List<BackOrder> backOrdersToPersist = new ArrayList<>();
        List<InventoryUnit> unitsToAllocate = new ArrayList<>();
        boolean hasBackorder = false;

        for (OrderItemRequest itemRequest : request.items()) {
            Product product = resolveProductForOrder(itemRequest.sku(), itemRequest.quantity());

            Inventory inventory = inventoryRepository.findByProduct(product)
                    .orElseThrow(() -> new BadRequestException("Inventory not found for SKU: " + itemRequest.sku()));

            List<InventoryUnit> availableUnits = inventoryUnitRepository
                    .findByInventoryAndStatusOrderByIdAsc(inventory, InventoryUnitStatus.BIN);

            int requestedQuantity = itemRequest.quantity();
            int allocatableQuantity = Math.min(
                    requestedQuantity,
                    Math.min(inventory.getAvailableQuantity(), availableUnits.size()));
            int backorderedQuantity = requestedQuantity - allocatableQuantity;

            inventory.setAvailableQuantity(inventory.getAvailableQuantity() - allocatableQuantity);
            inventory.setReservedQuantity(inventory.getReservedQuantity() + allocatableQuantity);

            for (int index = 0; index < allocatableQuantity; index++) {
                InventoryUnit unit = availableUnits.get(index);
                unit.setStatus(InventoryUnitStatus.ALLOCATED);
                unitsToAllocate.add(unit);
            }

            if (backorderedQuantity > 0) {
                hasBackorder = true;
            }

            BigDecimal lineTotal = product.getUnitPrice().multiply(BigDecimal.valueOf(requestedQuantity));
            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .quantity(requestedQuantity)
                    .unitPrice(product.getUnitPrice())
                    .lineTotal(lineTotal)
                    .build();
            itemsToPersist.add(orderItem);

            if (backorderedQuantity > 0) {
                backOrdersToPersist.add(BackOrder.builder()
                        .product(product)
                        .pendingQuantity(backorderedQuantity)
                        .createdAt(LocalDateTime.now())
                        .build());
            }

            itemResponses.add(new OrderItemResponse(product.getSku(), product.getProductName(),
                    requestedQuantity, allocatableQuantity, backorderedQuantity, product.getUnitPrice(), lineTotal));
        }

        CustomerOrder order = CustomerOrder.builder()
                .orderNumber(generateOrderNumber())
                .customerName(request.customerName().trim())
                .customerEmail(request.customerEmail().trim())
                .shippingAddress(ShippingAddress.builder()
                        .addressLine1(request.shippingAddress().addressLine1().trim())
                        .addressLine2(request.shippingAddress().addressLine2())
                        .city(request.shippingAddress().city().trim())
                        .state(request.shippingAddress().state().trim())
                        .postalCode(request.shippingAddress().postalCode().trim())
                        .country(request.shippingAddress().country().trim())
                        .build())
                .status(OrderStatus.CREATED)
                .serviceable(true)
                .createdAt(LocalDateTime.now())
                .build();

        itemsToPersist.forEach(order::addItem);
        backOrdersToPersist.forEach(order::addBackOrder);
        CustomerOrder savedOrder = customerOrderRepository.save(order);
        updateOrderStatus(savedOrder, OrderStatus.CREATED);
        transactionHistoryService.recordStatusChange(
                savedOrder,
                OrderStatus.CREATED,
                "Order created from order API request");

        unitsToAllocate.forEach(unit -> unit.setAllocatedOrder(savedOrder));

        OrderStatus postCreationStatus = hasBackorder ? OrderStatus.BACKORDERED : OrderStatus.ALLOCATED;
        updateOrderStatus(savedOrder, postCreationStatus);
        transactionHistoryService.recordStatusChange(
                savedOrder,
                postCreationStatus,
                hasBackorder
                        ? "Order created with partial allocation; remaining quantity moved to backorder"
                        : "Inventory allocated successfully for the order");

        String message = hasBackorder
                ? "Order created with partial allocation; remaining quantity moved to backorder"
                : "Order created and inventory allocated successfully";

        return new OrderResponse(savedOrder.getOrderNumber(), savedOrder.getStatus(),
                message, savedOrder.getCreatedAt(), itemResponses);
    }

    @Scheduled(fixedDelayString = "${oms.backorder.scheduler-delay-ms:300000}")
    @Transactional
    public void processBackorders() {
        List<CustomerOrder> backorderedOrders = customerOrderRepository.findByStatusOrderByCreatedAtAsc(OrderStatus.BACKORDERED);
        for (CustomerOrder order : backorderedOrders) {
            boolean fullyAllocated = true;

            List<BackOrder> orderBackOrders = backOrderRepository.findByOrderOrderByCreatedAtAsc(order);
            for (BackOrder backOrder : orderBackOrders) {
                if (backOrder.getPendingQuantity() <= 0) {
                    continue;
                }

                Inventory inventory = inventoryRepository.findByProduct(backOrder.getProduct())
                        .orElseThrow(() -> new BadRequestException("Inventory not found for SKU: "
                                + backOrder.getProduct().getSku()));

                List<InventoryUnit> availableUnits = inventoryUnitRepository
                        .findByInventoryAndStatusOrderByIdAsc(inventory, InventoryUnitStatus.BIN);

                int allocatableQuantity = Math.min(
                        backOrder.getPendingQuantity(),
                        Math.min(inventory.getAvailableQuantity(), availableUnits.size()));

                if (allocatableQuantity > 0) {
                    inventory.setAvailableQuantity(inventory.getAvailableQuantity() - allocatableQuantity);
                    inventory.setReservedQuantity(inventory.getReservedQuantity() + allocatableQuantity);

                    for (int index = 0; index < allocatableQuantity; index++) {
                        InventoryUnit unit = availableUnits.get(index);
                        unit.setStatus(InventoryUnitStatus.ALLOCATED);
                        unit.setAllocatedOrder(order);
                    }

                    backOrder.setPendingQuantity(backOrder.getPendingQuantity() - allocatableQuantity);
                }

                if (backOrder.getPendingQuantity() <= 0) {
                    backOrderRepository.delete(backOrder);
                    continue;
                }

                if (backOrder.getPendingQuantity() > 0) {
                    fullyAllocated = false;
                }
            }

            if (fullyAllocated) {
                updateOrderStatus(order, OrderStatus.ALLOCATED);
                transactionHistoryService.recordStatusChange(
                        order,
                        OrderStatus.ALLOCATED,
                        "Backordered inventory became available and order was fully allocated");
            }
        }
    }

    @Transactional
    public PickOrderResponse pickOrder(String orderNumber, PickOrderRequest request) {
        CustomerOrder order = customerOrderRepository.findByOrderNumberIgnoreCase(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumber));

        if (order.getStatus() != OrderStatus.ALLOCATED) {
            throw new BadRequestException("Order must be in ALLOCATED state to start picking");
        }

        List<String> normalizedSerials = request.serialNumbers().stream()
                .map(this::normalizeSerialNumber)
                .toList();

        Set<String> uniqueSerials = new HashSet<>(normalizedSerials);
        if (uniqueSerials.size() != normalizedSerials.size()) {
            throw new BadRequestException("Serial number validation failed: duplicate serial numbers in request");
        }

        List<InventoryUnit> allocatedUnits = inventoryUnitRepository.findByAllocatedOrderAndStatus(order, InventoryUnitStatus.ALLOCATED);
        int expectedQuantity = allocatedUnits.size();

        if (normalizedSerials.size() != expectedQuantity) {
            throw new BadRequestException("Serial number validation failed: expected " + expectedQuantity
                    + " serial numbers but received " + normalizedSerials.size());
        }

        Map<String, InventoryUnit> allocatedBySerial = allocatedUnits.stream()
                .collect(Collectors.toMap(
                        unit -> unit.getSerialNumber().toUpperCase(Locale.ROOT),
                        Function.identity()));

        if (!allocatedBySerial.keySet().containsAll(uniqueSerials)) {
            throw new BadRequestException("Inventory validation failed: one or more serial numbers are not allocated to order "
                    + order.getOrderNumber());
        }

        String pickerId = request.pickerId().trim();
        String containerId = request.containerId().trim();
        order.setAssignedPicker(pickerId);
        order.setPickContainer(containerId);
        updateOrderStatus(order, OrderStatus.PICK_IN_PROGRESS);
        transactionHistoryService.recordStatusChange(
                order,
                OrderStatus.PICK_IN_PROGRESS,
                "Picking started in container " + containerId);

        List<PickedInventoryResponse> pickedInventory = new ArrayList<>();
        for (String serialNumber : normalizedSerials) {
            InventoryUnit unit = allocatedBySerial.get(serialNumber);
            String fromLocation = unit.getCurrentLocation();
            unit.setStatus(InventoryUnitStatus.CONTAINER);
            unit.setCurrentLocation(containerId);
            unit.setContainerLocation(containerId);

            Inventory inventory = unit.getInventory();
            inventory.setReservedQuantity(inventory.getReservedQuantity() - 1);

            pickedInventory.add(new PickedInventoryResponse(
                    inventory.getProduct().getSku(),
                    unit.getSerialNumber(),
                    fromLocation,
                    containerId));
        }

        updateOrderStatus(order, OrderStatus.PICKED);
        transactionHistoryService.recordStatusChange(
                order,
                OrderStatus.PICKED,
                "All allocated inventory units were picked successfully");
        updateOrderStatus(order, OrderStatus.READY_TO_PACK);
        transactionHistoryService.recordStatusChange(
                order,
                OrderStatus.READY_TO_PACK,
                "Order picked and moved to ready-to-pack stage");

        return new PickOrderResponse(
                order.getOrderNumber(),
                order.getAssignedPicker(),
                order.getPickContainer(),
                order.getStatus(),
                "Order picked and ready to pack",
                pickedInventory);
    }

    @Transactional
    public PackOrderResponse packOrder(String orderNumber, PackOrderRequest request) {
        CustomerOrder order = customerOrderRepository.findByOrderNumberIgnoreCase(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumber));

        if (order.getStatus() != OrderStatus.READY_TO_PACK) {
            throw new BadRequestException("Order must be in READY_TO_PACK state before packing");
        }

        BigDecimal packageWeight = request.weight();
        String assignedBox = assignBox(packageWeight);

        order.setPackageWeight(packageWeight);
        order.setAssignedBox(assignedBox);
        updateOrderStatus(order, OrderStatus.PACKED);
        transactionHistoryService.recordStatusChange(
                order,
                OrderStatus.PACKED,
                "Order packed in box " + assignedBox + " with weight " + packageWeight);

        return new PackOrderResponse(
                order.getOrderNumber(),
                order.getPackageWeight(),
                order.getAssignedBox(),
                order.getStatus(),
                "Box assigned and order packed");
    }

    @Transactional
    public ShippingLabelResponse generateShippingLabel(String orderNumber) {
        CustomerOrder order = customerOrderRepository.findByOrderNumberIgnoreCase(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumber));

        if (order.getStatus() != OrderStatus.PACKED) {
            throw new BadRequestException("Order must be in PACKED state before generating shipping label");
        }

        ShippedData shippedData = shippedDataRepository.findByOrder(order)
                .orElseGet(() -> shippedDataRepository.save(ShippedData.builder()
                        .order(order)
                        .shippingLabelNumber(generateShippingLabelNumber(order))
                        .trackingNumber(generateTrackingNumber(order))
                        .carrier(DEFAULT_CARRIER)
                        .serviceLevel(DEFAULT_SERVICE_LEVEL)
                        .shippingLabelGeneratedAt(LocalDateTime.now())
                        .build()));

        updateOrderStatus(order, OrderStatus.READY_TO_SHIP);
        order.setShipmentId(generateShipmentId());
        order.setTrackingNumber(shippedData.getTrackingNumber());
        order.setShipmentStatus(OrderStatus.READY_TO_SHIP.name());
        order.setOrderStatusCode(OrderStatus.READY_TO_SHIP.ordinal() + 1);
        customerOrderRepository.save(order);
        transactionHistoryService.recordStatusChange(
                order,
                OrderStatus.READY_TO_SHIP,
                "Shipping label generated and order is ready to ship");
        String barcodeValue = buildBarcodeValue(order, shippedData);
        String destinationAddress = formatDestinationAddress(order.getShippingAddress());
        String labelImageBase64 = generateLabelImageBase64(order, shippedData, destinationAddress, barcodeValue);
        String labelImageDataUrl = "data:" + LABEL_IMAGE_CONTENT_TYPE + ";base64," + labelImageBase64;

        return new ShippingLabelResponse(
                order.getOrderNumber(),
                shippedData.getShippingLabelNumber(),
                shippedData.getTrackingNumber(),
                shippedData.getCarrier(),
                shippedData.getServiceLevel(),
                shippedData.getShippingLabelGeneratedAt(),
                SHIP_FROM_ADDRESS,
                destinationAddress,
                order.getPackageWeight(),
                order.getAssignedBox(),
                barcodeValue,
                LABEL_IMAGE_CONTENT_TYPE,
                labelImageBase64,
                labelImageDataUrl,
                "LABEL_GENERATED",
                "Shipping label generated");
    }

    @Transactional(readOnly = true)
    public List<ShippingOrderTableResponse> getShippingOrderTable() {
        return customerOrderRepository.findAllByOrderByCreatedAtAsc().stream()
                .filter(order -> order.getStatus() == OrderStatus.PACKED
                        || order.getStatus() == OrderStatus.READY_TO_SHIP
                        || order.getStatus() == OrderStatus.SHIPPED)
                .map(this::toShippingOrderTableResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderTransactionResponse> getOrderTransactions() {
        Map<String, List<CarrierSentData>> carrierUpdatesByShipmentId = carrierSentDataRepository.findAll().stream()
                .filter(update -> update.getShipmentId() != null && !update.getShipmentId().isBlank())
                .collect(Collectors.groupingBy(update -> update.getShipmentId().trim().toUpperCase(Locale.ROOT)));

        return customerOrderRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(order -> toOrderTransactionResponse(order, carrierUpdatesByShipmentId))
                .toList();
    }

    private ShippingOrderTableResponse toShippingOrderTableResponse(CustomerOrder order) {
        ShippedData shippedData = shippedDataRepository.findByOrder(order).orElse(null);
        return new ShippingOrderTableResponse(
                order.getOrderNumber(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getStatus(),
                order.getAssignedBox(),
                shippedData == null ? null : shippedData.getShippingLabelNumber(),
                shippedData == null ? null : shippedData.getTrackingNumber(),
                shippedData == null ? null : shippedData.getCarrier(),
                shippedData == null ? null : shippedData.getServiceLevel(),
                shippedData == null ? null : shippedData.getShippingLabelGeneratedAt(),
                order.getLpn() == null ? null : order.getLpn().getLpnNumber(),
                resolveTruckloadNumber(order),
                formatDestinationAddress(order.getShippingAddress()));
    }

    private OrderTransactionResponse toOrderTransactionResponse(
            CustomerOrder order,
            Map<String, List<CarrierSentData>> carrierUpdatesByShipmentId
    ) {
        ShippedData shippedData = shippedDataRepository.findByOrder(order).orElse(null);
        List<CarrierSentData> relatedCarrierUpdates = resolveCarrierUpdates(order, carrierUpdatesByShipmentId);
        CarrierSentData latestCarrierUpdate = relatedCarrierUpdates.stream()
                .filter(update -> update.getStatusTime() != null)
                .max(java.util.Comparator.comparing(CarrierSentData::getStatusTime))
                .orElse(null);
        LocalDateTime deliveredAt = relatedCarrierUpdates.stream()
                .filter(update -> "DELIVERED".equalsIgnoreCase(update.getCarrierStatus()))
                .map(CarrierSentData::getStatusTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(order.getStatus() == OrderStatus.DELIVERED ? order.getUpdatedAt() : null);

        return new OrderTransactionResponse(
                order.getOrderNumber(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getStatus(),
                order.getShipmentStatus(),
                order.getOrderStatusCode(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                shippedData == null ? null : shippedData.getShippingLabelGeneratedAt(),
                latestCarrierUpdate == null ? null : latestCarrierUpdate.getStatusTime(),
                deliveredAt,
                order.getAssignedPicker(),
                order.getPickContainer(),
                order.getAssignedBox(),
                order.getPackageWeight(),
                order.getShipmentId(),
                order.getTrackingNumber(),
                shippedData == null ? null : shippedData.getShippingLabelNumber(),
                shippedData == null ? null : shippedData.getCarrier(),
                shippedData == null ? null : shippedData.getServiceLevel(),
                order.getLpn() == null ? null : order.getLpn().getLpnNumber(),
                order.getLpn() == null ? null : order.getLpn().getCarrierName(),
                order.getLpn() == null ? null : order.getLpn().getShipmentStatus(),
                resolveTruckloadNumber(order),
                resolveTruckloadCarrier(order),
                resolveTruckloadShipmentStatus(order),
                resolveTruckloadDispatchDate(order),
                order.getItems().stream().mapToInt(OrderItem::getQuantity).sum(),
                order.getBackOrders().stream().mapToInt(BackOrder::getPendingQuantity).sum(),
                order.getItems().stream()
                        .map(item -> item.getProduct().getSku() + " x " + item.getQuantity())
                        .collect(Collectors.joining(", ")),
                formatDestinationAddress(order.getShippingAddress()));
    }

    private List<CarrierSentData> resolveCarrierUpdates(
            CustomerOrder order,
            Map<String, List<CarrierSentData>> carrierUpdatesByShipmentId
    ) {
        return java.util.stream.Stream.of(order.getShipmentId(), order.getTrackingNumber(), order.getOrderNumber())
                .filter(value -> value != null && !value.isBlank())
                .map(value -> carrierUpdatesByShipmentId.getOrDefault(value.trim().toUpperCase(Locale.ROOT), List.of()))
                .flatMap(List::stream)
                .distinct()
                .toList();
    }

    private String resolveTruckloadNumber(CustomerOrder order) {
        if (order.getDirectTruckload() != null) {
            return order.getDirectTruckload().getTruckloadNumber();
        }
        if (order.getLpn() != null && order.getLpn().getTruckload() != null) {
            return order.getLpn().getTruckload().getTruckloadNumber();
        }
        return null;
    }

    private String resolveTruckloadCarrier(CustomerOrder order) {
        if (order.getDirectTruckload() != null) {
            return order.getDirectTruckload().getCarrierName();
        }
        if (order.getLpn() != null && order.getLpn().getTruckload() != null) {
            return order.getLpn().getTruckload().getCarrierName();
        }
        return null;
    }

    private com.advatix.OMS.domain.ShipmentStatus resolveTruckloadShipmentStatus(CustomerOrder order) {
        if (order.getDirectTruckload() != null) {
            return order.getDirectTruckload().getShipmentStatus();
        }
        if (order.getLpn() != null && order.getLpn().getTruckload() != null) {
            return order.getLpn().getTruckload().getShipmentStatus();
        }
        return null;
    }

    private LocalDateTime resolveTruckloadDispatchDate(CustomerOrder order) {
        if (order.getDirectTruckload() != null) {
            return order.getDirectTruckload().getDispatchDate();
        }
        if (order.getLpn() != null && order.getLpn().getTruckload() != null) {
            return order.getLpn().getTruckload().getDispatchDate();
        }
        return null;
    }

    private String generateOrderNumber() {
        return "ORD-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }

    private void updateOrderStatus(CustomerOrder order, OrderStatus status) {
        order.setStatus(status);
        order.setOrderStatusCode(status.ordinal() + 1);
        order.setUpdatedAt(LocalDateTime.now());
    }

    private String generateShippingLabelNumber(CustomerOrder order) {
        return "SL-" + order.getOrderNumber().replace("ORD-", "");
    }

    private String generateTrackingNumber(CustomerOrder order) {
        return "OMS" + order.getOrderNumber().replace("ORD-", "") + "IN";
    }

    private Product resolveProductForOrder(String sku, int requestedQuantity) {
        List<Product> products = productRepository.findBySkuIgnoreCaseOrderByIdAsc(sku);
        if (products.isEmpty()) {
            throw new BadRequestException("Product not found for SKU: " + sku);
        }

        Product bestProduct = products.get(0);
        int bestAllocatableQuantity = -1;
        for (Product product : products) {
            int allocatableQuantity = inventoryRepository.findByProduct(product)
                    .map(inventory -> {
                        int availableUnits = inventoryUnitRepository
                                .findByInventoryAndStatusOrderByIdAsc(inventory, InventoryUnitStatus.BIN)
                                .size();
                        return Math.min(requestedQuantity, Math.min(inventory.getAvailableQuantity(), availableUnits));
                    })
                    .orElse(0);

            if (allocatableQuantity > bestAllocatableQuantity) {
                bestProduct = product;
                bestAllocatableQuantity = allocatableQuantity;
            }

            if (allocatableQuantity == requestedQuantity) {
                break;
            }
        }

        return bestProduct;
    }

    private String normalizeSerialNumber(String serial) {
        String normalized = serial == null ? "" : serial.trim();
        while (normalized.startsWith("\"") || normalized.startsWith("'")) {
            normalized = normalized.substring(1).trim();
        }
        while (normalized.endsWith("\"") || normalized.endsWith("'") || normalized.endsWith(",")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String formatDestinationAddress(ShippingAddress address) {
        List<String> parts = new ArrayList<>();
        addAddressPart(parts, address.getAddressLine1());
        addAddressPart(parts, address.getAddressLine2());
        addAddressPart(parts, address.getCity());
        addAddressPart(parts, address.getState());
        addAddressPart(parts, address.getPostalCode());
        addAddressPart(parts, address.getCountry());
        return String.join(", ", parts);
    }

    private String buildBarcodeValue(CustomerOrder order, ShippedData shippedData) {
        return shippedData.getCarrier() + "|"
                + shippedData.getServiceLevel() + "|"
                + shippedData.getTrackingNumber() + "|"
                + order.getOrderNumber();
    }

    private String generateLabelImageBase64(CustomerOrder order, ShippedData shippedData, String destinationAddress, String barcodeValue) {
        BufferedImage image = new BufferedImage(800, 1200, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.setStroke(new BasicStroke(4));
            graphics.drawRect(20, 20, 760, 1160);

            drawText(graphics, shippedData.getCarrier(), 50, 85, Font.BOLD, 42);
            drawText(graphics, shippedData.getServiceLevel(), 565, 85, Font.BOLD, 34);
            graphics.drawLine(40, 125, 760, 125);

            drawText(graphics, "SHIP FROM", 50, 175, Font.BOLD, 22);
            drawWrappedText(graphics, SHIP_FROM_ADDRESS, 50, 210, 690, 26, Font.PLAIN, 22);
            graphics.drawLine(40, 300, 760, 300);

            drawText(graphics, "SHIP TO", 50, 350, Font.BOLD, 28);
            drawText(graphics, order.getCustomerName(), 50, 395, Font.BOLD, 32);
            drawWrappedText(graphics, destinationAddress, 50, 435, 690, 34, Font.PLAIN, 28);
            graphics.drawLine(40, 610, 760, 610);

            drawText(graphics, "ORDER", 50, 665, Font.BOLD, 22);
            drawText(graphics, order.getOrderNumber(), 50, 700, Font.PLAIN, 28);
            drawText(graphics, "LABEL", 420, 665, Font.BOLD, 22);
            drawText(graphics, shippedData.getShippingLabelNumber(), 420, 700, Font.PLAIN, 28);

            drawText(graphics, "BOX", 50, 770, Font.BOLD, 22);
            drawText(graphics, order.getAssignedBox(), 50, 805, Font.PLAIN, 28);
            drawText(graphics, "WEIGHT", 420, 770, Font.BOLD, 22);
            drawText(graphics, order.getPackageWeight() + " kg", 420, 805, Font.PLAIN, 28);
            graphics.drawLine(40, 850, 760, 850);

            drawText(graphics, "TRACKING NUMBER", 50, 900, Font.BOLD, 24);
            drawText(graphics, shippedData.getTrackingNumber(), 50, 945, Font.BOLD, 38);
            drawBarcode(graphics, barcodeValue, 55, 990, 690, 100);
            drawText(graphics, barcodeValue, 60, 1130, Font.PLAIN, 18);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to generate shipping label image", exception);
        } finally {
            graphics.dispose();
        }
    }

    private void drawText(Graphics2D graphics, String text, int x, int y, int style, int size) {
        graphics.setFont(new Font("Arial", style, size));
        graphics.setColor(Color.BLACK);
        graphics.drawString(String.valueOf(text), x, y);
    }

    private void drawWrappedText(Graphics2D graphics, String text, int x, int y, int maxWidth, int lineHeight,
                                 int style, int size) {
        graphics.setFont(new Font("Arial", style, size));
        StringBuilder line = new StringBuilder();
        int currentY = y;
        for (String word : String.valueOf(text).split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (graphics.getFontMetrics().stringWidth(candidate) > maxWidth && !line.isEmpty()) {
                graphics.drawString(line.toString(), x, currentY);
                line = new StringBuilder(word);
                currentY += lineHeight;
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            graphics.drawString(line.toString(), x, currentY);
        }
    }

    private void drawBarcode(Graphics2D graphics, String value, int x, int y, int width, int height) {
        graphics.setColor(Color.WHITE);
        graphics.fillRect(x, y, width, height);
        graphics.setColor(Color.BLACK);
        int cursor = x;
        for (int index = 0; index < value.length() && cursor < x + width; index++) {
            int code = value.charAt(index);
            int barWidth = 2 + (code % 4);
            int gap = 2 + ((code / 4) % 3);
            graphics.fillRect(cursor, y, barWidth, height);
            cursor += barWidth + gap;
        }
        graphics.setStroke(new BasicStroke(2));
        graphics.drawRect(x, y, width, height);
    }

    private void addAddressPart(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value.trim());
        }
    }

    private String assignBox(BigDecimal weight) {
        if (weight.compareTo(new BigDecimal("1.000")) <= 0) {
            return "SMALL_BOX";
        }
        if (weight.compareTo(new BigDecimal("5.000")) <= 0) {
            return "MEDIUM_BOX";
        }
        if (weight.compareTo(new BigDecimal("10.000")) <= 0) {
            return "LARGE_BOX";
        }
        return "EXTRA_LARGE_BOX";
    }
    private String generateShipmentId() {
        return "SH-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddhhmmssSSS"));
    }
}
