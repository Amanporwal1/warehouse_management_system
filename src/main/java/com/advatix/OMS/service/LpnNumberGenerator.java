package com.advatix.OMS.service;

import com.advatix.OMS.domain.LpnSequence;
import com.advatix.OMS.repository.LpnSequenceRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LpnNumberGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final LpnSequenceRepository lpnSequenceRepository;

    @Transactional
    public synchronized String generate() {
        LocalDate today = LocalDate.now();
        LpnSequence sequence = lpnSequenceRepository.findWithLockBySequenceDate(today)
                .orElseGet(() -> LpnSequence.builder()
                        .sequenceDate(today)
                        .nextValue(1)
                        .build());

        int value = sequence.getNextValue();
        sequence.setNextValue(value + 1);
        lpnSequenceRepository.save(sequence);

        return "LPN-" + today.format(DATE_FORMATTER) + "-" + String.format("%04d", value);
    }
}
