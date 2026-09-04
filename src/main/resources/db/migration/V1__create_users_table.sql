CREATE TABLE users
(
    id            UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    username      VARCHAR(50)  NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    avatar_url    VARCHAR(500),
    bio           VARCHAR(255),
    date_of_birth DATE,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    deleted_at    TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_users_email ON users (email);
CREATE UNIQUE INDEX idx_users_username ON users (username);
