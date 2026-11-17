package com.ttareureung.api.domain.senior.repository;

import com.ttareureung.api.domain.senior.entity.MedicationRecognition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MedicationRecognitionRepository extends JpaRepository<MedicationRecognition, Long> {

    long countBySessionIdAndMedicationId(Long sessionId, Long medicationId);

    Optional<MedicationRecognition> findTopBySessionIdAndMedicationIdOrderByIdDesc(Long sessionId, Long medicationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MedicationRecognition r where r.session.senior.id = :seniorId")
    int deleteAllBySeniorId(@Param("seniorId") Long seniorId);
}
