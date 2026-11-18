package com.ttareureung.api.domain.profile.repository;

import com.ttareureung.api.domain.profile.entity.GuardianSenior;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// 응답에 양쪽 프로필 이름이 필요해서 fetch join으로 한 번에 조회 (N+1 방지)
public interface GuardianSeniorRepository extends JpaRepository<GuardianSenior, Long> {

    // 초대로 들어온 다른 계정 보호자도 보호자 목록에 보이도록 시니어 쪽 계정 기준도 포함
    @Query("""
            select gs from GuardianSenior gs
            join fetch gs.guardian join fetch gs.senior
            where gs.guardian.account.id = :accountId or gs.senior.account.id = :accountId
            order by gs.id
            """)
    List<GuardianSenior> findAllInAccount(Long accountId);

    @Query("""
            select gs from GuardianSenior gs
            join fetch gs.guardian join fetch gs.senior
            where gs.guardian.id = :profileId or gs.senior.id = :profileId
            order by gs.id
            """)
    List<GuardianSenior> findAllByProfileId(Long profileId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select gs from GuardianSenior gs where gs.senior.id = :seniorProfileId")
    List<GuardianSenior> findAllBySeniorProfileIdWithLock(@Param("seniorProfileId") Long seniorProfileId);

    boolean existsByGuardianIdAndSeniorId(Long guardianId, Long seniorId);

    // 초대로 연결된 외부 보호자는 보호자·시니어 계정이 달라 양쪽 모두 해제 가능해야 함
    @Query("""
            select gs from GuardianSenior gs
            where gs.id = :id
              and (gs.guardian.account.id = :accountId or gs.senior.account.id = :accountId)
            """)
    Optional<GuardianSenior> findByIdAndAccountId(Long id, Long accountId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GuardianSenior gs where gs.guardian.id = :profileId or gs.senior.id = :profileId")
    int deleteAllByProfileId(Long profileId);
}
