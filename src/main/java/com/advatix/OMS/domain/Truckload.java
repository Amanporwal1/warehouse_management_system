package com.advatix.OMS.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
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
@Table(name = "truckloads")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Truckload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String truckloadNumber;

    @Column(length = 80)
    private String carrierName;

    private LocalDateTime dispatchDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private ShipmentStatus shipmentStatus;

    @Column(length = 500)
    private String remarks;

    @OneToMany(mappedBy = "truckload", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    private List<Lpn> lpns = new ArrayList<>();

    @OneToMany(mappedBy = "directTruckload", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    private List<CustomerOrder> directOrders = new ArrayList<>();
}
