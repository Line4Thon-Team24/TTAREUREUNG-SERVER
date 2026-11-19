package com.ttareureung.api.domain.push.repository;

import com.ttareureung.api.domain.push.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findAllByProfileIdIn(Collection<Long> profileIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PushSubscription s where s.profile.id = :profileId")
    int deleteAllByProfileId(@Param("profileId") Long profileId);

    @Modifying
    @Query("delete from PushSubscription s where s.endpoint = :endpoint")
    int deleteByEndpoint(@Param("endpoint") String endpoint);
}
