package com.advatix.OMS.repository;

import com.advatix.OMS.domain.LpnSequence;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface LpnSequenceRepository extends JpaRepository<LpnSequence, LocalDate> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LpnSequence> findWithLockBySequenceDate(LocalDate sequenceDate);
}
