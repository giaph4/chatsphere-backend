
CREATE TABLE user_settings
(
    user_id              UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    online_visibility    VARCHAR(20) NOT NULL DEFAULT 'EVERYONE',
    call_permission      VARCHAR(20) NOT NULL DEFAULT 'EVERYONE',
    notification_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at           TIMESTAMPTZ NOT NULL
);