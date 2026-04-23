CREATE TABLE customers (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id           UUID         NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    pass_serial       VARCHAR(255) NOT NULL UNIQUE,
    device_library_id VARCHAR(255),
    push_token        VARCHAR(255),
    stamp_count       INTEGER      NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_customers_shop_id    ON customers(shop_id);
CREATE INDEX idx_customers_pass_serial ON customers(pass_serial);
