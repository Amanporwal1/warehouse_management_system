package com.advatix.OMS.repository;

import com.advatix.OMS.domain.Lpn;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LpnRepository extends JpaRepository<Lpn, Long> {

    Optional<Lpn> findByLpnNumberIgnoreCase(String lpnNumber);
}
