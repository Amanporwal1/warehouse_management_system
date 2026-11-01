package com.advatix.OMS.repository;

import com.advatix.OMS.domain.TransactionHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionHistoryRepository extends JpaRepository<TransactionHistory, Long> {

    List<TransactionHistory> findByOrderOrderNumberIgnoreCaseOrderByPerformedAtDesc(String orderNumber);

    List<TransactionHistory> findByOrderOrderNumberIgnoreCaseOrderByPerformedAtAsc(String orderNumber);

    Optional<TransactionHistory> findFirstByOrderOrderNumberIgnoreCaseOrderByPerformedAtDesc(String orderNumber);

    boolean existsByOrderOrderNumberIgnoreCaseAndStatusCode(String orderNumber, Integer statusCode);
}
