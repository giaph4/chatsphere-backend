package com.chatsphere.user.repository;

import com.chatsphere.user.domain.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {
    // PK chính là userId (shared primary key ở 2.1) → findById(userId) là đủ.
}
