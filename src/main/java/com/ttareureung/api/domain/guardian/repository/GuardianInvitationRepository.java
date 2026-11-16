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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM GuardianInvitation i WHERE i.token = :token")
    Optional<GuardianInvitation> findByTokenWithLock(@Param("token") String token);
}
