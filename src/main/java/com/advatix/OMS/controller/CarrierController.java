package com.advatix.OMS.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.advatix.OMS.dto.CarrierDataRequest;
import com.advatix.OMS.dto.CarrierDataResponse;
import com.advatix.OMS.dto.CarrierSyncResponse;
import com.advatix.OMS.service.CarrierUpdatesService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/carrier")
public class CarrierController {

    private final CarrierUpdatesService carrierUpdatesService;

    @PostMapping
    public ResponseEntity<CarrierDataResponse> createCarrierStatus(@Valid @RequestBody CarrierDataRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(carrierUpdatesService.createCarrierStatus(request));
    }

    @PostMapping("/update-order")
    public ResponseEntity<CarrierSyncResponse> createCarrierStatusAndUpdateOrder() {
        return ResponseEntity.ok(carrierUpdatesService.createCarrierStatusAndUpdateOrder());
    }
}
