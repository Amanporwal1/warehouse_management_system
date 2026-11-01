package com.advatix.OMS.repository;

import com.advatix.OMS.domain.Truckload;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TruckloadRepository extends JpaRepository<Truckload, Long> {

    Optional<Truckload> findByTruckloadNumberIgnoreCase(String truckloadNumber);
}
