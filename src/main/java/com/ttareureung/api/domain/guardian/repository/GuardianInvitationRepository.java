package com.ttareureung.api.domain.guardian.repository;

import com.ttareureung.api.domain.guardian.entity.GuardianInvitation;
import com.ttareureung.api.domain.guardian.entity.InvitationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface GuardianInvitationRepository extends JpaRepository<GuardianInvitation, Long> {
    boolean existsBySeniorProfileIdAndPhoneNumberAndStatusAndCreatedAtAfter(
            Long seniorProfileId,
            String phoneNumber,
            InvitationStatus status,
            LocalDateTime createdAtAfter
    );
    Optional<GuardianInvitation> findByToken(String token);

    // 같은 번호에 대기 중인 초대가 있으면 새로 만들지 않고 그 링크를 다시 돌려주기 위한 조회
    Optional<GuardianInvitation> findFirstBySeniorProfileIdAndPhoneNumberAndStatusAndCreatedAtAfterOrderByIdDesc(
            Long seniorProfileId,
            String phoneNumber,
            InvitationStatus status,
            LocalDateTime createdAtAfter
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM GuardianInvitation i WHERE i.token = :token")
    Optional<GuardianInvitation> findByTokenWithLock(@Param("token") String token);
}
