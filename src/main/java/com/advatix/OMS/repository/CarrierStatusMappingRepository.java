package com.advatix.OMS.repository;

import com.advatix.OMS.domain.CarrierStatusMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CarrierStatusMappingRepository extends JpaRepository<CarrierStatusMapping, Long> {

    Optional<CarrierStatusMapping> findByStatusCode(Integer statusCode);

    Optional<CarrierStatusMapping> findByCarrierStatusIgnoreCase(String carrierStatus);

    boolean existsByStatusCode(Integer statusCode);
}
