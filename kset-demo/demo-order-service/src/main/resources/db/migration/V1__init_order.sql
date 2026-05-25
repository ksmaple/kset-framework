CREATE TABLE IF NOT EXISTS demo_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    create_time DATETIME,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0
);

INSERT INTO demo_order (user_id, product_name, create_time, update_time, deleted)
VALUES (1, 'Sample Product', NOW(), NOW(), 0);
