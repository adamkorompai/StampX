package com.stampx.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stamp_events")
public class StampEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @CreationTimestamp
    @Column(name = "stamped_at", nullable = false, updatable = false)
    private Instant stampedAt;

    public StampEvent() {}

    public UUID getId() { return id; }
    public Shop getShop() { return shop; }
    public Customer getCustomer() { return customer; }
    public Instant getStampedAt() { return stampedAt; }

    public void setId(UUID id) { this.id = id; }
    public void setShop(Shop shop) { this.shop = shop; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public void setStampedAt(Instant stampedAt) { this.stampedAt = stampedAt; }
}
