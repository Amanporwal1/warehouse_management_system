package com.advatix.OMS.controller;

import com.advatix.OMS.dto.ClientRequest;
import com.advatix.OMS.dto.ClientResponse;
import com.advatix.OMS.dto.InventoryResponse;
import com.advatix.OMS.dto.ProductRequest;
import com.advatix.OMS.dto.ProductResponse;
import com.advatix.OMS.service.ClientService;
import com.advatix.OMS.service.ProductService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ClientService clientService;
    private final ProductService productService;

    @PostMapping("/clients")
    public ResponseEntity<ClientResponse> createClient(@Valid @RequestBody ClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clientService.createClient(request));
    }

    @GetMapping("/clients")
    public ResponseEntity<List<ClientResponse>> getClients() {
        return ResponseEntity.ok(clientService.getClients());
    }

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    @GetMapping("/products")
    public ResponseEntity<List<ProductResponse>> getProducts() {
        return ResponseEntity.ok(productService.getProducts());
    }

    @GetMapping("/inventory")
    public ResponseEntity<List<InventoryResponse>> getInventory() {
        return ResponseEntity.ok(productService.getInventory());
    }
}
