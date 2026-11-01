package com.advatix.OMS.config;

import com.advatix.OMS.domain.AdminUser;
import com.advatix.OMS.domain.CarrierStatusMapping;
import com.advatix.OMS.repository.AdminUserRepository;
import com.advatix.OMS.repository.CarrierStatusMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final AdminProperties adminProperties;

    @Bean
    CommandLineRunner seedDefaultAdmin(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder) {
        return args -> adminUserRepository.findByUsername(adminProperties.username())
                .orElseGet(() -> adminUserRepository.save(AdminUser.builder()
                        .username(adminProperties.username())
                        .password(passwordEncoder.encode(adminProperties.password()))
                        .fullName(adminProperties.fullName())
                        .build()));
    }

    @Bean
    CommandLineRunner seedCarrierStatusMapping(CarrierStatusMappingRepository carrierStatusMappingRepository) {
        return args -> {
            Map<Integer, String> mappings = new LinkedHashMap<>();
            mappings.put(1, "CREATED");
            mappings.put(2, "ALLOCATED");
            mappings.put(3, "BACKORDERED");
            mappings.put(4, "PICK_IN_PROGRESS");
            mappings.put(5, "PICKED");
            mappings.put(6, "READY_TO_PACK");
            mappings.put(7, "PACKED");
            mappings.put(8, "READY_TO_SHIP");
            mappings.put(9, "SHIPPED");
            mappings.put(10, "IN_TRANSIT");
            mappings.put(11, "OUT_FOR_DELIVERY");
            mappings.put(12, "DELIVERED");
            mappings.put(13, "CANCELLED");
            mappings.put(14, "REJECTED");

            mappings.forEach((statusCode, carrierStatus) -> {
                CarrierStatusMapping mapping = carrierStatusMappingRepository.findByStatusCode(statusCode)
                        .orElseGet(() -> CarrierStatusMapping.builder()
                                .statusCode(statusCode)
                                .build());
                mapping.setCarrierStatus(carrierStatus);
                carrierStatusMappingRepository.save(mapping);
            });
        };
    }
}
