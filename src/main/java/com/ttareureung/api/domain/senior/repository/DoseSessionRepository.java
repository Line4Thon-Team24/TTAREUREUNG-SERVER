package com.ttareureung.api.domain.senior.repository;

import com.ttareureung.api.domain.profile.entity.GuardianSeniorStatus;
import com.ttareureung.api.domain.senior.entity.DoseSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DoseSessionRepository extends JpaRepository<DoseSession, Long> {

    List<DoseSession> findAllBySeniorIdAndTargetDate(Long seniorId, LocalDate targetDate);

    // 전화 받기·미응답은 상태 전이라 같은 회차 동시 요청을 직렬화. 접근 확인용 시니어도 함께 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DoseSession s join fetch s.senior where s.id = :id")
    Optional<DoseSession> findByIdForUpdate(@Param("id") Long id);

    // 외부 API 호출(약 판독)처럼 오래 걸리는 작업은 잠금 없이 조회
    @Query("select s from DoseSession s join fetch s.senior where s.id = :id")
    Optional<DoseSession> findByIdWithSenior(@Param("id") Long id);

    // 보호자 화면 "N분 후 복용 예정" 계산용. 저장하지 않고 조회 시점에 찾음
    @Query("""
            select s from DoseSession s
            join fetch s.senior p
            where s.status = com.ttareureung.api.domain.senior.entity.DoseSessionStatus.SCHEDULED
              and s.nextCallAt between :from and :to
              and (p.account.id = :accountId
                   or exists (select 1 from GuardianSenior gs
                              where gs.senior = p and gs.guardian.account.id = :accountId and gs.status = :active))
            order by s.nextCallAt asc
            """)
    List<DoseSession> findUpcomingAccessible(@Param("accountId") Long accountId,
                                             @Param("active") GuardianSeniorStatus active,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DoseSession s where s.senior.id = :seniorId")
    int deleteAllBySeniorId(@Param("seniorId") Long seniorId);
}
