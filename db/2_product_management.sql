-- ============================================================
-- PRODUCT MANAGEMENT — schema additions
-- Run against ecommerce_db (after the base schema).
-- ============================================================
USE ecommerce_db;

-- 1) Extend products with type + brand -------------------------------------
ALTER TABLE products
    ADD COLUMN product_type VARCHAR(100) NULL AFTER name,
    ADD COLUMN brand        VARCHAR(150) NULL AFTER product_type;

-- 2) Arbitrary product attributes (key/value) ------------------------------
CREATE TABLE IF NOT EXISTS product_attributes (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    attr_name  VARCHAR(100) NOT NULL,
    attr_value VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_product_attributes_product (product_id),
    CONSTRAINT fk_product_attributes_product
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

-- 3) Product variants (color / size combinations) --------------------------
CREATE TABLE IF NOT EXISTS product_variants (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    product_id     BIGINT NOT NULL,
    sku            VARCHAR(120) NOT NULL,
    color          VARCHAR(60),
    size           VARCHAR(60),
    price          DECIMAL(12,2),         -- NULL => inherit product price
    discount_price DECIMAL(12,2),
    stock_quantity INT NOT NULL DEFAULT 0,
    status         VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_variants_sku (sku),
    KEY idx_product_variants_product (product_id),
    CONSTRAINT fk_product_variants_product
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT chk_product_variants_stock CHECK (stock_quantity >= 0),
    CONSTRAINT chk_product_variants_status
        CHECK (status IN ('ACTIVE','INACTIVE','OUT_OF_STOCK'))
);

-- 4) Let an image optionally belong to a specific variant (e.g. a colour) --
ALTER TABLE product_images
    ADD COLUMN variant_id BIGINT NULL AFTER product_id,
    ADD CONSTRAINT fk_product_images_variant
        FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE CASCADE;

-- 5) A few starter categories (safe to re-run) -----------------------------
INSERT INTO categories (name, description) VALUES
    ('Apparel','Clothing and wearables'),
    ('Footwear','Shoes and sneakers'),
    ('Electronics','Devices and gadgets')
ON DUPLICATE KEY UPDATE name = name;
