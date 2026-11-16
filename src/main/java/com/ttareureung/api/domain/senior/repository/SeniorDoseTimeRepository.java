package com.ttareureung.api.domain.senior.repository;

import com.ttareureung.api.domain.senior.entity.SeniorDoseTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeniorDoseTimeRepository extends JpaRepository<SeniorDoseTime, Long> {

    List<SeniorDoseTime> findAllBySeniorId(Long seniorId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SeniorDoseTime t where t.senior.id = :seniorId")
    int deleteAllBySeniorId(@Param("seniorId") Long seniorId);
}
