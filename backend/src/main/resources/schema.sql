-- Auth (ported from SPRE Users + session tokens)
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    better_password VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
    token VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON sessions (expires_at);


-- Market
CREATE TABLE IF NOT EXISTS market_groups (
    market_group_id   INT PRIMARY KEY,
    parent_group_id   INT NULL REFERENCES market_groups (market_group_id),
    market_group_name VARCHAR(255) NOT NULL,
    description       TEXT NULL,
    has_types         BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS inv_types (
    type_id         INT PRIMARY KEY,
    type_name       VARCHAR(255) NOT NULL,
    description     TEXT NULL,
    base_price      DECIMAL(20, 2) NULL,
    published       BOOLEAN NOT NULL DEFAULT TRUE,
    market_group_id INT NULL REFERENCES market_groups (market_group_id)
);

CREATE TABLE IF NOT EXISTS locations (
    location_id        INT PRIMARY KEY,
    parent_location_id INT NULL REFERENCES locations (location_id),
    location_name      VARCHAR(255) NOT NULL,
    description        TEXT NULL
);

CREATE TABLE IF NOT EXISTS sources (
    source_id   INT PRIMARY KEY,
    source_code VARCHAR(64) NOT NULL UNIQUE,
    source_name VARCHAR(255) NOT NULL,
    base_url    TEXT NULL
);

CREATE TABLE IF NOT EXISTS market_orders (
    order_id            BIGSERIAL PRIMARY KEY,
    type_id             INT NOT NULL REFERENCES inv_types (type_id),
    location_id         INT NOT NULL REFERENCES locations (location_id),
    source_id           INT NOT NULL REFERENCES sources (source_id),
    side                VARCHAR(4) NOT NULL CHECK (side IN ('buy', 'sell')),
    price               DECIMAL(20, 2) NOT NULL CHECK (price >= 0),
    quantity            INT NOT NULL CHECK (quantity >= 0),
    quantity_initial    INT NOT NULL CHECK (quantity_initial > 0),
    status              VARCHAR(16) NOT NULL DEFAULT 'open'
                        CHECK (status IN ('open', 'partial', 'filled', 'cancelled')),
    external_url        TEXT NULL,
    external_listing_id VARCHAR(255) NULL,
    owner_kind          VARCHAR(32) NOT NULL
                        CHECK (owner_kind IN ('user', 'market_maker', 'external')),
    owner_ref           VARCHAR(255) NULL,
    user_id             BIGINT NULL REFERENCES users (id),
    expires_at          TIMESTAMP WITH TIME ZONE NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_market_orders_book
    ON market_orders (type_id, location_id, side, status, price);

CREATE INDEX IF NOT EXISTS idx_market_orders_external
    ON market_orders (source_id, external_listing_id);

CREATE INDEX IF NOT EXISTS idx_market_orders_user_id
    ON market_orders (user_id);

CREATE INDEX IF NOT EXISTS idx_market_orders_expires_at
    ON market_orders (expires_at);

CREATE TABLE IF NOT EXISTS market_fills (
    fill_id       BIGSERIAL PRIMARY KEY,
    type_id       INT NOT NULL REFERENCES inv_types (type_id),
    location_id   INT NOT NULL REFERENCES locations (location_id),
    buy_order_id  BIGINT NOT NULL REFERENCES market_orders (order_id),
    sell_order_id BIGINT NOT NULL REFERENCES market_orders (order_id),
    price         DECIMAL(20, 2) NOT NULL CHECK (price >= 0),
    quantity      INT NOT NULL CHECK (quantity > 0),
    filled_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_market_fills_type_time
    ON market_fills (type_id, location_id, filled_at);

CREATE TABLE IF NOT EXISTS market_prices (
    type_id        INT NOT NULL REFERENCES inv_types (type_id),
    location_id    INT NOT NULL REFERENCES locations (location_id),
    adjusted_price DECIMAL(20, 2) NULL,
    average_price  DECIMAL(20, 2) NULL,
    sample_size    INT NOT NULL DEFAULT 0,
    PRIMARY KEY (type_id, location_id)
);

CREATE TABLE IF NOT EXISTS market_history (
    location_id INT NOT NULL REFERENCES locations (location_id),
    type_id     INT NOT NULL REFERENCES inv_types (type_id),
    date        DATE NOT NULL,
    low_price   DECIMAL(20, 2) NOT NULL,
    high_price  DECIMAL(20, 2) NOT NULL,
    avg_price   DECIMAL(20, 2) NOT NULL,
    volume      BIGINT NOT NULL DEFAULT 0,
    orders      BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (location_id, type_id, date)
);


-- Basic mock table entries

INSERT INTO sources (source_id, source_code, source_name, base_url)
SELECT 1, 'ebay', 'eBay', 'https://www.ebay.co.uk'
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE source_id = 1);

INSERT INTO sources (source_id, source_code, source_name, base_url)
SELECT 2, 'vinted', 'Vinted', 'https://www.vinted.co.uk'
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE source_id = 2);

INSERT INTO sources (source_id, source_code, source_name, base_url)
SELECT 3, 'gumtree', 'Gumtree', 'https://www.gumtree.com'
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE source_id = 3);

INSERT INTO sources (source_id, source_code, source_name, base_url)
SELECT 4, 'facebook', 'Facebook Marketplace', 'https://www.facebook.com/marketplace'
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE source_id = 4);

INSERT INTO sources (source_id, source_code, source_name, base_url)
SELECT 5, 'amazon', 'Amazon', 'https://www.amazon.co.uk'
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE source_id = 5);

INSERT INTO sources (source_id, source_code, source_name, base_url)
SELECT 6, 'mock_mm', 'Market Maker', NULL
WHERE NOT EXISTS (SELECT 1 FROM sources WHERE source_id = 6);

INSERT INTO locations (location_id, parent_location_id, location_name, description)
SELECT 1, NULL, 'UK', 'United Kingdom (postage / national)'
WHERE NOT EXISTS (SELECT 1 FROM locations WHERE location_id = 1);

INSERT INTO locations (location_id, parent_location_id, location_name, description)
SELECT 2, 1, 'London', 'Greater London'
WHERE NOT EXISTS (SELECT 1 FROM locations WHERE location_id = 2);

INSERT INTO locations (location_id, parent_location_id, location_name, description)
SELECT 3, NULL, 'EU', 'European Union (cross-border)'
WHERE NOT EXISTS (SELECT 1 FROM locations WHERE location_id = 3);

INSERT INTO locations (location_id, parent_location_id, location_name, description)
SELECT 4, 3, 'Germany', 'Germany'
WHERE NOT EXISTS (SELECT 1 FROM locations WHERE location_id = 4);

INSERT INTO locations (location_id, parent_location_id, location_name, description)
SELECT 5, 3, 'France', 'France'
WHERE NOT EXISTS (SELECT 1 FROM locations WHERE location_id = 5);
