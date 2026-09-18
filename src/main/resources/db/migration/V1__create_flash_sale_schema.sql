-- Day 1：活动是库存配置源，订单表提前建立最终唯一约束供后续异步落单使用。
CREATE TABLE flash_sale_activity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(100) NOT NULL,
    sku_code VARCHAR(64) NOT NULL,
    original_price DECIMAL(12, 2) NOT NULL,
    flash_price DECIMAL(12, 2) NOT NULL,
    total_stock INT NOT NULL,
    available_stock INT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_flash_sale_activity_sku_time (sku_code, start_time),
    KEY idx_flash_sale_activity_public (status, start_time, end_time),
    CONSTRAINT ck_flash_sale_activity_price CHECK (
        original_price >= 0 AND flash_price >= 0 AND flash_price <= original_price
    ),
    CONSTRAINT ck_flash_sale_activity_stock CHECK (
        total_stock >= 0 AND available_stock >= 0 AND available_stock <= total_stock
    ),
    CONSTRAINT ck_flash_sale_activity_time CHECK (end_time > start_time)
);

CREATE TABLE flash_sale_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(32) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    sku_code VARCHAR(64) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    amount DECIMAL(12, 2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'CREATED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_flash_sale_order_no (order_no),
    UNIQUE KEY uk_flash_sale_order_request (request_id),
    UNIQUE KEY uk_flash_sale_order_user_activity (activity_id, user_id),
    KEY idx_flash_sale_order_user_created (user_id, created_at),
    CONSTRAINT fk_flash_sale_order_activity
        FOREIGN KEY (activity_id) REFERENCES flash_sale_activity(id),
    CONSTRAINT ck_flash_sale_order_quantity CHECK (quantity = 1),
    CONSTRAINT ck_flash_sale_order_amount CHECK (amount >= 0)
);

-- 本地演示数据让 Day 1 启动后即可验证查询接口。
INSERT INTO flash_sale_activity (
    title, sku_code, original_price, flash_price,
    total_stock, available_stock, start_time, end_time, status
) VALUES (
    'FlashSale 首日演示活动', 'DEMO-SKU-001', 199.00, 59.90,
    100, 100, '2026-01-01 00:00:00', '2030-01-01 00:00:00', 'ONLINE'
);
