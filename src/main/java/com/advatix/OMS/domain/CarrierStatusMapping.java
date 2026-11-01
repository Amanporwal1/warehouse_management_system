package com.advatix.OMS.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "carrier_status_mapping")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarrierStatusMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "status_code", nullable = false, unique = true)
    private Integer statusCode;

    @Column(name = "carrier_status", nullable = false, unique = true, length = 60)
    private String carrierStatus;
}
