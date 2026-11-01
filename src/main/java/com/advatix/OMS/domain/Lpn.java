package com.advatix.OMS.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "lpns")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lpn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String lpnNumber;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, length = 80)
    private String createdBy;

    @Column(length = 80)
    private String carrierName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private ShipmentStatus shipmentStatus;

    @Column(nullable = false)
    private Integer totalOrders;

    @Column(nullable = false)
    private Integer totalQuantity;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal totalWeight;

    @Column(length = 500)
    private String remarks;

    @ManyToOne
    @JoinColumn(name = "truckload_id")
    private Truckload truckload;

    @OneToMany(mappedBy = "lpn", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    private List<CustomerOrder> orders = new ArrayList<>();
}
