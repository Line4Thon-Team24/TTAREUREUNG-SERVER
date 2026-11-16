package com.ttareureung.api.domain.senior.repository;

import com.ttareureung.api.domain.senior.entity.DoseSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DoseSessionRepository extends JpaRepository<DoseSession, Long> {

    List<DoseSession> findAllBySeniorIdAndTargetDate(Long seniorId, LocalDate targetDate);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DoseSession s where s.senior.id = :seniorId")
    int deleteAllBySeniorId(@Param("seniorId") Long seniorId);
}
