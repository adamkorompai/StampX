package com.stampx.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shops")
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "primary_color", length = 7)
    private String primaryColor;

    @Column(name = "stamp_goal", nullable = false)
    private int stampGoal;

    @Column(name = "reward_description", columnDefinition = "TEXT")
    private String rewardDescription;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** Stores SHA-256(rawApiKey) as lowercase hex. Raw key given once on registration. */
    @Column(name = "api_key", nullable = false, unique = true)
    private String apiKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Shop() {}

    public Shop(UUID id, String name, String slug, String logoUrl, String primaryColor,
                int stampGoal, String rewardDescription, String email,
                String passwordHash, String apiKey, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.logoUrl = logoUrl;
        this.primaryColor = primaryColor;
        this.stampGoal = stampGoal;
        this.rewardDescription = rewardDescription;
        this.email = email;
        this.passwordHash = passwordHash;
        this.apiKey = apiKey;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getLogoUrl() { return logoUrl; }
    public String getPrimaryColor() { return primaryColor; }
    public int getStampGoal() { return stampGoal; }
    public String getRewardDescription() { return rewardDescription; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getApiKey() { return apiKey; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(UUID id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setSlug(String slug) { this.slug = slug; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }
    public void setStampGoal(int stampGoal) { this.stampGoal = stampGoal; }
    public void setRewardDescription(String rewardDescription) { this.rewardDescription = rewardDescription; }
    public void setEmail(String email) { this.email = email; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
