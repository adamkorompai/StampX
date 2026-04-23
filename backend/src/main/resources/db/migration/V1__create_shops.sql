CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE shops (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(255) NOT NULL,
    slug               VARCHAR(100) NOT NULL UNIQUE,
    logo_url           TEXT,
    primary_color      VARCHAR(7),
    stamp_goal         INTEGER      NOT NULL DEFAULT 10,
    reward_description TEXT,
    email              VARCHAR(255) NOT NULL UNIQUE,
    password_hash      VARCHAR(255) NOT NULL,
    api_key            VARCHAR(255) NOT NULL UNIQUE,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_shops_slug    ON shops(slug);
CREATE INDEX idx_shops_api_key ON shops(api_key);
