package com.stampx.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rewards")
public class Reward {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** Null means the reward is pending (not yet redeemed). */
    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    public Reward() {}

    public UUID getId() { return id; }
    public Shop getShop() { return shop; }
    public Customer getCustomer() { return customer; }
    public Instant getRedeemedAt() { return redeemedAt; }

    public void setId(UUID id) { this.id = id; }
    public void setShop(Shop shop) { this.shop = shop; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public void setRedeemedAt(Instant redeemedAt) { this.redeemedAt = redeemedAt; }
}
