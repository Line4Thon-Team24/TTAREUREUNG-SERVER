package com.ttareureung.api.domain.profile.service;

import com.ttareureung.api.domain.auth.exception.AuthErrorCode;
import com.ttareureung.api.domain.profile.dto.ProfileCreateRequest;
import com.ttareureung.api.domain.profile.dto.ProfileResponse;
import com.ttareureung.api.domain.profile.dto.ProfileUpdateRequest;
import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.domain.profile.entity.ProfileRole;
import com.ttareureung.api.domain.profile.exception.ProfileErrorCode;
import com.ttareureung.api.domain.profile.repository.ProfileRepository;
import com.ttareureung.api.domain.user.entity.Account;
import com.ttareureung.api.domain.user.entity.AccountStatus;
import com.ttareureung.api.domain.user.repository.AccountRepository;
import com.ttareureung.api.global.exception.CustomException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 가족 계정의 프로필 생성·조회·수정 로직.
 * accountId는 Access Token 기준이라 요청 본문으로 다른 계정 지정 불가
 */
@Service
@Transactional
public class ProfileService {

    private final AccountRepository accounts;
    private final ProfileRepository profiles;

    public ProfileService(AccountRepository accounts, ProfileRepository profiles) {
        this.accounts = accounts;
        this.profiles = profiles;
    }

    public ProfileResponse create(Long accountId, ProfileCreateRequest request) {
        Account account = activeAccount(accountId);
        // 가입 흐름이 보호자 → 시니어 순서라 보호자 없는 시니어 생성 차단
        if (request.role() == ProfileRole.SENIOR && !profiles.existsByAccountIdAndRole(accountId, ProfileRole.GUARDIAN)) {
            throw new CustomException(ProfileErrorCode.GUARDIAN_REQUIRED);
        }
        Profile profile = new Profile(account, request.name(), request.birthDate(), request.gender(),
                request.phoneNumber(), request.relationship(), request.role());
        return ProfileResponse.from(profiles.save(profile));
    }

    // 조회 전용이라 readOnly로 변경 감지 비용 절감
    @Transactional(readOnly = true)
    public List<ProfileResponse> findAll(Long accountId) {
        return profiles.findAllByAccountIdOrderByIdAsc(accountId).stream()
                .map(ProfileResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProfileResponse findOne(Long accountId, Long profileId) {
        return ProfileResponse.from(ownedProfile(accountId, profileId));
    }

    // save 호출 없이 트랜잭션 종료 시 변경 감지로 UPDATE
    public ProfileResponse update(Long accountId, Long profileId, ProfileUpdateRequest request) {
        activeAccount(accountId);
        Profile profile = ownedProfile(accountId, profileId);
        profile.update(
                keepIfNull(request.name(), profile.getName()),
                keepIfNull(request.birthDate(), profile.getBirthDate()),
                keepIfNull(request.gender(), profile.getGender()),
                clearIfEmpty(request.phoneNumber(), profile.getPhoneNumber()),
                clearIfEmpty(request.relationship(), profile.getRelationship()));
        return ProfileResponse.from(profile);
    }

    // Access Token 유효 시간(최대 30분) 중 비활성화된 계정의 변경 차단용 재확인
    private Account activeAccount(Long accountId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new CustomException(AuthErrorCode.ACCOUNT_INACTIVE);
        }
        return account;
    }

    // profileId만으로 조회하면 다른 계정 프로필 접근 가능. accountId 조건 필수
    private Profile ownedProfile(Long accountId, Long profileId) {
        return profiles.findByIdAndAccountId(profileId, accountId)
                .orElseThrow(() -> new CustomException(ProfileErrorCode.PROFILE_NOT_FOUND));
    }

    private static <T> T keepIfNull(T requested, T current) {
        return requested == null ? current : requested;
    }

    private static String clearIfEmpty(String requested, String current) {
        if (requested == null) {
            return current;
        }
        return requested.isEmpty() ? null : requested;
    }
}
