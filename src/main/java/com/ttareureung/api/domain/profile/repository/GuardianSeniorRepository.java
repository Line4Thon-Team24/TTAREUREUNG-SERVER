package com.ttareureung.api.domain.profile.repository;

import com.ttareureung.api.domain.profile.entity.GuardianSenior;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

// 응답에 양쪽 프로필 이름이 필요해서 fetch join으로 한 번에 조회 (N+1 방지)
public interface GuardianSeniorRepository extends JpaRepository<GuardianSenior, Long> {

    @Query("""
            select gs from GuardianSenior gs
            join fetch gs.guardian join fetch gs.senior
            where gs.guardian.account.id = :accountId
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

    boolean existsByGuardianIdAndSeniorId(Long guardianId, Long seniorId);
}
