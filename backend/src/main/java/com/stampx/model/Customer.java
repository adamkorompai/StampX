package com.stampx.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Column(name = "pass_serial", nullable = false, unique = true)
    private String passSerial;

    @Column(name = "device_library_id")
    private String deviceLibraryId;

    @Column(name = "push_token")
    private String pushToken;

    @ColumnDefault("0")
    @Column(name = "stamp_count", nullable = false)
    private int stampCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Customer() {}

    public UUID getId() { return id; }
    public Shop getShop() { return shop; }
    public String getPassSerial() { return passSerial; }
    public String getDeviceLibraryId() { return deviceLibraryId; }
    public String getPushToken() { return pushToken; }
    public int getStampCount() { return stampCount; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(UUID id) { this.id = id; }
    public void setShop(Shop shop) { this.shop = shop; }
    public void setPassSerial(String passSerial) { this.passSerial = passSerial; }
    public void setDeviceLibraryId(String deviceLibraryId) { this.deviceLibraryId = deviceLibraryId; }
    public void setPushToken(String pushToken) { this.pushToken = pushToken; }
    public void setStampCount(int stampCount) { this.stampCount = stampCount; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
