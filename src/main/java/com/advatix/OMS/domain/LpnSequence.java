package com.advatix.OMS.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lpn_sequences")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LpnSequence {

    @Id
    private LocalDate sequenceDate;

    @Column(nullable = false)
    private Integer nextValue;
}
