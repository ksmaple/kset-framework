CREATE TABLE IF NOT EXISTS demo_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    create_time DATETIME,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0
);

INSERT INTO demo_user (name, create_time, update_time, deleted)
VALUES ('Alice', NOW(), NOW(), 0);
