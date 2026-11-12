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
 * 시니어 등록도 role=SENIOR 프로필 생성으로 처리 (ERD의 seniors를 profiles로 통합)
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

    // 목록 조회 시 계정까지 매번 조회하지 않도록 지연 로딩
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

    // 화면에 그대로 노출하는 값("어머니", "첫째 아들")이라 자유 텍스트. 최초 보호자는 가입 시 생략 가능
    @Column(length = 20)
    private String relationship;

    // 연결·약 데이터가 역할에 묶여 있어 생성 후 변경 불가
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ProfileRole role;

    public Profile(Account account, String name, LocalDate birthDate, Gender gender,
                   String phoneNumber, String relationship, ProfileRole role) {
        this.account = account;
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
        this.phoneNumber = phoneNumber;
        this.relationship = relationship;
        this.role = role;
    }

    public void update(String name, LocalDate birthDate, Gender gender, String phoneNumber, String relationship) {
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
        this.phoneNumber = phoneNumber;
        this.relationship = relationship;
    }
}
