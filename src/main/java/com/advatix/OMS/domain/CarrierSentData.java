package com.advatix.OMS.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "carrier_sent_data")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarrierSentData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shipment_id", nullable = false, length = 80)
    private String shipmentId;

    @Column(name = "carrier_status", nullable = false, length = 60)
    private String carrierStatus;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "int default 0")
    private Integer webhook = 0;

    @Column(name = "status_time")
    private LocalDateTime statusTime;

}
