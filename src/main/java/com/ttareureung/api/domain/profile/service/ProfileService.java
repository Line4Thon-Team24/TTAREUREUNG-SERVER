package com.ttareureung.api.domain.profile.service;

import com.ttareureung.api.domain.auth.exception.AuthErrorCode;
import com.ttareureung.api.domain.profile.dto.ProfileCreateRequest;
import com.ttareureung.api.domain.profile.dto.ProfileResponse;
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
 * 가족 계정의 프로필 생성·조회 로직.
 * accountId는 Controller가 Access Token에서 꺼내 넘겨주므로, 요청 본문으로 다른 계정을 지정할 수 없다.
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
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));
        // Access Token은 최대 30분 유효하므로, 그 사이 비활성화된 계정이 프로필을 만들지 못하게 한 번 더 확인
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new CustomException(AuthErrorCode.ACCOUNT_INACTIVE);
        }
        // 가입 흐름이 보호자 등록 → 시니어 등록 순서라서, 보호자 없이 시니어만 있는 계정은 만들지 않는다.
        if (request.role() == ProfileRole.SENIOR && !profiles.existsByAccountIdAndRole(accountId, ProfileRole.GUARDIAN)) {
            throw new CustomException(ProfileErrorCode.GUARDIAN_REQUIRED);
        }
        Profile profile = new Profile(account, request.name(), request.birthDate(), request.gender(),
                request.phoneNumber(), request.role());
        return ProfileResponse.from(profiles.save(profile));
    }

    // 조회만 하므로 readOnly로 두어 변경 감지(dirty checking) 비용을 줄인다.
    @Transactional(readOnly = true)
    public List<ProfileResponse> findAll(Long accountId) {
        return profiles.findAllByAccountIdOrderByIdAsc(accountId).stream()
                .map(ProfileResponse::from)
                .toList();
    }
}
