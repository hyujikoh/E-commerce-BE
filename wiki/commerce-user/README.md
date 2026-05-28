# commerce-user 도메인

회원 가입·인증·정보 관리·비밀번호 변경을 다루는 도메인.

## 범위 (Week1 #2)

- 회원가입 (`POST /api/v1/users`)
- 내 정보 조회 (`GET /api/v1/users/me`)
- 비밀번호 수정 (`PATCH /api/v1/users/me/password`)

## 인증 방식

- 매 요청 헤더 인증: `X-Loopers-LoginId` + `X-Loopers-LoginPw`
- 비밀번호는 BCrypt 단방향 해시로 저장 (`spring-security-crypto`)
- HTTPS 전제 운영. JWT 토큰 전환은 차기 주차 검토.

## 핵심 구조

```
com.loopers
├── domain.user/                  # UserModel, UserService, UserRepository
│   └── vo/                       # RawPassword, Password, Name, BirthDate, PhoneNumber
├── application.user/             # AuthFacade (인증 책임 격리)
├── infrastructure.user/          # UserJpaRepository, UserRepositoryImpl
└── interfaces.api.user/
    ├── UserV1Controller
    ├── UserV1ApiSpec / UserV1Dto
    └── auth/                     # @LoopersAuth, AuthenticatedUser, ArgumentResolver, WebMvcConfig, PasswordEncoderConfig
```

## 보안 결정 기록

| 결정 | 사유 |
|------|------|
| 헤더 평문 비번 유지 (HTTPS 전제) | quest 명세, 학습 맥락. JWT 전환은 차기 주차 |
| `Password.@get:JsonIgnore` 강제 | NFR-3 — 응답 직렬화에서 비번 해시 누출 방지 |
| ID 미존재 vs 비번 불일치 응답 동일화 (UNAUTHORIZED) | NFR-2 — 공격자 정보 추론 차단 |
| DB UNIQUE 1차 방어선 (`uk_users_login_id`) | check-then-act race 방지 |
| BCrypt 검증은 AuthFacade 단일 수행 | 도메인 레이어 재검증 제거 (라운드 1 fix) |

## 후속 작업 (Trust Ledger 발췌)

- BCrypt cost factor 설정 주입화 (`bcrypt.strength`)
- DataIntegrityViolationException 로그 레벨 하향 + 구조화 로깅
- `X-Loopers-LoginPw` 헤더 로그 마스킹 (RequestLoggingFilter)
- `UserService.findByLoginId` 로그에서 loginId 마스킹
- `Password.fromHash` 가시성 제한

자세한 결정 이력은 `.dev/prd.md`, `.dev/design.md`, `.dev/trust-ledger.md` 참조.
