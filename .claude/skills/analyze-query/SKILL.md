---
name: analyze-query
description: |
  대상이 되는 코드 범위를 탐색하고, Spring @Transactional, JPA, QueryDSL 기반의 코드에 대해
  트랜잭션 범위, 영속성 컨텍스트, 쿼리 실행 시점 관점에서 분석한다.

  특히 다음을 중점적으로 점검한다.
  - 트랜잭션이 불필요하게 크게 잡혀 있지는 않은지
  - 조회/쓰기 로직이 하나의 트랜잭션에 혼합되어 있지는 않은지
  - JPA의 지연 로딩, flush 타이밍, 변경 감지로 인해 의도치 않은 쿼리 또는 락이 발생할 가능성은 없는지
  - 다일자 재고 row 에 대한 락 순서가 일관적인지 (데드락 회피)
  - 동시성 제어 전략(원자적 UPDATE / 비관적 락 / 낙관적 락)이 도메인 특성에 맞는지

  단순한 정답 제시가 아니라, 현재 구조의 의도와 trade-off를 드러내고
  개선 가능 지점을 선택적으로 판단할 수 있도록 돕는다.
---

# analyze-query

`@Transactional`/JPA/QueryDSL 코드를 **트랜잭션 경계 · 영속성 컨텍스트 · 쿼리 실행 시점 · 락 순서** 관점에서 분석한다.
정답을 강요하지 않고 현재 구조의 **의도와 trade-off**를 드러낸다.

## 📌 Analysis Scope

아래 대상에 대해 분석한다.

- `@Transactional` 이 선언된 클래스 / 메서드
- Service / Facade / Application Layer 코드
- JPA Entity, Repository, QueryDSL 사용 코드
- **하나의 유즈케이스(요청 흐름) 단위**

> 컨트롤러 → 파사드 → 서비스 → 레포지토리 전체 흐름을 기준으로 분석하며, 특정 메서드만 떼어내어 판단하지 않는다.

이 레포의 핵심 분석 대상(숙박 예약):
- `application/accommodation/ReservationFacade` → `domain/accommodation/ReservationService.create()`
- `domain/accommodation/InventoryService.reserve()` (일자별 재고 차감 반복)
- `domain/coupon/CouponService.use()` (쿠폰 1회 사용 보장)
- `domain/wishlist/WishlistService` (찜 수 카운터 정합성)
- 원자적 UPDATE 레포: `DailyRoomInventoryJpaRepository.decrementIfAvailable`, `IssuedCouponJpaRepository.markUsedIfAvailable`, `PropertyWishlistCountJpaRepository.increment/decrement`

## 🔧 사용법

1. 분석할 유즈케이스(예: "예약 생성 흐름")를 정한다.
2. 진입점(Controller)부터 레포지토리까지 호출 체인을 따라가며 관련 파일을 모두 Read한다.
3. 아래 체크리스트를 **순서대로** 적용한다.
4. 발견 사항을 [의도 / trade-off / 개선 후보]로 분류해 보고한다. 개선은 **선택적 제안**이며 강제하지 않는다.

## 🔍 Analysis Checklist

### 1. Transaction Boundary 분석

순서대로 확인한다.

- 트랜잭션 시작 지점은 어디인가? (Controller / Facade / Service / 그 외)
- 트랜잭션이 실제로 필요한 작업은 무엇인가? (상태 변경=쓰기 / 단순 조회)
- 트랜잭션 내부에서 수행되는 작업을 나열한다.
  - 외부 API 호출 (PG, SMS, 알림)
  - 복잡한 조회 (QueryDSL, 대량 IN 절)
  - 반복문 기반 처리 (다일자 재고 차감)

**출력 예시**

```
- 현재 트랜잭션 범위: ReservationService.create()
  ├─ 일자별 요금 조회 (스냅샷)
  ├─ 일자별 재고 차감 (반복, 원자적 UPDATE)
  ├─ 쿠폰 사용 처리 (원자적 UPDATE)
  └─ 예약 생성/저장 (PENDING)

- 트랜잭션이 필요한 핵심 작업: 재고 차감 · 쿠폰 사용 · 예약 저장 (셋이 all-or-nothing)
- 트랜잭션 밖에 두어도 되는 작업: (없음 — PG 결제는 현재 범위 외)
```

### 2. 불필요하게 큰 트랜잭션 식별

아래 패턴이 존재하는지 점검한다.

- Controller 에 `@Transactional` 이 선언되어 있음
- 읽기 전용 로직이 쓰기 트랜잭션에 포함됨
- 외부 시스템 호출(PG/SMS)이 트랜잭션 내부에 포함됨 → **락 점유 시간 증가, 타임아웃·롤백 리스크**
- 트랜잭션 내부에서 대량 조회 / 복잡한 QueryDSL 실행
- 상태 변경 이후에도 트랜잭션이 길게 유지됨

### 3. JPA / 영속성 컨텍스트 관점 분석

- Entity 변경이 **언제 flush** 되는가? (트랜잭션 커밋 시점 / 명시적 flush / JPQL 실행 직전 auto-flush)
- 조회용 Entity가 **변경 감지(dirty checking) 대상**이 되어 의도치 않은 UPDATE가 나가지 않는가?
- **지연 로딩(LAZY)** 으로 인해 트랜잭션 후반/뷰 렌더링 시점에 추가 쿼리(N+1, LazyInitializationException)가 발생할 가능성
- `@Transactional(readOnly = true)` 미적용 여부 (조회 전용 메서드)
- **다일자 재고 row 락 순서가 일관적인가?** (date 오름차순 정렬 후 락 획득 권장 — 데드락 회피의 핵심)
  - 이 레포: `DateRange.datesExclusive()` 가 오름차순 리스트를 반환하므로, 모든 예약이 동일 순서로 재고 row 를 건드린다 → 교차 데드락 회피.
- `@Modifying` 벌크 UPDATE 후 영속성 컨텍스트 정합성 (`clearAutomatically = true` 여부 — stale 1차 캐시 방지)

### 4. 동시성 제어 전략 점검

각 쓰기 지점이 어떤 동시성 제어를 쓰는지, 도메인 특성에 맞는지 본다.

- **원자적 조건부 UPDATE** (`UPDATE ... WHERE 조건`): 단일 row 의 상태 전이/카운터에 적합. 락 보유 시간이 짧다.
  - 재고 차감 `... SET remaining = remaining - 1 WHERE remaining > 0`
  - 쿠폰 사용 `... SET status = USED WHERE status = AVAILABLE` (변경 행 수 0이면 이미 사용됨)
  - 찜 카운터 `... SET count = count + 1` (Lost Update 회피)
- **비관적 락** (`SELECT ... FOR UPDATE`, `@Lock(PESSIMISTIC_WRITE)`): 충돌이 잦고 읽은 값으로 분기 후 써야 할 때.
- **낙관적 락** (`@Version`): 충돌이 드물고 재시도 비용이 낮을 때. (이 레포 BaseEntity 에는 `@Version` 없음 — 필요 엔티티에 개별 추가)
- **UNIQUE 제약**: 중복 생성(중복 발급/중복 찜)을 DB 차원에서 멱등 보장.

점검 질문:
- 동시 요청이 같은 row 의 마지막 자원(마지막 재고 1개, AVAILABLE 쿠폰 1장)을 노릴 때 **정확히 1건만** 성공하는가?
- 트랜잭션 롤백 시 부분 성공이 남지 않는가? (다일자 재고 부분 차감 금지)

### 5. Improvement Proposal (선택적 제안)

상황에 따라 아래를 **선택적으로** 제안한다. 모두 적용하라는 뜻이 아니다.

- 트랜잭션 분리: 예약 PENDING 생성 → (PG 결제) → 예약 CONFIRMED 갱신
- `@Transactional(readOnly = true)` 적용 (조회 전용)
- DTO Projection 도입 (검색/목록 응답에서 엔티티 대신 필요한 컬럼만)
- 외부 호출(PG/SMS) 을 트랜잭션 외부로 이동
- Application Service / Domain Service 책임 재조정
- 락 순서 정렬 강제 (다일자 자원 접근 시 정렬된 컬렉션 사용)

## 📤 보고 형식

```
## analyze-query: <유즈케이스>

### 트랜잭션 범위
<트리>

### 발견
- [의도] ...        # 현재 구조가 의도한 것 (그대로 두어도 됨)
- [trade-off] ...   # 장단점이 갈리는 지점
- [개선 후보] ...    # 선택적 개선 (근거 + 영향도)

### 동시성/락
- <도메인>: <전략> — <적합성 판단>
```
