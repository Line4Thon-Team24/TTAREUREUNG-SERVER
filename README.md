# 💊 따르릉 (Ttareureung) — Backend

보호자와 시니어를 연결하는 AI 기반 복약 관리 서비스입니다.

## 🛠 기술 스택

| 구분 | 스택 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.2, Spring Security, Spring Data JPA |
| Database | MySQL |
| Build | Gradle 9.8.2 |
| API Docs | springdoc-openapi / Swagger UI |
| Monitoring | Spring Boot Actuator |

## 🚀 실행 방법

MySQL 실행 후 `.env`에 DB 접속 정보를 설정합니다.

```bash
# 로컬 환경변수 파일 생성 (.env는 Git에 올리지 않음)
cp .env.example .env

# 서버 실행
./gradlew bootRun
```

- [Swagger UI](http://localhost:8080/swagger-ui/index.html)
- [Health API](http://localhost:8080/api/v1/health)

## 🌿 Branch Convention

- 🔵 **`main`**: 배포 가능한 운영 브랜치. 직접 작업 금지, PR 머지만 허용
- 🟠 **`hotfix`**: 운영 긴급 수정. `main`에서 분기 후 `main`, `develop`에 병합
- 🟡 **`release`**: 배포 전 QA 및 수정. `develop`에서 분기 후 `main`, `develop`에 병합
- 🟣 **`develop`**: 기능 개발본을 모으는 통합 브랜치
- 🟢 **기능 브랜치**: 기능·이슈 단위로 `develop`에서 분기

## 📌 Branch Naming Convention

- **구조**: `Prefix/#이슈번호-작업내용` (Kebab Case)
- **예시**: `feat/#10-login-api`, `chore/#1-setting-base`

| Prefix | 설명 | 예시 |
|---|---|---|
| `feat` | 새로운 기능 | `feat/#10-login-api` |
| `fix` | 버그 수정 | `fix/#23-health-api` |
| `docs` | 문서 수정 | `docs/#5-update-readme` |
| `style` | 코드 포맷팅 | `style/#12-format-code` |
| `refactor` | 코드 리팩토링 | `refactor/#30-user-service` |
| `chore` | 설정·빌드 변경 | `chore/#1-setting-base` |
| `hotfix` | 운영 긴급 수정 | `hotfix/#40-login-error` |
| `release` | 배포 준비 | `release/v1.0.0` |

## 📝 Commit Convention

| 이모지 | 타입 | 설명 |
|---|---|---|
| 🎉 | `Start` | 프로젝트 초기화 |
| ✨ | `Feat` | 새로운 기능 |
| 🐛 | `Fix` | 버그 수정 |
| 🚑 | `Hotfix` | 긴급 버그 수정 |
| 🎨 | `Design` | UI / CSS 수정 |
| ♻️ | `Refactor` | 코드 리팩토링 |
| 🔧 | `Settings` | 환경설정·빌드 변경 |
| 🗃️ | `Comment` | 주석 추가·변경 |
| ➕ | `Dependency/Plugin` | 의존성·플러그인 추가 |
| 📝 | `Docs` | 문서 수정 |
| 🔀 | `Merge` | 브랜치 병합 |
| 🚀 | `Deploy` | 배포 작업 |
| 🚚 | `Rename` | 파일·폴더 이동 및 이름 변경 |
| 🔥 | `Remove` | 코드·파일 삭제 |
| ⏪️ | `Revert` | 이전 버전으로 롤백 |

- **구조**: `이모지 타입: 작업 내용 (#이슈번호)`
- **예시**: `✨ Feat: 보호자 회원가입 API 추가 (#10)`

## 🔀 Flow

1. `develop`에서 작업 브랜치 생성
2. 작업 완료 후 커밋 및 Push
3. `develop`을 대상으로 PR 생성
4. 팀원 리뷰 후 `develop`에 병합
5. 배포 시 `develop`에서 `release/v버전` 생성 후 QA 및 수정
6. `release`를 `main`, `develop`에 병합하고 `main`에 버전 태그 추가
7. 운영 긴급 수정은 `main`에서 `hotfix` 분기 후 `main`, `develop`에 병합

```bash
# 작업 브랜치 생성
git switch develop
git pull --ff-only origin develop
git switch -c 'feat/#10-login-api'

# 커밋 및 Push
git add src/
git commit -m '✨ Feat: 로그인 API 추가 (#10)'
git push -u origin 'feat/#10-login-api'

# PR: develop ← feat/#10-login-api
```
