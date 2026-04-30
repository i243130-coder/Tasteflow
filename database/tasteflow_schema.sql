-- ============================================================
-- TasteFlow Restaurant Management Platform
-- Complete Database Schema (Raw MySQL / No ORM)
-- ============================================================

DROP DATABASE IF EXISTS tasteflow;
CREATE DATABASE tasteflow CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE tasteflow;

-- ============================================================
-- 1. BRANCH
-- ============================================================
CREATE TABLE branch (
    branch_id   INT AUTO_INCREMENT PRIMARY KEY,
    branch_name VARCHAR(100)  NOT NULL,
    address     VARCHAR(255)  NOT NULL,
    phone       VARCHAR(20),
    is_active   BOOLEAN       DEFAULT TRUE,
    created_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ============================================================
-- 2. USER (Staff / Employees)
--    Reserved word → backtick-quoted everywhere
-- ============================================================
CREATE TABLE `user` (
    user_id       INT AUTO_INCREMENT PRIMARY KEY,
    branch_id     INT            NOT NULL,
    username      VARCHAR(50)    NOT NULL UNIQUE,
    password_hash VARCHAR(255)   NOT NULL,
    full_name     VARCHAR(100)   NOT NULL,
    role          ENUM('ADMIN','MANAGER','CHEF','WAITER','CASHIER','DELIVERY_DRIVER') NOT NULL,
    phone         VARCHAR(20),
    email         VARCHAR(100),
    is_active     BOOLEAN        DEFAULT TRUE,
    created_at    TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (branch_id) REFERENCES branch(branch_id)
) ENGINE=InnoDB;

-- ============================================================
-- 3. CUSTOMER (Loyalty Programme)
-- ============================================================
CREATE TABLE customer (
    customer_id    INT AUTO_INCREMENT PRIMARY KEY,
    full_name      VARCHAR(100)  NOT NULL,
    phone          VARCHAR(20)   NOT NULL UNIQUE,
    email          VARCHAR(100),
    loyalty_points INT           DEFAULT 0,
    loyalty_tier   ENUM('BRONZE','SILVER','GOLD','PLATINUM') DEFAULT 'BRONZE',
    created_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_points_non_negative CHECK (loyalty_points >= 0)
) ENGINE=InnoDB;

-- ============================================================
-- 4. DINING TABLE
-- ============================================================
CREATE TABLE dining_table (
    table_id     INT AUTO_INCREMENT PRIMARY KEY,
    branch_id    INT  NOT NULL,
    table_number INT  NOT NULL,
    capacity     INT  NOT NULL,
    status       ENUM('AVAILABLE','OCCUPIED','RESERVED','OUT_OF_SERVICE') DEFAULT 'AVAILABLE',

    UNIQUE KEY uq_branch_table (branch_id, table_number),
    FOREIGN KEY (branch_id) REFERENCES branch(branch_id)
) ENGINE=InnoDB;

-- ============================================================
-- 5. ALLERGEN
-- ============================================================
CREATE TABLE allergen (
    allergen_id   INT AUTO_INCREMENT PRIMARY KEY,
    allergen_name VARCHAR(50)  NOT NULL UNIQUE,
    severity      ENUM('LOW','MEDIUM','HIGH') DEFAULT 'MEDIUM'
) ENGINE=InnoDB;

-- ============================================================
-- 6. MENU CATEGORY
-- ============================================================
CREATE TABLE menu_category (
    category_id   INT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL,
    display_order INT          DEFAULT 0,
    is_active     BOOLEAN      DEFAULT TRUE
) ENGINE=InnoDB;

-- ============================================================
-- 7. INGREDIENT (Inventory)
-- ============================================================
CREATE TABLE ingredient (
    ingredient_id   INT AUTO_INCREMENT PRIMARY KEY,
    ingredient_name VARCHAR(100)   NOT NULL,
    unit            VARCHAR(20)    NOT NULL,
    current_stock   DECIMAL(10,3)  NOT NULL DEFAULT 0.000,
    reorder_level   DECIMAL(10,3)  NOT NULL DEFAULT 0.000,
    cost_per_unit   DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    is_active       BOOLEAN        DEFAULT TRUE,
    last_updated    TIMESTAMP      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_stock_non_negative CHECK (current_stock >= 0)
) ENGINE=InnoDB;

-- ============================================================
-- 8. MENU ITEM
-- ============================================================
CREATE TABLE menu_item (
    item_id              INT AUTO_INCREMENT PRIMARY KEY,
    category_id          INT            NOT NULL,
    item_name            VARCHAR(100)   NOT NULL,
    description          TEXT,
    price                DECIMAL(10,2)  NOT NULL,
    image_url            VARCHAR(255),
    is_available         BOOLEAN        DEFAULT TRUE,
    preparation_time_min INT            DEFAULT 15,
    created_at           TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (category_id) REFERENCES menu_category(category_id)
) ENGINE=InnoDB;

-- ============================================================
-- 9. MENU ITEM ↔ ALLERGEN  (M:N)
-- ============================================================
CREATE TABLE menu_item_allergen (
    item_id     INT NOT NULL,
    allergen_id INT NOT NULL,

    PRIMARY KEY (item_id, allergen_id),
    FOREIGN KEY (item_id)     REFERENCES menu_item(item_id),
    FOREIGN KEY (allergen_id) REFERENCES allergen(allergen_id)
) ENGINE=InnoDB;

-- ============================================================
-- 10. RECIPE INGREDIENT  (Menu Item → Ingredients with qty)
-- ============================================================
CREATE TABLE recipe_ingredient (
    recipe_id         INT AUTO_INCREMENT PRIMARY KEY,
    item_id           INT            NOT NULL,
    ingredient_id     INT            NOT NULL,
    quantity_required DECIMAL(10,3)  NOT NULL,

    UNIQUE KEY uq_item_ingredient (item_id, ingredient_id),
    FOREIGN KEY (item_id)       REFERENCES menu_item(item_id),
    FOREIGN KEY (ingredient_id) REFERENCES ingredient(ingredient_id)
) ENGINE=InnoDB;

-- ============================================================
-- 11. SUPPLIER
-- ============================================================
CREATE TABLE supplier (
    supplier_id    INT AUTO_INCREMENT PRIMARY KEY,
    supplier_name  VARCHAR(100) NOT NULL,
    contact_person VARCHAR(100),
    phone          VARCHAR(20),
    email          VARCHAR(100),
    address        VARCHAR(255),
    is_active      BOOLEAN      DEFAULT TRUE,
    created_at     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ============================================================
-- 12. SUPPLIER ↔ INGREDIENT  (M:N with pricing)
-- ============================================================
CREATE TABLE supplier_ingredient (
    supplier_id   INT NOT NULL,
    ingredient_id INT NOT NULL,
    unit_price    DECIMAL(10,2) NOT NULL,
    lead_time_days INT DEFAULT 1,

    PRIMARY KEY (supplier_id, ingredient_id),
    FOREIGN KEY (supplier_id)   REFERENCES supplier(supplier_id),
    FOREIGN KEY (ingredient_id) REFERENCES ingredient(ingredient_id)
) ENGINE=InnoDB;

-- ============================================================
-- 13. PURCHASE ORDER
-- ============================================================
CREATE TABLE purchase_order (
    po_id              INT AUTO_INCREMENT PRIMARY KEY,
    supplier_id        INT NOT NULL,
    branch_id          INT NOT NULL,
    ordered_by         INT NOT NULL,
    order_date         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    expected_delivery  DATE,
    status             ENUM('DRAFT','SUBMITTED','PARTIALLY_RECEIVED','RECEIVED','CANCELLED') DEFAULT 'DRAFT',
    total_amount       DECIMAL(12,2) DEFAULT 0.00,
    notes              TEXT,

    FOREIGN KEY (supplier_id) REFERENCES supplier(supplier_id),
    FOREIGN KEY (branch_id)   REFERENCES branch(branch_id),
    FOREIGN KEY (ordered_by)  REFERENCES `user`(user_id)
) ENGINE=InnoDB;

-- ============================================================
-- 14. PURCHASE ORDER ITEM
-- ============================================================
CREATE TABLE purchase_order_item (
    po_item_id        INT AUTO_INCREMENT PRIMARY KEY,
    po_id             INT            NOT NULL,
    ingredient_id     INT            NOT NULL,
    quantity_ordered  DECIMAL(10,3)  NOT NULL,
    quantity_received DECIMAL(10,3)  DEFAULT 0.000,
    unit_price        DECIMAL(10,2)  NOT NULL,

    FOREIGN KEY (po_id)          REFERENCES purchase_order(po_id),
    FOREIGN KEY (ingredient_id)  REFERENCES ingredient(ingredient_id)
) ENGINE=InnoDB;

-- ============================================================
-- 15. RESERVATION
--     UNIQUE constraint prevents double-booking on same
--     (table, date, start_time). Overlap validation is
--     enforced at DAO layer with SELECT … FOR UPDATE.
-- ============================================================
CREATE TABLE reservation (
    reservation_id   INT AUTO_INCREMENT PRIMARY KEY,
    table_id         INT          NOT NULL,
    customer_id      INT,
    guest_name       VARCHAR(100) NOT NULL,
    guest_phone      VARCHAR(20)  NOT NULL,
    guest_count      INT          NOT NULL,
    reservation_date DATE         NOT NULL,
    start_time       TIME         NOT NULL,
    end_time         TIME         NOT NULL,
    status           ENUM('CONFIRMED','SEATED','COMPLETED','CANCELLED','NO_SHOW') DEFAULT 'CONFIRMED',
    special_requests TEXT,
    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uq_table_datetime (table_id, reservation_date, start_time),
    FOREIGN KEY (table_id)    REFERENCES dining_table(table_id),
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id)
) ENGINE=InnoDB;

-- ============================================================
-- 16. ORDER
--     Covers DINE_IN, TAKEAWAY, DELIVERY, and PRE_ORDER types
-- ============================================================
CREATE TABLE `order` (
    order_id              INT AUTO_INCREMENT PRIMARY KEY,
    branch_id             INT  NOT NULL,
    table_id              INT,
    customer_id           INT,
    reservation_id        INT,
    waiter_id             INT,
    order_type            ENUM('DINE_IN','TAKEAWAY','DELIVERY','PRE_ORDER') NOT NULL,
    status                ENUM('PENDING','IN_PROGRESS','READY','SERVED','COMPLETED','CANCELLED') DEFAULT 'PENDING',
    subtotal              DECIMAL(12,2) DEFAULT 0.00,
    tax                   DECIMAL(12,2) DEFAULT 0.00,
    discount              DECIMAL(12,2) DEFAULT 0.00,
    loyalty_points_redeemed INT          DEFAULT 0,
    total                 DECIMAL(12,2) DEFAULT 0.00,
    special_instructions  TEXT,
    created_at            TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    completed_at          TIMESTAMP     NULL,

    FOREIGN KEY (branch_id)      REFERENCES branch(branch_id),
    FOREIGN KEY (table_id)       REFERENCES dining_table(table_id),
    FOREIGN KEY (customer_id)    REFERENCES customer(customer_id),
    FOREIGN KEY (reservation_id) REFERENCES reservation(reservation_id),
    FOREIGN KEY (waiter_id)      REFERENCES `user`(user_id)
) ENGINE=InnoDB;

-- ============================================================
-- 17. ORDER ITEM
-- ============================================================
CREATE TABLE order_item (
    order_item_id    INT AUTO_INCREMENT PRIMARY KEY,
    order_id         INT            NOT NULL,
    item_id          INT            NOT NULL,
    quantity         INT            NOT NULL DEFAULT 1,
    unit_price       DECIMAL(10,2)  NOT NULL,
    subtotal         DECIMAL(10,2)  NOT NULL,
    special_requests TEXT,
    status           ENUM('QUEUED','PREPARING','READY','SERVED','CANCELLED') DEFAULT 'QUEUED',
    priority         ENUM('NORMAL','RUSH','VIP') DEFAULT 'NORMAL',
    sent_to_kds_at   TIMESTAMP      NULL,
    ready_at         TIMESTAMP      NULL,

    FOREIGN KEY (order_id) REFERENCES `order`(order_id),
    FOREIGN KEY (item_id)  REFERENCES menu_item(item_id)
) ENGINE=InnoDB;

-- ============================================================
-- 18. KDS TICKET  (Kitchen Display System)
--     Supports allergen warnings, chef ack, correction window
-- ============================================================
CREATE TABLE kds_ticket (
    ticket_id             INT AUTO_INCREMENT PRIMARY KEY,
    order_item_id         INT       NOT NULL,
    station               ENUM('GRILL','FRY','SALAD','DESSERT','BEVERAGE','GENERAL') DEFAULT 'GENERAL',
    status                ENUM('PENDING','IN_PROGRESS','READY','RECALLED') DEFAULT 'PENDING',
    has_allergen_warning  BOOLEAN   DEFAULT FALSE,
    allergen_acknowledged BOOLEAN   DEFAULT FALSE,
    acknowledged_by       INT,
    acknowledged_at       TIMESTAMP NULL,
    correction_window_end TIMESTAMP NULL,
    created_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at          TIMESTAMP NULL,

    FOREIGN KEY (order_item_id) REFERENCES order_item(order_item_id),
    FOREIGN KEY (acknowledged_by) REFERENCES `user`(user_id)
) ENGINE=InnoDB;

-- ============================================================
-- 19. KDS ALLERGEN ACKNOWLEDGEMENT LOG
-- ============================================================
CREATE TABLE kds_allergen_ack (
    ack_id          INT AUTO_INCREMENT PRIMARY KEY,
    ticket_id       INT NOT NULL,
    allergen_id     INT NOT NULL,
    chef_id         INT NOT NULL,
    acknowledged_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (ticket_id)   REFERENCES kds_ticket(ticket_id),
    FOREIGN KEY (allergen_id) REFERENCES allergen(allergen_id),
    FOREIGN KEY (chef_id)     REFERENCES `user`(user_id)
) ENGINE=InnoDB;

-- ============================================================
-- 20. STAFF SHIFT
--     Overlap prevention: enforced at DAO layer using
--     SELECT … FOR UPDATE before INSERT to guarantee no
--     overlapping shift exists for same user + branch.
-- ============================================================
CREATE TABLE staff_shift (
    shift_id      INT AUTO_INCREMENT PRIMARY KEY,
    user_id       INT  NOT NULL,
    branch_id     INT  NOT NULL,
    shift_date    DATE NOT NULL,
    start_time    TIME NOT NULL,
    end_time      TIME NOT NULL,
    role_on_shift ENUM('CHEF','WAITER','CASHIER','MANAGER','DELIVERY_DRIVER') NOT NULL,
    status        ENUM('SCHEDULED','CHECKED_IN','CHECKED_OUT','ABSENT') DEFAULT 'SCHEDULED',
    notes         TEXT,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id)   REFERENCES `user`(user_id),
    FOREIGN KEY (branch_id) REFERENCES branch(branch_id),

    INDEX idx_shift_user_date (user_id, branch_id, shift_date)
) ENGINE=InnoDB;

-- ============================================================
-- 21. LOYALTY TRANSACTION
-- ============================================================
CREATE TABLE loyalty_transaction (
    transaction_id   INT AUTO_INCREMENT PRIMARY KEY,
    customer_id      INT NOT NULL,
    order_id         INT,
    transaction_type ENUM('EARNED','REDEEMED','ADJUSTED','EXPIRED') NOT NULL,
    points           INT NOT NULL,
    balance_after    INT NOT NULL,
    description      VARCHAR(255),
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    FOREIGN KEY (order_id)    REFERENCES `order`(order_id),

    CONSTRAINT chk_balance_non_negative CHECK (balance_after >= 0)
) ENGINE=InnoDB;

-- ============================================================
-- 22. DELIVERY ORDER
-- ============================================================
CREATE TABLE delivery_order (
    delivery_id             INT AUTO_INCREMENT PRIMARY KEY,
    order_id                INT NOT NULL UNIQUE,
    driver_id               INT,
    delivery_address        VARCHAR(255) NOT NULL,
    delivery_phone          VARCHAR(20)  NOT NULL,
    estimated_delivery_time TIMESTAMP    NULL,
    actual_delivery_time    TIMESTAMP    NULL,
    status                  ENUM('PENDING_ASSIGNMENT','ASSIGNED','PICKED_UP','IN_TRANSIT','DELIVERED','FAILED','RETURNED') DEFAULT 'PENDING_ASSIGNMENT',
    current_lat             DECIMAL(10,7),
    current_lng             DECIMAL(10,7),
    distance_km             DECIMAL(6,2),
    delivery_fee            DECIMAL(10,2) DEFAULT 0.00,
    notes                   TEXT,
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (order_id)  REFERENCES `order`(order_id),
    FOREIGN KEY (driver_id) REFERENCES `user`(user_id)
) ENGINE=InnoDB;

-- ============================================================
-- 23. DELIVERY STATUS LOG  (real-time tracking audit trail)
-- ============================================================
CREATE TABLE delivery_status_log (
    log_id      INT AUTO_INCREMENT PRIMARY KEY,
    delivery_id INT NOT NULL,
    status      ENUM('PENDING_ASSIGNMENT','ASSIGNED','PICKED_UP','IN_TRANSIT','DELIVERED','FAILED','RETURNED') NOT NULL,
    latitude    DECIMAL(10,7),
    longitude   DECIMAL(10,7),
    note        VARCHAR(255),
    logged_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (delivery_id) REFERENCES delivery_order(delivery_id)
) ENGINE=InnoDB;

-- ============================================================
-- 24. INVENTORY TRANSACTION  (audit trail for stock changes)
--     Atomic deductions use SELECT … FOR UPDATE on ingredient
--     row inside a transaction before INSERT here.
-- ============================================================
CREATE TABLE inventory_transaction (
    txn_id           INT AUTO_INCREMENT PRIMARY KEY,
    ingredient_id    INT            NOT NULL,
    order_id         INT,
    po_id            INT,
    transaction_type ENUM('ORDER_DEDUCTION','PO_RECEIPT','MANUAL_ADJUSTMENT','WASTE','RETURN') NOT NULL,
    quantity_change  DECIMAL(10,3)  NOT NULL,
    stock_after      DECIMAL(10,3)  NOT NULL,
    performed_by     INT,
    created_at       TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (ingredient_id) REFERENCES ingredient(ingredient_id),
    FOREIGN KEY (order_id)      REFERENCES `order`(order_id),
    FOREIGN KEY (po_id)         REFERENCES purchase_order(po_id),
    FOREIGN KEY (performed_by)  REFERENCES `user`(user_id)
) ENGINE=InnoDB;

-- ============================================================
-- 25. PAYMENT
-- ============================================================
CREATE TABLE payment (
    payment_id     INT AUTO_INCREMENT PRIMARY KEY,
    order_id       INT NOT NULL,
    payment_method ENUM('CASH','CREDIT_CARD','DEBIT_CARD','MOBILE_WALLET','LOYALTY_POINTS','SPLIT') NOT NULL,
    amount         DECIMAL(12,2) NOT NULL,
    tip            DECIMAL(10,2) DEFAULT 0.00,
    status         ENUM('PENDING','COMPLETED','REFUNDED','FAILED') DEFAULT 'PENDING',
    processed_by   INT,
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (order_id)     REFERENCES `order`(order_id),
    FOREIGN KEY (processed_by) REFERENCES `user`(user_id)
) ENGINE=InnoDB;

-- ============================================================
-- SEED DATA: Common Allergens
-- ============================================================
INSERT INTO allergen (allergen_name, severity) VALUES
    ('Gluten',    'HIGH'),
    ('Dairy',     'HIGH'),
    ('Nuts',      'HIGH'),
    ('Shellfish', 'HIGH'),
    ('Eggs',      'MEDIUM'),
    ('Soy',       'MEDIUM'),
    ('Fish',      'HIGH'),
    ('Sesame',    'MEDIUM');
