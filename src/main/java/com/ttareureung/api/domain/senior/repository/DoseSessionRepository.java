package com.ttareureung.api.domain.senior.repository;

import com.ttareureung.api.domain.senior.entity.DoseSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DoseSession s where s.senior.id = :seniorId")
    int deleteAllBySeniorId(@Param("seniorId") Long seniorId);
}
