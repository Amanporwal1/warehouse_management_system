package com.advatix.OMS.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
@Table(name = "orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 40)
    private String orderNumber;

    @Column(nullable = false, length = 120)
    private String customerName;

    @Column(nullable = false, length = 120)
    private String customerEmail;

    @Embedded
    private ShippingAddress shippingAddress;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false)
    private Boolean serviceable;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(length = 80)
    private String assignedPicker;

    @Column(length = 80)
    private String pickContainer;

    @Column(precision = 10, scale = 3)
    private BigDecimal packageWeight;

    @Column(length = 80)
    private String assignedBox;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lpn_id")
    private Lpn lpn;

    @Column(name = "shipment_id", length = 80)
    private String shipmentId;

    @Column(name = "tracking_number", length = 80)
    private String trackingNumber;

    @Column(name = "shipment_status", length = 60)
    private String shipmentStatus;

    @Column(name = "order_status_code")
    private Integer orderStatusCode;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "direct_truckload_id")
    private Truckload directTruckload;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BackOrder> backOrders = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TransactionHistory> transactionHistories = new ArrayList<>();

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void addBackOrder(BackOrder backOrder) {
        backOrders.add(backOrder);
        backOrder.setOrder(this);
    }

    public void addTransactionHistory(TransactionHistory transactionHistory) {
        transactionHistories.add(transactionHistory);
        transactionHistory.setOrder(this);
    }
}
