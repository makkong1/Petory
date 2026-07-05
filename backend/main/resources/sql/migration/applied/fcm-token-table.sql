CREATE TABLE IF NOT EXISTS fcm_token (
    idx          BIGINT       NOT NULL AUTO_INCREMENT,
    user_idx     BIGINT       NOT NULL,
    token        VARCHAR(512) NOT NULL,
    device_type  VARCHAR(10)  NOT NULL COMMENT 'ANDROID | IOS',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (idx),
    UNIQUE KEY uk_fcm_token_token (token),
    CONSTRAINT fk_fcm_token_user FOREIGN KEY (user_idx) REFERENCES users (idx) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
