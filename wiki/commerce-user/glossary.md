# commerce-user 용어집

## 회원 (User)

회원 가입을 통해 식별된 서비스 사용자.

| 필드 | 타입 | 제약 |
|------|------|------|
| loginId | String | 영문+숫자만 (`^[A-Za-z0-9]+$`), UNIQUE |
| password (passwordHash) | String | BCrypt 해시 (60자), 응답 미노출 |
| name | String | not blank |
| birthDate | LocalDate | 유효 날짜 |
| email | String | RFC 5322 |
| phoneNumber | String | `010-XXXX-XXXX` (SMS 발송용) |

## 값 객체

- **RawPassword** — 평문 비밀번호 + 정책 검증 (8~16자 화이트리스트, 생년월일 포함 금지). 인프라 의존 없음.
- **Password** — BCrypt 해시 래퍼. `@get:JsonIgnore`로 누출 차단. `matches(plain, encoder)`로 검증.
- **Name** — 이름. `masked()` 메서드로 마지막 글자 `*` 치환 (1글자도 동일).
- **PhoneNumber** — 휴대폰. `masked()` 메서드로 가운데 4자리 `****` 치환 (예: `010-****-5678`).
- **BirthDate** — 생년월일 LocalDate 래퍼. `toYyyyMmDd()`, `toYyyyDashMmDashDd()` 포맷 메서드 제공.

## 인증

- **@LoopersAuth** — 컨트롤러 메서드 파라미터 어노테이션. ArgumentResolver가 인증 처리 후 `AuthenticatedUser` 주입.
- **AuthenticatedUser** — 인증된 사용자의 loginId만 보유하는 데이터 객체.
- **AuthFacade** — 인증 책임 격리. `authenticate(loginId, plain)` → `AuthenticatedUser`. NOT_FOUND를 UNAUTHORIZED로 변환.

## 정책

- **비밀번호 정책 (BR-2)** — 8~16자 + 영문 대/소문자·숫자·특수문자 화이트리스트 + 생년월일 포함 금지(`YYYYMMDD`·`YYYY-MM-DD` 두 형식). 강제 조합 없음.
- **이름 마스킹 정책 (BR-7)** — 마지막 글자 1개를 `*`로 치환. 1글자도 동일하게 `*`.
- **휴대폰 마스킹 정책 (BR-8)** — 가운데 4자리 `****`로 치환. 형식 보존.
- **인증 실패 메시지 동일화 (NFR-2)** — ID 미존재와 비번 불일치 모두 동일 응답 (UNAUTHORIZED).

## API 엔드포인트

| Method | Path | Auth | 요약 |
|--------|------|------|------|
| POST | `/api/v1/users` | — | 회원가입. 201 + `{ "loginId": "..." }` |
| GET | `/api/v1/users/me` | @LoopersAuth | 내 정보 조회. 200 + 마스킹된 응답 |
| PATCH | `/api/v1/users/me/password` | @LoopersAuth | 비밀번호 수정. 200 + data:null |
