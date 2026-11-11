package com.ttareureung.api.domain.profile.repository;

import com.ttareureung.api.domain.profile.entity.Profile;
import com.ttareureung.api.domain.profile.entity.ProfileRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// 프로필 조회는 항상 계정 단위로 한다. 다른 가족 계정의 프로필이 섞이지 않도록 accountId 조건을 붙인다.
public interface ProfileRepository extends JpaRepository<Profile, Long> {

    List<Profile> findAllByAccountIdOrderByIdAsc(Long accountId);

    boolean existsByAccountIdAndRole(Long accountId, ProfileRole role);
}
