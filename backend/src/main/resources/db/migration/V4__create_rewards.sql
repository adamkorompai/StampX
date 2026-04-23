CREATE TABLE rewards (
    id          UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id     UUID      NOT NULL REFERENCES shops(id)     ON DELETE CASCADE,
    customer_id UUID      NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    redeemed_at TIMESTAMP
);

CREATE INDEX idx_rewards_shop_id    ON rewards(shop_id);
CREATE INDEX idx_rewards_redeemed_at ON rewards(redeemed_at);
