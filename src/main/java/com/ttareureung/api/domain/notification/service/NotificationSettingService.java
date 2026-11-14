package com.ttareureung.api.domain.notification.service;

import com.ttareureung.api.domain.notification.dto.NotificationSettingResponse;
import com.ttareureung.api.domain.notification.dto.NotificationSettingUpdateRequest;
import com.ttareureung.api.domain.notification.entity.NotificationSetting;
import com.ttareureung.api.domain.notification.repository.NotificationSettingRepository;
import com.ttareureung.api.domain.profile.service.ProfileAccessChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationSettingService {

    private final ProfileAccessChecker accessChecker;
    private final NotificationSettingRepository settings;

    public NotificationSettingService(ProfileAccessChecker accessChecker, NotificationSettingRepository settings) {
        this.accessChecker = accessChecker;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public NotificationSettingResponse get(Long accountId, Long profileId) {
        accessChecker.ownedProfile(accountId, profileId);
        return settings.findByProfileId(profileId)
                .map(NotificationSettingResponse::from)
                .orElseGet(() -> NotificationSettingResponse.from(NotificationSetting.defaultsFor(profileId)));
    }

    @Transactional
    public NotificationSettingResponse update(Long accountId, NotificationSettingUpdateRequest req) {
        accessChecker.activeAccount(accountId);
        accessChecker.ownedProfile(accountId, req.profileId());
        NotificationSetting setting = settings.findByProfileId(req.profileId())
                .orElseGet(() -> settings.save(NotificationSetting.defaultsFor(req.profileId())));
        setting.update(req.notifyOnComplete(), req.notifyOnMiss(), req.notifyOnDelay(), req.remindBeforeMinutes());
        return NotificationSettingResponse.from(setting);
    }

    /** 프로필 삭제 트랜잭션 안에서만 호출 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteByProfileId(Long profileId) {
        settings.deleteByProfileId(profileId);
    }
}
