package com.advatix.OMS.controller;

import com.advatix.OMS.dto.OrderLatestStatusResponse;
import com.advatix.OMS.dto.TransactionHistoryResponse;
import com.advatix.OMS.service.TransactionHistoryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/{orderNumber}/transaction-history")
@RequiredArgsConstructor
public class TransactionHistoryController {

    private final TransactionHistoryService transactionHistoryService;

    @GetMapping
    public ResponseEntity<List<TransactionHistoryResponse>> getHistoryByOrderNumber(@PathVariable String orderNumber) {
        return ResponseEntity.ok(transactionHistoryService.getHistoryByOrderNumber(orderNumber));
    }

    @GetMapping("/latest")
    public ResponseEntity<OrderLatestStatusResponse> getLatestStatus(@PathVariable String orderNumber) {
        return ResponseEntity.ok(transactionHistoryService.getLatestStatus(orderNumber));
    }

    @GetMapping("/timeline")
    public ResponseEntity<List<TransactionHistoryResponse>> getTimeline(@PathVariable String orderNumber) {
        return ResponseEntity.ok(transactionHistoryService.getTimelineByOrderNumber(orderNumber));
    }
}
