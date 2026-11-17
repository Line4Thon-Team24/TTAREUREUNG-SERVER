package com.ttareureung.api.domain.guardian.service;

import com.ttareureung.api.domain.guardian.dto.AcceptInvitationRequest;
import com.ttareureung.api.domain.guardian.dto.AcceptInvitationResponse;
import com.ttareureung.api.domain.guardian.dto.InvitationRequest;
import com.ttareureung.api.domain.guardian.dto.InvitationResponse;
import com.ttareureung.api.domain.guardian.entity.GuardianInvitation;
import com.ttareureung.api.domain.guardian.entity.InvitationStatus;
import com.ttareureung.api.domain.guardian.exception.GuardianErrorCode;
import com.ttareureung.api.domain.guardian.repository.GuardianInvitationRepository;
import com.ttareureung.api.domain.profile.entity.GuardianSenior;
import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.domain.profile.entity.ProfileRole;
import com.ttareureung.api.domain.profile.repository.GuardianSeniorRepository;
import com.ttareureung.api.domain.profile.repository.ProfileRepository;
import com.ttareureung.api.domain.profile.service.ProfileAccessChecker;
import com.ttareureung.api.global.exception.CustomException;
import com.ttareureung.api.global.util.PhoneNumbers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 다른 가족 계정 보호자 초대.
 * SMS 인증 없이 링크 방식: 초대한 사람이 응답의 링크를 직접 전달, 받은 사람은 가입 후 토큰으로 수락
 */
@Service
public class GuardianInvitationService {

    private final ProfileAccessChecker accessChecker;
    private final SeniorAccessValidator seniorAccessValidator;
    private final GuardianInvitationRepository invitations;
    private final GuardianSeniorRepository links;
    private final ProfileRepository profiles;
    private final String frontendBaseUrl;

    public GuardianInvitationService(ProfileAccessChecker accessChecker,
                                     SeniorAccessValidator seniorAccessValidator,
                                     GuardianInvitationRepository invitations,
                                     GuardianSeniorRepository links,
                                     ProfileRepository profiles,
                                     @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.accessChecker = accessChecker;
        this.seniorAccessValidator = seniorAccessValidator;
        this.invitations = invitations;
        this.links = links;
        this.profiles = profiles;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public InvitationResponse invite(Long accountId, InvitationRequest req) {
        accessChecker.activeAccount(accountId);
        Profile senior = seniorAccessValidator.requireSenior(accountId, req.seniorProfileId());

        Set<String> linkedPhones = links.findAllByProfileId(senior.getId()).stream()
                .map(l -> PhoneNumbers.normalize(l.getGuardian().getPhoneNumber()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        LocalDateTime expirationThreshold = LocalDateTime.now().minusDays(7);
        List<InvitationResponse.Item> items = new ArrayList<>();
        int invited = 0;
        for (String phone : req.phoneNumbers().stream().map(PhoneNumbers::normalize).distinct().toList()) {
            if (linkedPhones.contains(phone)) {
                continue;
            }
            // 대기 중인 초대가 있으면 링크를 잃어버린 경우를 위해 같은 토큰을 다시 돌려줌
            Optional<GuardianInvitation> pending = invitations
                    .findFirstBySeniorProfileIdAndPhoneNumberAndStatusAndCreatedAtAfterOrderByIdDesc(
                            senior.getId(), phone, InvitationStatus.PENDING, expirationThreshold);
            if (pending.isPresent()) {
                items.add(toItem(pending.get(), false));
                continue;
            }
            GuardianInvitation invitation = invitations.save(GuardianInvitation.create(senior.getId(), phone));
            items.add(toItem(invitation, true));
            invited++;
        }
        return new InvitationResponse(invited, "초대 링크를 전달해주세요.", items);
    }

    /** 초대 링크를 연 사람이 가입 → 보호자 프로필 생성 후 토큰과 함께 호출 */
    @Transactional
    public AcceptInvitationResponse accept(Long accountId, AcceptInvitationRequest req) {
        accessChecker.activeAccount(accountId);

        GuardianInvitation invitation = invitations.findByTokenWithLock(req.token())
                .filter(i -> i.getStatus() == InvitationStatus.PENDING)
                .orElseThrow(() -> new CustomException(GuardianErrorCode.INVITATION_NOT_FOUND));

        if (invitation.isExpired()) {
            throw new CustomException(GuardianErrorCode.INVITATION_EXPIRED);
        }

        Profile guardian = accessChecker.ownedProfile(accountId, req.guardianProfileId());
        if (guardian.getRole() != ProfileRole.GUARDIAN) {
            throw new CustomException(GuardianErrorCode.NOT_GUARDIAN_PROFILE);
        }
        Profile senior = profiles.findById(invitation.getSeniorProfileId())
                .orElseThrow(() -> new CustomException(GuardianErrorCode.INVITATION_NOT_FOUND));

        // 시니어의 첫 연결이면 대표 보호자. 잠금 조회로 동시 수락 시 대표 2명 방지
        List<GuardianSenior> seniorLinks = links.findAllBySeniorProfileIdWithLock(senior.getId());
        GuardianSenior link = seniorLinks.stream()
                .filter(l -> l.getGuardian().getId().equals(guardian.getId()))
                .findFirst()
                .orElseGet(() -> {
                    GuardianSenior created = new GuardianSenior(guardian, senior);
                    if (seniorLinks.isEmpty()) {
                        created.changePrimary(true);
                    }
                    return links.save(created);
                });
        invitation.accept();
        return new AcceptInvitationResponse(senior.getId(), senior.getName(), link.getId());
    }

    private InvitationResponse.Item toItem(GuardianInvitation invitation, boolean newlyCreated) {
        return new InvitationResponse.Item(invitation.getPhoneNumber(), invitation.getToken(),
                frontendBaseUrl + "/invite?token=" + invitation.getToken(), invitation.getExpiresAt(), newlyCreated);
    }
}
