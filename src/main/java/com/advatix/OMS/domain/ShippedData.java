package com.advatix.OMS.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "shipped_data")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippedData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private CustomerOrder order;

    @Column(nullable = false, length = 80)
    private String shippingLabelNumber;

    @Column(nullable = false)
    private LocalDateTime shippingLabelGeneratedAt;

    @Column(nullable = false, length = 80)
    private String trackingNumber;

    @Column(nullable = false, length = 80)
    private String carrier;

    @Column(nullable = false, length = 80)
    private String serviceLevel;

}
