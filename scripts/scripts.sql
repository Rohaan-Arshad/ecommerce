-- ============================================================

-- E-COMMERCE DATABASE

-- ============================================================

-- Database: ecommerce_db

-- Database Engine: MySQL 8+

--

-- Authentication:

--   LOCAL     -> Email + Password

--   GOOGLE    -> Google OAuth

--   MICROSOFT -> Microsoft OAuth

--

-- ============================================================


-- ============================================================

-- 1. DATABASE

-- ============================================================

CREATE DATABASE IF NOT EXISTS ecommerce_db

    CHARACTER SET utf8mb4

    COLLATE utf8mb4_unicode_ci;

USE ecommerce_db;


-- ============================================================

-- 2. USERS

-- ============================================================

CREATE TABLE users (

                       id BIGINT NOT NULL AUTO_INCREMENT,

                       first_name VARCHAR(100) NOT NULL,

                       last_name VARCHAR(100),

                       email VARCHAR(255) NOT NULL,

                       password VARCHAR(255),

                       phone VARCHAR(30),

    -- LOCAL / GOOGLE / MICROSOFT

                       auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',

    -- Google subject ID / Microsoft subject ID

                       provider_user_id VARCHAR(255),

    -- ACTIVE / INACTIVE / BLOCKED

                       status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

                       email_verified BOOLEAN NOT NULL DEFAULT FALSE,

                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP

                           ON UPDATE CURRENT_TIMESTAMP,

                       PRIMARY KEY (id),

                       UNIQUE KEY uk_users_email (email),

                       UNIQUE KEY uk_users_provider (

                           auth_provider,

                           provider_user_id

                           ),

                       CONSTRAINT chk_users_auth_provider

                           CHECK (

                               auth_provider IN (

                                                 'LOCAL',

                                                 'GOOGLE',

                                                 'MICROSOFT'

                                   )

                               ),

                       CONSTRAINT chk_users_status

                           CHECK (

                               status IN (

                                          'ACTIVE',

                                          'INACTIVE',

                                          'BLOCKED'

                                   )

                               )

);


-- ============================================================

-- 3. ROLES

-- ============================================================

CREATE TABLE roles (

                       id BIGINT NOT NULL AUTO_INCREMENT,

                       name VARCHAR(50) NOT NULL,

                       PRIMARY KEY (id),

                       UNIQUE KEY uk_roles_name (name)

);


-- ============================================================

-- 4. USER ROLES

-- ============================================================

CREATE TABLE user_roles (

                            user_id BIGINT NOT NULL,

                            role_id BIGINT NOT NULL,

                            PRIMARY KEY (user_id, role_id),

                            CONSTRAINT fk_user_roles_user

                                FOREIGN KEY (user_id)

                                    REFERENCES users(id)

                                    ON DELETE CASCADE,

                            CONSTRAINT fk_user_roles_role

                                FOREIGN KEY (role_id)

                                    REFERENCES roles(id)

                                    ON DELETE CASCADE

);


-- ============================================================

-- 5. ADDRESSES

-- ============================================================

CREATE TABLE addresses (

                           id BIGINT NOT NULL AUTO_INCREMENT,

                           user_id BIGINT NOT NULL,

                           address_line1 VARCHAR(255) NOT NULL,

                           address_line2 VARCHAR(255),

                           city VARCHAR(100) NOT NULL,

                           state VARCHAR(100),

                           postal_code VARCHAR(20),

                           country VARCHAR(100) NOT NULL,

                           is_default BOOLEAN NOT NULL DEFAULT FALSE,

                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                           PRIMARY KEY (id),

                           KEY idx_addresses_user_id (user_id),

                           CONSTRAINT fk_addresses_user

                               FOREIGN KEY (user_id)

                                   REFERENCES users(id)

                                   ON DELETE CASCADE

);


-- ============================================================

-- 6. CATEGORIES

-- ============================================================

CREATE TABLE categories (

                            id BIGINT NOT NULL AUTO_INCREMENT,

                            name VARCHAR(150) NOT NULL,

                            description VARCHAR(500),

                            status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP

                                ON UPDATE CURRENT_TIMESTAMP,

                            PRIMARY KEY (id),

                            UNIQUE KEY uk_categories_name (name),

                            CONSTRAINT chk_categories_status

                                CHECK (

                                    status IN (

                                               'ACTIVE',

                                               'INACTIVE'

                                        )

                                    )

);


-- ============================================================

-- 7. PRODUCTS

-- ============================================================

CREATE TABLE products (

                          id BIGINT NOT NULL AUTO_INCREMENT,

                          category_id BIGINT NOT NULL,

                          name VARCHAR(255) NOT NULL,

                          description TEXT,

                          sku VARCHAR(100) NOT NULL,

                          price DECIMAL(12,2) NOT NULL,

                          discount_price DECIMAL(12,2),

                          status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP

                              ON UPDATE CURRENT_TIMESTAMP,

                          PRIMARY KEY (id),

                          UNIQUE KEY uk_products_sku (sku),

                          KEY idx_products_category_id (category_id),

                          KEY idx_products_name (name),

                          KEY idx_products_status (status),

                          CONSTRAINT fk_products_category

                              FOREIGN KEY (category_id)

                                  REFERENCES categories(id),

                          CONSTRAINT chk_products_price

                              CHECK (price >= 0),

                          CONSTRAINT chk_products_discount_price

                              CHECK (

                                  discount_price IS NULL

                                      OR discount_price >= 0

                                  ),

                          CONSTRAINT chk_products_status

                              CHECK (

                                  status IN (

                                             'ACTIVE',

                                             'INACTIVE',

                                             'OUT_OF_STOCK'

                                      )

                                  )

);


-- ============================================================

-- 8. PRODUCT IMAGES

-- ============================================================

CREATE TABLE product_images (

                                id BIGINT NOT NULL AUTO_INCREMENT,

                                product_id BIGINT NOT NULL,

                                image_url VARCHAR(500) NOT NULL,

                                is_primary BOOLEAN NOT NULL DEFAULT FALSE,

                                display_order INT NOT NULL DEFAULT 0,

                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                PRIMARY KEY (id),

                                KEY idx_product_images_product_id (product_id),

                                CONSTRAINT fk_product_images_product

                                    FOREIGN KEY (product_id)

                                        REFERENCES products(id)

                                        ON DELETE CASCADE

);


-- ============================================================

-- 9. INVENTORY

-- ============================================================

CREATE TABLE inventory (

                           id BIGINT NOT NULL AUTO_INCREMENT,

                           product_id BIGINT NOT NULL,

                           quantity INT NOT NULL DEFAULT 0,

                           reserved_quantity INT NOT NULL DEFAULT 0,

                           reorder_level INT NOT NULL DEFAULT 10,

                           version BIGINT NOT NULL DEFAULT 0,

                           updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP

                               ON UPDATE CURRENT_TIMESTAMP,

                           PRIMARY KEY (id),

                           UNIQUE KEY uk_inventory_product (product_id),

                           CONSTRAINT fk_inventory_product

                               FOREIGN KEY (product_id)

                                   REFERENCES products(id)

                                   ON DELETE CASCADE,

                           CONSTRAINT chk_inventory_quantity

                               CHECK (quantity >= 0),

                           CONSTRAINT chk_inventory_reserved

                               CHECK (reserved_quantity >= 0),

                           CONSTRAINT chk_inventory_reorder

                               CHECK (reorder_level >= 0)

);


-- ============================================================

-- 10. CARTS

-- ============================================================

CREATE TABLE carts (

                       id BIGINT NOT NULL AUTO_INCREMENT,

                       user_id BIGINT NOT NULL,

                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP

                           ON UPDATE CURRENT_TIMESTAMP,

                       PRIMARY KEY (id),

                       UNIQUE KEY uk_carts_user (user_id),

                       CONSTRAINT fk_carts_user

                           FOREIGN KEY (user_id)

                               REFERENCES users(id)

                               ON DELETE CASCADE

);


-- ============================================================

-- 11. CART ITEMS

-- ============================================================

CREATE TABLE cart_items (

                            id BIGINT NOT NULL AUTO_INCREMENT,

                            cart_id BIGINT NOT NULL,

                            product_id BIGINT NOT NULL,

                            quantity INT NOT NULL,

                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP

                                ON UPDATE CURRENT_TIMESTAMP,

                            PRIMARY KEY (id),

                            UNIQUE KEY uk_cart_product (

                                cart_id,

                                product_id

                                ),

                            KEY idx_cart_items_product (product_id),

                            CONSTRAINT fk_cart_items_cart

                                FOREIGN KEY (cart_id)

                                    REFERENCES carts(id)

                                    ON DELETE CASCADE,

                            CONSTRAINT fk_cart_items_product

                                FOREIGN KEY (product_id)

                                    REFERENCES products(id),

                            CONSTRAINT chk_cart_items_quantity

                                CHECK (quantity > 0)

);


-- ============================================================

-- 12. ORDERS

-- ============================================================

CREATE TABLE orders (

                        id BIGINT NOT NULL AUTO_INCREMENT,

                        order_number VARCHAR(50) NOT NULL,

                        user_id BIGINT NOT NULL,

                        subtotal DECIMAL(12,2) NOT NULL,

                        discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0,

                        tax_amount DECIMAL(12,2) NOT NULL DEFAULT 0,

                        shipping_amount DECIMAL(12,2) NOT NULL DEFAULT 0,

                        total_amount DECIMAL(12,2) NOT NULL,

                        status VARCHAR(30) NOT NULL DEFAULT 'CREATED',

    -- Shipping address snapshot

                        shipping_address_line1 VARCHAR(255) NOT NULL,

                        shipping_address_line2 VARCHAR(255),

                        shipping_city VARCHAR(100) NOT NULL,

                        shipping_state VARCHAR(100),

                        shipping_postal_code VARCHAR(20),

                        shipping_country VARCHAR(100) NOT NULL,

                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP

                            ON UPDATE CURRENT_TIMESTAMP,

                        PRIMARY KEY (id),

                        UNIQUE KEY uk_orders_order_number (order_number),

                        KEY idx_orders_user_id (user_id),

                        KEY idx_orders_status (status),

                        KEY idx_orders_created_at (created_at),

                        CONSTRAINT fk_orders_user

                            FOREIGN KEY (user_id)

                                REFERENCES users(id),

                        CONSTRAINT chk_orders_amounts

                            CHECK (

                                subtotal >= 0

                                    AND discount_amount >= 0

                                    AND tax_amount >= 0

                                    AND shipping_amount >= 0

                                    AND total_amount >= 0

                                ),

                        CONSTRAINT chk_orders_status

                            CHECK (

                                status IN (

                                           'CREATED',

                                           'CONFIRMED',

                                           'PROCESSING',

                                           'SHIPPED',

                                           'DELIVERED',

                                           'CANCELLED',

                                           'REFUNDED'

                                    )

                                )

);


-- ============================================================

-- 13. ORDER ITEMS

-- ============================================================

CREATE TABLE order_items (

                             id BIGINT NOT NULL AUTO_INCREMENT,

                             order_id BIGINT NOT NULL,

                             product_id BIGINT NOT NULL,

    -- Historical product snapshot

                             product_name VARCHAR(255) NOT NULL,

                             product_sku VARCHAR(100) NOT NULL,

                             quantity INT NOT NULL,

                             unit_price DECIMAL(12,2) NOT NULL,

                             discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0,

                             total_price DECIMAL(12,2) NOT NULL,

                             PRIMARY KEY (id),

                             KEY idx_order_items_order_id (order_id),

                             KEY idx_order_items_product_id (product_id),

                             CONSTRAINT fk_order_items_order

                                 FOREIGN KEY (order_id)

                                     REFERENCES orders(id)

                                     ON DELETE CASCADE,

                             CONSTRAINT fk_order_items_product

                                 FOREIGN KEY (product_id)

                                     REFERENCES products(id),

                             CONSTRAINT chk_order_items_quantity

                                 CHECK (quantity > 0),

                             CONSTRAINT chk_order_items_prices

                                 CHECK (

                                     unit_price >= 0

                                         AND discount_amount >= 0

                                         AND total_price >= 0

                                     )

);


-- ============================================================

-- 14. PAYMENTS

-- ============================================================

CREATE TABLE payments (

                          id BIGINT NOT NULL AUTO_INCREMENT,

                          order_id BIGINT NOT NULL,

                          payment_reference VARCHAR(100),

                          amount DECIMAL(12,2) NOT NULL,

                          payment_method VARCHAR(30) NOT NULL,

                          status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

                          transaction_date TIMESTAMP NULL,

                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP

                              ON UPDATE CURRENT_TIMESTAMP,

                          PRIMARY KEY (id),

                          UNIQUE KEY uk_payments_order (order_id),

                          UNIQUE KEY uk_payments_reference (payment_reference),

                          CONSTRAINT fk_payments_order

                              FOREIGN KEY (order_id)

                                  REFERENCES orders(id),

                          CONSTRAINT chk_payments_amount

                              CHECK (amount >= 0),

                          CONSTRAINT chk_payments_method

                              CHECK (

                                  payment_method IN (

                                                     'CASH_ON_DELIVERY',

                                                     'CARD',

                                                     'BANK_TRANSFER',

                                                     'WALLET'

                                      )

                                  ),

                          CONSTRAINT chk_payments_status

                              CHECK (

                                  status IN (

                                             'PENDING',

                                             'SUCCESS',

                                             'FAILED',

                                             'REFUNDED'

                                      )

                                  )

);


-- ============================================================

-- 15. SHIPMENTS

-- ============================================================

CREATE TABLE shipments (

                           id BIGINT NOT NULL AUTO_INCREMENT,

                           order_id BIGINT NOT NULL,

                           tracking_number VARCHAR(100),

                           carrier VARCHAR(100),

                           status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

                           shipped_at TIMESTAMP NULL,

                           delivered_at TIMESTAMP NULL,

                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                           updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP

                               ON UPDATE CURRENT_TIMESTAMP,

                           PRIMARY KEY (id),

                           UNIQUE KEY uk_shipments_order (order_id),

                           UNIQUE KEY uk_shipments_tracking (tracking_number),

                           CONSTRAINT fk_shipments_order

                               FOREIGN KEY (order_id)

                                   REFERENCES orders(id),

                           CONSTRAINT chk_shipments_status

                               CHECK (

                                   status IN (

                                              'PENDING',

                                              'SHIPPED',

                                              'IN_TRANSIT',

                                              'DELIVERED',

                                              'CANCELLED'

                                       )

                                   )

);


-- ============================================================

-- 16. COUPONS

-- ============================================================

CREATE TABLE coupons (

                         id BIGINT NOT NULL AUTO_INCREMENT,

                         code VARCHAR(50) NOT NULL,

                         discount_type VARCHAR(20) NOT NULL,

                         discount_value DECIMAL(12,2) NOT NULL,

                         minimum_order_amount DECIMAL(12,2) NOT NULL DEFAULT 0,

                         maximum_discount_amount DECIMAL(12,2),

                         usage_limit INT,

                         used_count INT NOT NULL DEFAULT 0,

                         valid_from TIMESTAMP NOT NULL,

                         valid_until TIMESTAMP NOT NULL,

                         status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                         PRIMARY KEY (id),

                         UNIQUE KEY uk_coupons_code (code),

                         KEY idx_coupons_validity (

        valid_from,

        valid_until

    ),

                         CONSTRAINT chk_coupons_discount_type

                             CHECK (

                                 discount_type IN (

                                                   'PERCENTAGE',

                                                   'FIXED'

                                     )

                                 ),

                         CONSTRAINT chk_coupons_discount_value

                             CHECK (discount_value >= 0),

                         CONSTRAINT chk_coupons_minimum

                             CHECK (minimum_order_amount >= 0),

                         CONSTRAINT chk_coupons_usage

                             CHECK (

                                 used_count >= 0

                                     AND (

                                     usage_limit IS NULL

                                         OR usage_limit >= 0

                                     )

                                 ),

                         CONSTRAINT chk_coupons_status

                             CHECK (

                                 status IN (

                                            'ACTIVE',

                                            'INACTIVE',

                                            'EXPIRED'

                                     )

                                 )

);


-- ============================================================

-- 17. COUPON PRODUCTS

-- ============================================================

CREATE TABLE coupon_products (

                                 coupon_id BIGINT NOT NULL,

                                 product_id BIGINT NOT NULL,

                                 PRIMARY KEY (

                                              coupon_id,

                                              product_id

                                     ),

                                 CONSTRAINT fk_coupon_products_coupon

                                     FOREIGN KEY (coupon_id)

                                         REFERENCES coupons(id)

                                         ON DELETE CASCADE,

                                 CONSTRAINT fk_coupon_products_product

                                     FOREIGN KEY (product_id)

                                         REFERENCES products(id)

                                         ON DELETE CASCADE

);


-- ============================================================

-- 18. REVIEWS

-- ============================================================

CREATE TABLE reviews (

                         id BIGINT NOT NULL AUTO_INCREMENT,

                         product_id BIGINT NOT NULL,

                         user_id BIGINT NOT NULL,

                         rating TINYINT NOT NULL,

                         title VARCHAR(255),

                         comment TEXT,

                         status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                         updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP

                             ON UPDATE CURRENT_TIMESTAMP,

                         PRIMARY KEY (id),

                         UNIQUE KEY uk_review_user_product (

                             user_id,

                             product_id

                             ),

                         KEY idx_reviews_product_id (product_id),

                         CONSTRAINT fk_reviews_product

                             FOREIGN KEY (product_id)

                                 REFERENCES products(id)

                                 ON DELETE CASCADE,

                         CONSTRAINT fk_reviews_user

                             FOREIGN KEY (user_id)

                                 REFERENCES users(id),

                         CONSTRAINT chk_reviews_rating

                             CHECK (rating BETWEEN 1 AND 5),

                         CONSTRAINT chk_reviews_status

                             CHECK (

                                 status IN (

                                            'ACTIVE',

                                            'HIDDEN',

                                            'DELETED'

                                     )

                                 )

);


-- ============================================================

-- 19. NOTIFICATIONS

-- ============================================================

CREATE TABLE notifications (

                               id BIGINT NOT NULL AUTO_INCREMENT,

                               user_id BIGINT NOT NULL,

                               type VARCHAR(50) NOT NULL,

                               title VARCHAR(255) NOT NULL,

                               message TEXT NOT NULL,

                               is_read BOOLEAN NOT NULL DEFAULT FALSE,

                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                               PRIMARY KEY (id),

                               KEY idx_notifications_user (

        user_id

    ),

                               KEY idx_notifications_read (

        user_id,

        is_read

    ),

                               CONSTRAINT fk_notifications_user

                                   FOREIGN KEY (user_id)

                                       REFERENCES users(id)

                                       ON DELETE CASCADE

);


-- ============================================================

-- 20. AUDIT LOGS

-- ============================================================

CREATE TABLE audit_logs (

                            id BIGINT NOT NULL AUTO_INCREMENT,

                            user_id BIGINT,

                            action VARCHAR(100) NOT NULL,

                            entity_type VARCHAR(100),

                            entity_id BIGINT,

                            old_value JSON,

                            new_value JSON,

                            ip_address VARCHAR(45),

                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                            PRIMARY KEY (id),

                            KEY idx_audit_logs_user_id (user_id),

                            KEY idx_audit_logs_entity (

        entity_type,

        entity_id

    ),

                            KEY idx_audit_logs_created_at (

        created_at

    ),

                            CONSTRAINT fk_audit_logs_user

                                FOREIGN KEY (user_id)

                                    REFERENCES users(id)

                                    ON DELETE SET NULL

);


-- ============================================================

-- 21. INITIAL ROLES

-- ============================================================

INSERT INTO roles (name)

VALUES

    ('CUSTOMER'),

    ('ADMIN');


-- ============================================================

-- 22. SAMPLE ADMIN USER

-- ============================================================

-- IMPORTANT:

-- This is only a placeholder.

-- DO NOT use a plain-text password.

--

-- We will create the real admin through Spring Security

-- and BCrypt/Argon2 hashing.

--

-- Therefore, no admin user is inserted here.

-- ============================================================


-- ============================================================

-- 23. CHECK TABLES

-- ============================================================

SHOW TABLES;


-- ============================================================

-- 24. VERIFY ROLES

-- ============================================================

SELECT *

FROM roles;



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
