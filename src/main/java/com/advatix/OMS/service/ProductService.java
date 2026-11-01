package com.advatix.OMS.service;

import com.advatix.OMS.domain.Inventory;
import com.advatix.OMS.domain.InventoryUnit;
import com.advatix.OMS.domain.InventoryUnitStatus;
import com.advatix.OMS.domain.Product;
import com.advatix.OMS.dto.InventoryResponse;
import com.advatix.OMS.dto.ProductRequest;
import com.advatix.OMS.dto.ProductResponse;
import com.advatix.OMS.exception.BadRequestException;
import com.advatix.OMS.exception.ResourceNotFoundException;
import com.advatix.OMS.repository.ClientRepository;
import com.advatix.OMS.repository.InventoryRepository;
import com.advatix.OMS.repository.InventoryUnitRepository;
import com.advatix.OMS.repository.ProductRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ClientRepository clientRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryUnitRepository inventoryUnitRepository;

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        var client = clientRepository.findByNameIgnoreCase(request.clientName())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + request.clientName()));

        Product product = productRepository.save(Product.builder()
                .productName(request.productName().trim())
                .client(client)
                .sku(request.sku().trim())
                .productDescription(request.productDescription().trim())
                .upc(request.upc().trim())
                .weight(request.weight())
                .unitPrice(request.unitPrice())
                .build());

        Inventory inventory = inventoryRepository.save(Inventory.builder()
                .product(product)
                .availableQuantity(request.openingStock())
                .reservedQuantity(0)
                .warehouseLocation(request.warehouseLocation().trim())
                .build());

        long nextSerialNumber = inventoryUnitRepository
                .countBySerialNumberStartingWithIgnoreCase(product.getSku() + "-SN-") + 1;
        for (int index = 1; index <= request.openingStock(); index++) {
            inventoryUnitRepository.save(InventoryUnit.builder()
                    .inventory(inventory)
                    .serialNumber(product.getSku() + "-SN-" + nextSerialNumber++)
                    .status(InventoryUnitStatus.BIN)
                    .currentLocation(inventory.getWarehouseLocation())
                    .build());
        }

        return new ProductResponse(product.getId(), product.getProductName(), client.getName(), product.getSku(),
                product.getProductDescription(), product.getUpc(), product.getWeight(), product.getUnitPrice(),
                inventory.getAvailableQuantity());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getProducts() {
        return inventoryRepository.findAll().stream()
                .sorted(Comparator.comparing(inventory -> inventory.getProduct().getProductName(), String.CASE_INSENSITIVE_ORDER))
                .map(inventory -> {
                    Product product = inventory.getProduct();
                    return new ProductResponse(product.getId(), product.getProductName(), product.getClient().getName(),
                            product.getSku(), product.getProductDescription(), product.getUpc(), product.getWeight(),
                            product.getUnitPrice(), inventory.getAvailableQuantity());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> getInventory() {
        return inventoryRepository.findAll().stream()
                .sorted(Comparator.comparing(inventory -> inventory.getProduct().getSku(), String.CASE_INSENSITIVE_ORDER))
                .map(inventory -> new InventoryResponse(
                        inventory.getProduct().getSku(),
                        inventory.getAvailableQuantity(),
                        inventory.getReservedQuantity(),
                        inventory.getWarehouseLocation()))
                .toList();
    }
}
