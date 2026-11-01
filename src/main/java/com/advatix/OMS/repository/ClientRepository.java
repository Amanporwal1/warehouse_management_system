package com.advatix.OMS.repository;

import com.advatix.OMS.domain.Client;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByClientCodeIgnoreCase(String clientCode);

    Optional<Client> findByNameIgnoreCase(String name);
}
