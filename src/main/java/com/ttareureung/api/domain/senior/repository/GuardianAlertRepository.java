package com.ttareureung.api.domain.senior.repository;

import com.ttareureung.api.domain.profile.entity.GuardianSeniorStatus;
import com.ttareureung.api.domain.senior.entity.GuardianAlert;
import com.ttareureung.api.domain.senior.entity.GuardianAlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// 접근 범위: 내 계정의 시니어 + 내 계정 보호자와 연결된 다른 계정 시니어 (SeniorAccessValidator와 같은 기준)
public interface GuardianAlertRepository extends JpaRepository<GuardianAlert, Long> {

    @Query("""
            select a from GuardianAlert a
            join fetch a.senior p
            join fetch a.session s
            where p.account.id = :accountId
               or exists (select 1 from GuardianSenior gs
                          where gs.senior = p and gs.guardian.account.id = :accountId and gs.status = :active)
            order by a.id desc
            """)
    List<GuardianAlert> findAllAccessible(@Param("accountId") Long accountId,
                                          @Param("active") GuardianSeniorStatus active);

    @Query("""
            select a from GuardianAlert a
            join fetch a.senior p
            join fetch a.session s
            where a.id = :id
              and (p.account.id = :accountId
                   or exists (select 1 from GuardianSenior gs
                              where gs.senior = p and gs.guardian.account.id = :accountId and gs.status = :active))
            """)
    Optional<GuardianAlert> findAccessibleById(@Param("id") Long id, @Param("accountId") Long accountId,
                                               @Param("active") GuardianSeniorStatus active);

    boolean existsBySessionIdAndType(Long sessionId, GuardianAlertType type);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GuardianAlert a where a.senior.id = :seniorId")
    int deleteAllBySeniorId(@Param("seniorId") Long seniorId);
}
