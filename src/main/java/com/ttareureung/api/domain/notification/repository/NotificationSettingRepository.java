package com.ttareureung.api.domain.notification.repository;

import com.ttareureung.api.domain.notification.entity.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {
    Optional<NotificationSetting> findByProfileId(Long profileId);

    @Modifying
    @Query("delete from NotificationSetting s where s.profileId = :profileId")
    void deleteByProfileId(@Param("profileId") Long profileId);
}
