# Week4 코드 리뷰 기록 — 쿠폰 + 예약 트랜잭션/락/동시성 (PR #11)

- **리뷰 일자**: 2026-08-19
- **대상**: `main..week4` diff (PR #11, 머지 커밋 `858b9333`)
- **방식**: [OpenCodeReview](https://github.com/alibaba/open-code-review) delegation 모드 — `ocr delegate preview` / `ocr delegate rule`로 리뷰 대상·룰을 산출하고, 호스트 에이전트(Claude Code)가 리뷰 수행
- **범위**: 리뷰 대상 51개 파일 (Kotlin 47 + ontology YAML 4, 테스트/wiki 자동 제외)
  - 룰 그룹 1 (Kotlin): Null Safety·데드코드, 간결성, 컬렉션 연산, 코루틴, 클래스/객체 설계, 리소스 관리, 성능 함정, Java interop, 불변성·문자열 템플릿
  - 룰 그룹 2 (YAML): 키 철자 검사 (값 내용은 제외)
- **원칙**: OCR의 precision-over-recall — 근거가 확인된 항목만 보고, 추측성 지적은 제외
- **사전 검증**: 전체 테스트 158개 통과 (`cleanTest` 포함 실제 실행, Testcontainers MySQL)

## 결론

**Critical/Major 없음. Minor 2건, 정책 확인 질문(QUESTION) 3건.**

---

## 🟡 Minor (수정 권장)

### M1. `Coupon.create`/`update`가 과거 시각의 `expiredAt`을 허용

- **위치**: `apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/Coupon.kt`
- **내용**: `validate()`가 name·value·RATE 범위·minOrderAmount는 검사하지만 `expiredAt`은 검사하지 않는다. admin이 이미 만료된 시각으로 템플릿을 만들면 발급까지는 정상 성공하고(`issue()`에도 만료 검사 없음), 사용자는 사용 시점에야 `COUPON_EXPIRED`를 받는다. "받자마자 못 쓰는 쿠폰"이 생기는 경로.
- **제안**: `create`/`update`에 `expiredAt` 미래 검증 추가, 또는 최소한 `issue()`에서 만료 템플릿 발급 차단.

### M2. `expiredAt.atZone(ZoneId.systemDefault())` — 서버 타임존 의존

- **위치**: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/coupon/admin/CouponAdminV1Dto.kt`
- **내용**: admin이 입력한 `LocalDateTime`을 서버 기본 타임존으로 해석한다. 로컬(KST)에서는 문제가 없지만 UTC 컨테이너로 배포되는 순간 만료 시각 해석이 9시간 어긋난다.
- **제안**: `ZoneId.of("Asia/Seoul")` 고정, 또는 요청 스펙을 `ZonedDateTime`/ISO offset 문자열로 변경.

---

## ❓ Question (의도 확인 필요)

### Q1. 발급 이후 템플릿 `update`의 소급 효과

할인 계산이 사용 시점에 템플릿의 `type`/`value`를 조회하므로, 이미 발급된 쿠폰이 있는 템플릿을 admin이 수정하면 기발급 쿠폰의 가치가 소급 변경된다. 의도된 정책인지 확인 필요. 아니라면 "발급 이력 있는 템플릿은 수정 불가" 가드가 필요하다.

### Q2. `INSERT IGNORE`의 광범위한 에러 무시

- **위치**: `infrastructure/wishlist/WishlistJpaRepository.kt`
- MySQL `INSERT IGNORE`는 중복 키뿐 아니라 데이터 절단 등 다른 에러도 warning으로 삼킨다. 현재 스키마(FK 없음, 단순 컬럼)에서는 실질 위험이 낮아 Minor로 올리지 않음. 트레이드오프를 인지하고 선택한 것인지 확인. 대안은 `issue()`처럼 UNIQUE 위반 catch 방식.

### Q3. 존재하지 않는 `propertyId` 찜 허용 + admin API 무인증

- `WishlistService`는 숙소 존재 검증 없이 아무 ID나 찜할 수 있고, `/api-admin/v1/coupons`는 경로 분리 외 인증 장치가 없다. 둘 다 이번 라운드 범위 밖(Property 애그리거트 미구현, 인증 인프라는 X-USER-ID 헤더 방식)으로 보여 QUESTION으로만 기록.

---

## ✅ 검증 통과 (의심 후 근거 확인으로 기각한 항목)

| 항목 | 판정 근거 |
|------|-----------|
| `issue()`의 `try/catch (DataIntegrityViolationException)` | `GenerationType.IDENTITY`라 `save()` 시점에 즉시 INSERT 실행 → 유효한 패턴 |
| 락 순서 | 쿠폰→재고, 찜→카운터로 모든 경로 일관 → 데드락 조건 없음 |
| `@Modifying(clearAutomatically = true)` | 현재 호출 순서상 벌크 연산 전에 로드된 엔티티 없음 → 안전 |
| 비숫자 `X-USER-ID` 헤더 | 기존 `MethodArgumentTypeMismatchException` 핸들러가 400 처리 (누락 헤더는 신규 `MissingRequestHeaderException` 핸들러) |
| `finalAmount` 계산 | `Money.minus` 음수 가드 + 할인액 `min(할인, 주문금액)` 상한으로 이중 방어 |
| 찜 수 read-your-writes | 조건부 UPDATE 후 동일 트랜잭션 재조회로 정확 동작 |
| RATE 정률 할인의 정수 내림 나눗셈 | 주석으로 의도 문서화됨 |
| ontology YAML 4개 파일 키 철자 | 오류 없음 (룰 그룹 2 통과) |

---

## 후속 작업 후보

- [ ] M1: `expiredAt` 과거 값 검증 추가
- [ ] M2: 타임존 고정 또는 요청 스펙 변경
- [ ] Q1~Q3: 정책 결정 후 코드 반영 또는 ontology `notes`에 정책 기록
