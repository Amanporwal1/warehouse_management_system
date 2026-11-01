package com.advatix.OMS.controller;

import com.advatix.OMS.dto.OrderRequest;
import com.advatix.OMS.dto.OrderResponse;
import com.advatix.OMS.dto.OrderTransactionResponse;
import com.advatix.OMS.dto.PackOrderRequest;
import com.advatix.OMS.dto.PackOrderResponse;
import com.advatix.OMS.dto.PickOrderRequest;
import com.advatix.OMS.dto.PickOrderResponse;
import com.advatix.OMS.dto.ShippingLabelResponse;
import com.advatix.OMS.dto.ShippingOrderTableResponse;
import com.advatix.OMS.dto.TransactionHistoryResponse;
import com.advatix.OMS.service.OrderService;
import com.advatix.OMS.service.TransactionHistoryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final TransactionHistoryService transactionHistoryService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request));
    }

    @PostMapping("/{orderNumber}/pick")
    public ResponseEntity<PickOrderResponse> pickOrder(
            @PathVariable String orderNumber,
            @Valid @RequestBody PickOrderRequest request
    ) {
        return ResponseEntity.ok(orderService.pickOrder(orderNumber, request));
    }

    @PostMapping("/{orderNumber}/pack")
    public ResponseEntity<PackOrderResponse> packOrder(
            @PathVariable String orderNumber,
            @Valid @RequestBody PackOrderRequest request
    ) {
        return ResponseEntity.ok(orderService.packOrder(orderNumber, request));
    }

    @PostMapping("/{orderNumber}/shipping-label")
    public ResponseEntity<ShippingLabelResponse> generateShippingLabel(@PathVariable String orderNumber) {
        return ResponseEntity.ok(orderService.generateShippingLabel(orderNumber));
    }

    @GetMapping("/shipping-table")
    public ResponseEntity<List<ShippingOrderTableResponse>> getShippingOrderTable() {
        return ResponseEntity.ok(orderService.getShippingOrderTable());
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<OrderTransactionResponse>> getOrderTransactions() {
        return ResponseEntity.ok(orderService.getOrderTransactions());
    }

    @GetMapping("/{orderNumber}/transactions")
    public ResponseEntity<List<TransactionHistoryResponse>> getTransactionsByOrderNumber(@PathVariable String orderNumber) {
        return ResponseEntity.ok(transactionHistoryService.getHistoryByOrderNumber(orderNumber));
    }
}
