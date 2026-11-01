package com.advatix.OMS.controller;

import com.advatix.OMS.dto.AssignCarrierRequest;
import com.advatix.OMS.dto.AttachLpnsRequest;
import com.advatix.OMS.dto.AttachOrdersRequest;
import com.advatix.OMS.dto.CreateTruckloadRequest;
import com.advatix.OMS.dto.TruckloadResponse;
import com.advatix.OMS.service.TruckloadService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/truckloads")
@RequiredArgsConstructor
public class TruckloadController {

    private final TruckloadService truckloadService;

    @PostMapping
    public ResponseEntity<TruckloadResponse> createTruckload(@RequestBody CreateTruckloadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(truckloadService.createTruckload(request));
    }

    @GetMapping("/{truckloadNumber}")
    public ResponseEntity<TruckloadResponse> getTruckload(@PathVariable String truckloadNumber) {
        return ResponseEntity.ok(truckloadService.getTruckload(truckloadNumber));
    }

    @PostMapping("/{truckloadNumber}/lpns")
    public ResponseEntity<TruckloadResponse> attachLpns(
            @PathVariable String truckloadNumber,
            @Valid @RequestBody AttachLpnsRequest request
    ) {
        return ResponseEntity.ok(truckloadService.attachLpns(truckloadNumber, request));
    }

    @PostMapping("/{truckloadNumber}/orders")
    public ResponseEntity<TruckloadResponse> attachDirectOrders(
            @PathVariable String truckloadNumber,
            @Valid @RequestBody AttachOrdersRequest request
    ) {
        return ResponseEntity.ok(truckloadService.attachDirectOrders(truckloadNumber, request));
    }

    @PostMapping("/{truckloadNumber}/carrier")
    public ResponseEntity<TruckloadResponse> assignCarrier(
            @PathVariable String truckloadNumber,
            @Valid @RequestBody AssignCarrierRequest request
    ) {
        return ResponseEntity.ok(truckloadService.assignCarrier(truckloadNumber, request));
    }

    @PostMapping("/{truckloadNumber}/dispatch")
    public ResponseEntity<TruckloadResponse> dispatchTruckload(@PathVariable String truckloadNumber) {
        return ResponseEntity.ok(truckloadService.dispatchTruckload(truckloadNumber));
    }
}
