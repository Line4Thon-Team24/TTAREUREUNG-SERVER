package com.ttareureung.api.domain.profile.service;

import com.ttareureung.api.domain.guardian.service.SeniorAccessValidator;
import com.ttareureung.api.domain.profile.dto.GuardianSeniorChangeRequest;
import com.ttareureung.api.domain.profile.dto.GuardianSeniorCreateRequest;
import com.ttareureung.api.domain.profile.dto.GuardianSeniorResponse;
import com.ttareureung.api.domain.profile.entity.GuardianSenior;
import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.domain.profile.entity.ProfileRole;
import com.ttareureung.api.domain.profile.exception.ProfileErrorCode;
import com.ttareureung.api.domain.profile.repository.GuardianSeniorRepository;
import com.ttareureung.api.global.exception.CustomException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;

/**
 * 보호자-시니어 연결 생성·조회 로직.
 * 두 프로필 모두 토큰의 계정 소속인지 확인 후 연결
 */
@Service
@Transactional
public class GuardianSeniorService {

    private final ProfileAccessChecker accessChecker;
    private final SeniorAccessValidator seniorAccess;
    private final GuardianSeniorRepository links;

    public GuardianSeniorService(ProfileAccessChecker accessChecker, SeniorAccessValidator seniorAccess,
                                 GuardianSeniorRepository links) {
        this.accessChecker = accessChecker;
        this.seniorAccess = seniorAccess;
        this.links = links;
    }

    public GuardianSeniorResponse link(Long accountId, GuardianSeniorCreateRequest request) {
        accessChecker.lockedActiveAccount(accountId);
        Profile guardian = accessChecker.ownedProfile(accountId, request.guardianProfileId());
        Profile senior = accessChecker.ownedProfile(accountId, request.seniorProfileId());
        if (guardian.getRole() != ProfileRole.GUARDIAN || senior.getRole() != ProfileRole.SENIOR) {
            throw new CustomException(ProfileErrorCode.INVALID_LINK_ROLE);
        }
        if (links.existsByGuardianIdAndSeniorId(guardian.getId(), senior.getId())) {
            throw new CustomException(ProfileErrorCode.ALREADY_LINKED);
        }
        try {
            // 시니어의 첫 연결이면 대표 보호자. 잠금 조회로 동시 연결 시 대표 2명 방지
            boolean firstLink = links.findAllBySeniorProfileIdWithLock(senior.getId()).isEmpty();
            GuardianSenior link = new GuardianSenior(guardian, senior);
            if (firstLink) {
                link.changePrimary(true);
            }

            GuardianSenior savedLink = links.saveAndFlush(link);
            return GuardianSeniorResponse.from(savedLink);

        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 exists 확인을 통과한 경우 UNIQUE 제약(1062)에서 걸림
            if (e.getMostSpecificCause() instanceof SQLException sql && sql.getErrorCode() == 1062) {
                throw new CustomException(ProfileErrorCode.ALREADY_LINKED);
            }
            throw e;
        }
    }

    // 시니어의 마지막 연결 해제도 허용. 연결 없는 시니어는 프로필 목록에 남아 다시 연결 가능
    public void unlink(Long accountId, Long linkId) {
        accessChecker.activeAccount(accountId);
        GuardianSenior link = links.findByIdAndAccountId(linkId, accountId)
                .orElseThrow(() -> new CustomException(ProfileErrorCode.LINK_NOT_FOUND));
        boolean wasPrimary = link.isPrimary();
        Long seniorId = link.getSenior().getId();
        List<GuardianSenior> seniorLinks = links.findAllBySeniorProfileIdWithLock(seniorId);
        links.delete(link);
        if (wasPrimary) {  // 대표가 해제시 대표 승계
            seniorLinks.stream()
                    .filter(other -> !other.getId().equals(linkId))
                    .findFirst()
                    .ifPresent(next -> next.changePrimary(true));
        }
    }

    /**
     * 담당 시니어 변경. linkId는 유지하고 시니어만 교체
     * 새 시니어는 요청 계정이 접근 가능한 시니어여야 함. 대표였으면 원래 시니어 쪽은 남은 연결에 승계
     */
    public GuardianSeniorResponse changeSenior(Long accountId, Long linkId, GuardianSeniorChangeRequest request) {
        accessChecker.activeAccount(accountId);
        GuardianSenior link = links.findByIdAndAccountId(linkId, accountId)
                .orElseThrow(() -> new CustomException(ProfileErrorCode.LINK_NOT_FOUND));
        Profile newSenior = seniorAccess.requireSenior(accountId, request.seniorProfileId());
        Long oldSeniorId = link.getSenior().getId();
        if (oldSeniorId.equals(newSenior.getId())) {
            return GuardianSeniorResponse.from(link);
        }
        if (links.existsByGuardianIdAndSeniorId(link.getGuardian().getId(), newSenior.getId())) {
            throw new CustomException(ProfileErrorCode.ALREADY_LINKED);
        }

        // 두 시니어의 연결을 id 작은 쪽부터 잠가 동시 변경 시 교착 방지
        Long first = Math.min(oldSeniorId, newSenior.getId());
        Long second = Math.max(oldSeniorId, newSenior.getId());
        List<GuardianSenior> firstLinks = links.findAllBySeniorProfileIdWithLock(first);
        List<GuardianSenior> secondLinks = links.findAllBySeniorProfileIdWithLock(second);
        List<GuardianSenior> oldLinks = first.equals(oldSeniorId) ? firstLinks : secondLinks;
        List<GuardianSenior> newLinks = first.equals(oldSeniorId) ? secondLinks : firstLinks;

        boolean wasPrimary = link.isPrimary();
        link.changeSenior(newSenior, newLinks.isEmpty());
        if (wasPrimary) {
            oldLinks.stream()
                    .filter(other -> !other.getId().equals(linkId))
                    .findFirst()
                    .ifPresent(next -> next.changePrimary(true));
        }
        try {
            links.flush();
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 같은 보호자-시니어 조합이 먼저 생긴 경우
            if (e.getMostSpecificCause() instanceof SQLException sql && sql.getErrorCode() == 1062) {
                throw new CustomException(ProfileErrorCode.ALREADY_LINKED);
            }
            throw e;
        }
        return GuardianSeniorResponse.from(link);
    }

    // profileId가 없으면 계정 전체 연결, 있으면 해당 프로필이 포함된 연결만
    @Transactional(readOnly = true)
    public List<GuardianSeniorResponse> findAll(Long accountId, Long profileId) {
        List<GuardianSenior> result;
        if (profileId == null) {
            result = links.findAllInAccount(accountId);
        } else {
            accessChecker.ownedProfile(accountId, profileId);
            result = links.findAllByProfileId(profileId);
        }
        return result.stream().map(GuardianSeniorResponse::from).toList();
    }
}
