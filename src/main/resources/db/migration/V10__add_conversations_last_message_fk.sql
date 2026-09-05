-- Gắn bổ sung FK còn thiếu ở V7 (phụ thuộc vòng conversations <-> messages,
-- xem ghi chú tại V7__create_conversations_table.sql).
ALTER TABLE conversations
    ADD CONSTRAINT fk_conversations_last_message
        FOREIGN KEY (last_message_id) REFERENCES messages (id);
