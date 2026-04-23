CREATE TABLE stamp_events (
    id          UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id     UUID      NOT NULL REFERENCES shops(id)     ON DELETE CASCADE,
    customer_id UUID      NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    stamped_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stamp_events_shop_id    ON stamp_events(shop_id);
CREATE INDEX idx_stamp_events_stamped_at ON stamp_events(stamped_at);
