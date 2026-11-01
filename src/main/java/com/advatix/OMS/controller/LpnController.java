package com.advatix.OMS.controller;

import com.advatix.OMS.dto.AssignCarrierRequest;
import com.advatix.OMS.dto.AttachOrdersRequest;
import com.advatix.OMS.dto.CreateLpnRequest;
import com.advatix.OMS.dto.LpnResponse;
import com.advatix.OMS.service.LpnService;
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
@RequestMapping("/api/lpns")
@RequiredArgsConstructor
public class LpnController {

    private final LpnService lpnService;

    @PostMapping
    public ResponseEntity<LpnResponse> createLpn(@Valid @RequestBody CreateLpnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lpnService.createLpn(request));
    }

    @GetMapping("/{lpnNumber}")
    public ResponseEntity<LpnResponse> getLpn(@PathVariable String lpnNumber) {
        return ResponseEntity.ok(lpnService.getLpn(lpnNumber));
    }

    @PostMapping("/{lpnNumber}/orders")
    public ResponseEntity<LpnResponse> attachOrders(
            @PathVariable String lpnNumber,
            @Valid @RequestBody AttachOrdersRequest request
    ) {
        return ResponseEntity.ok(lpnService.attachOrders(lpnNumber, request));
    }

    @PostMapping("/{lpnNumber}/carrier")
    public ResponseEntity<LpnResponse> assignCarrier(
            @PathVariable String lpnNumber,
            @Valid @RequestBody AssignCarrierRequest request
    ) {
        return ResponseEntity.ok(lpnService.assignCarrier(lpnNumber, request));
    }
}
