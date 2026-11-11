package com.ttareureung.api.domain.profile.entity;

import com.ttareureung.api.domain.user.entity.Account;
import com.ttareureung.api.global.common.BaseTimeEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/**
 * 가족 공통 계정(Account) 안에 속한 보호자/시니어 프로필.
 * 시니어 등록도 role이 SENIOR인 프로필을 만드는 것으로 처리한다. (ERD에서 seniors 테이블을 profiles로 통합)
 */
@Getter
@Entity
@Table(name = "profiles")
@AttributeOverrides({
        @AttributeOverride(name = "createdAt", column = @Column(name = "created_at", nullable = false, updatable = false)),
        @AttributeOverride(name = "updatedAt", column = @Column(name = "updated_at", nullable = false))
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 프로필 목록을 볼 때 계정 정보까지 매번 조회하지 않도록 지연 로딩
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 10)
    private Gender gender;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    // 연결 정보와 약 데이터가 역할에 묶이기 때문에 생성 후에는 바꾸지 않는다.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ProfileRole role;

    public Profile(Account account, String name, LocalDate birthDate, Gender gender,
                   String phoneNumber, ProfileRole role) {
        this.account = account;
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
        this.phoneNumber = phoneNumber;
        this.role = role;
    }
}
