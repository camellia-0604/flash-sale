-- Day 4：记录发布结果未知和死信消费失败，为补偿与对账提供持久化事实。
CREATE TABLE flash_sale_message_failure (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    stage VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    attempts INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_message_failure_request (request_id),
    KEY idx_message_failure_reconcile (status, updated_at)
);
