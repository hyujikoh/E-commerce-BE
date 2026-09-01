# accommodation 도메인 의사결정 기록

> "왜 이렇게 만들었는가"를 기록한다. 코드만 봐서는 알 수 없는 합의·트레이드오프가 여기 모인다.
> 모든 결정은 `docs/design/01-requirements.md`의 합의 기록과 1:1 대응.

---

## search — 검색 read model (Round 5 갱신)

**결정 (Round 5)**: 검색은 단일 네이티브 SQL 파생 테이블 쿼리로 구현한다. (객실타입 × 일자) JOIN을 `GROUP BY rt.id HAVING COUNT(*) = 박수`로 묶어 "기간 전 일자에 재고·요금이 존재"함을 판정하고, 숙소별 `MIN(기간 총액)`을 집계한 뒤 찜 수(`property_wishlist_count`)를 LEFT JOIN한다. 별도 검색 인덱스(Elasticsearch)는 도입하지 않는다.

**왜** (Round 2의 "4단 IN-절 조회" 결정을 대체):
- 4단 IN-절은 애플리케이션 4회 왕복 + 메모리 조합이 필요해, 615만 행 규모에서 성능·페이지네이션 모두 불리하다.
- 정렬(가격/찜/추천)과 페이지네이션이 모두 집계 결과 기준이므로 SQL로 내려야 `LIMIT`이 의미를 가진다.
- Elasticsearch 등 별도 인덱스는 동기화 비용 대비 이득이 없다 — 인덱스 + 캐시로 충분 (측정: `docs/perf/round5-search-optimization.md`).

**정렬 3종**: 가격 오름차순 / 찜 수 내림차순 / 추천 — `0.7·LN(1+찜수) − 0.3·LN(총액)` 내림차순. 찜 수가 멱분포라 로그 스케일로 눌러 가중 합산한다.

**인덱스**: `idx_property_city`(도시 필터 진입점), `idx_room_type_property_capacity`(property→room_type 조인 + capacity 필터 커버). daily 테이블은 기존 `UNIQUE(room_type_id, date)`가 조인 인덱스를 겸한다.

**대안**: 가용 숙소 사전 집계 테이블 → 재고 변경마다 갱신 비용이 커서 보류. Elasticsearch → 검색 TPS·SLO 근거가 생기면 재검토.

---

## search-cache — 검색·상세 Redis 캐시 (Round 5)

**결정**: 검색 페이지(TTL 60초 ± 10초 지터)와 숙소 상세 기본 정보(TTL 10분)를 cache-aside로 캐시한다. 찜 수는 **상세 조회 경로에서는** 캐시하지 않고 매 조회 시 DB에서 병합한다 — 검색 결과에 포함된 찜 수와 찜 수 기반 정렬 순서는 페이지와 함께 캐시되어 최대 70초(TTL + 지터) 낡을 수 있다. 일자별 재고·요금 원본은 캐시하지 않는다.

**왜**:
- 검색 결과는 재고 변동으로 금방 낡는다 — 60초는 "목록이 잠깐 낡아도 결제가 막아준다"는 전제에서 허용. 예약 생성이 원자적 조건부 UPDATE로 DB 재고를 재확인하므로(#inventory-atomic), 캐시가 낡아도 초과 판매는 발생하지 않는다.
- 찜 수는 실시간성 기대가 높고 카운터 테이블 단건 조회가 이미 저렴하다 — 캐시 제외, 상세 응답에서 병합.
- 캐시 장애 시 warn 로그 + DB 폴백으로 정상 동작한다. Redis는 가용성 요구가 아닌 성능 부가 레이어다.
- 검색 TTL의 ± 10초 지터는 동시 적재된 키들의 동시 만료(캐시 스탬피드 → 커넥션 풀 고갈)를 분산하기 위한 것 — 부하 테스트로 실증된 문제다(`docs/perf/round5-load-test.md` § 3). 단, 지터만으로는 개별 키의 만료 herd가 남아(§ 5) stale-while-revalidate 또는 single-flight가 후속 과제다.

**키 설계**: `property:v1:detail:{id}` / `property:v1:search:{city}:{checkIn}:{checkOut}:{guests}:{sort}:{page}:{size}`. `v1` 프리픽스로 응답 스키마 변경 시 일괄 무효화.

**무효화**: TTL 만료만 사용(명시적 evict 없음) — 숙소 정보 쓰기 경로가 아직 없고 TTL이 짧아 evict 훅의 복잡도를 정당화하지 못한다.

---

## reservation-flow — 결제 흐름

**결정**: PENDING 소프트 홀드 모델 (P3=A).

```
PENDING (재고 점유) ──결제 성공─→ CONFIRMED
       ├──결제 실패/타임아웃─→ CANCELLED + 재고 복구
       └──10분 만료──────────→ CANCELLED + 재고 복구
```

**왜**:
- 결제 도중 다른 사용자가 같은 객실을 채가는 경험을 차단.
- 결제·재고 결과의 일관성은 보상 트랜잭션 없이 상태 전이로 해결.

**대안**: 결제 성공 후 CONFIRMED + 동시 차감(A→C) → 사용자 결제 중 재고 경쟁이 발생.

---

## inventory-atomic — 일자별 재고 원자성

**결정**: 단일 RDB 트랜잭션 + atomic UPDATE (B5=A).

```sql
UPDATE daily_room_inventory
   SET remaining = remaining - 1
 WHERE room_type_id = ?
   AND date IN (?, ?, ...)
   AND remaining > 0;
-- affected rows != nights면 ROLLBACK
```

**왜**:
- 단일 RDB로 충분한 트래픽 가정. 별도 락 인프라 불필요.
- atomic SQL이 자연스럽게 동시성 충돌을 검출 (affected rows로 판정).
- 복합 PK + CHECK 제약과 결합해 부정합 상태가 RDB에 저장될 수 없는 구조.

**대안**:
- 비관적 락 (`SELECT ... FOR UPDATE`) → 트랜잭션이 길어져 동시성 ↓.
- 분산 락 (Redis) → 인프라 추가·장애 시나리오 복잡. 다중 인스턴스 RDB 진입 시 재검토.

---

## pending-expiration — PENDING 만료 정리

**결정**: 10분 소프트 홀드 + 두 경로 정리 (P4=A).

1. 백그라운드 스케줄러 1분 주기 (`FOR UPDATE SKIP LOCKED`)
2. 사용자 결제 재진입 시점 (만료 검사)

두 경로 모두 같은 `expire(reservationId)` 함수를 호출.

**왜**:
- 스케줄러 단독 정리 시 최대 1분 갭에서 만료된 PENDING이 살아있음 → 사용자 경험 보정.
- 두 경로의 race는 예약 행 `FOR UPDATE` 잠금 후 최신 상태에서 애그리거트 전이 가드로 정리 —
  잠금으로 직렬화되고, 늦게 도착한 쪽은 갱신된 상태를 보고 no-op(이미 만료됨) 또는
  `INVALID_RESERVATION_STATE`로 끝난다. 설계 문서의 "affected rows 가드"와 동일한 효과.
- `FOR UPDATE SKIP LOCKED` 스캔(batch 100)으로 스케줄러 다중 인스턴스 안전.

**구현 특기 (Round 6)**:
- 만료 정리(상태 전이 + 재고 복구 + 쿠폰 복구)와 확정 실패 응답은 **트랜잭션을 분리**한다 —
  `Facade.confirm`이 `expire`(별도 tx, 커밋 유지)를 선행 호출한 뒤 예외를 던지므로,
  실패 응답 때문에 만료 정리까지 롤백되는 문제가 없다.
- 만료 시각이 지난 PENDING을 사용자가 취소해도 `EXPIRED` 사유로 기록한다 —
  `CancelReason`은 정산·환불 분기 키이므로 실제 원인을 남긴다.

**리스크**: 결제 성공 webhook이 만료 timer 직후 도착하면 "결제됐는데 예약은 취소" 케이스. 검출·로깅까지만 이번 라운드, 자동 환불 큐는 후속.

---

## state-machine — Reservation 상태 머신

**결정**: 6개 상태 enum + 전이는 Reservation 메서드만 수행.

| 상태 | 다음 상태 | 트리거 |
|------|-----------|--------|
| PENDING | CONFIRMED | 결제 성공 webhook |
| PENDING | CANCELLED | 만료 (10분) / 사용자 취소 / 결제 실패 |
| CONFIRMED | CHECKED_IN | 호스트(또는 게스트) 체크인 처리 |
| CONFIRMED | CANCELLED | 사용자 취소 (위약금은 후속) |
| CONFIRMED | NO_SHOW | 체크인일 종료 + 미체크인 (스케줄러, P6) |
| CHECKED_IN | CHECKED_OUT | 체크아웃일 11:00 자동 (스케줄러, P7) |
| CANCELLED / CHECKED_OUT / NO_SHOW | (terminal) | — |

**왜**:
- 상태 머신은 Reservation 도메인 내에 응집 (3장 클래스 다이어그램의 메서드 시그니처와 1:1).
- enum 분리(`CancelReason`: USER_REQUEST/EXPIRED/PAYMENT_FAILED)로 정산·환불 정책 분기 키 확보.
- DB에는 string 저장 + CHECK 제약으로 enum 오타 방어.

---

## price-snapshot — 가격 잠금 시점

**결정**: PENDING 생성 시점 스냅샷 (B2=A).

- `Reservation.total_amount` + `ReservationNightly` (일자별 분해)
- 결제·취소·환불 전 과정에서 이 스냅샷이 진실값

**왜**:
- 결제 도중 호스트가 요금을 바꾸면 사용자가 다른 금액 청구를 받는 케이스 차단.
- 일자별 분해 보관으로 후속 환불 산정(특정 일자만 환불 등) 기반 제공.

**리스크**: 검색 시점 가격과 PENDING 시점 가격이 다를 수 있음. UI에 "검색 시점 기준 가격" 표기 권장 (이번 라운드는 응답 명세만, 사용자 알림 UX는 후속).

---

## inventory-responsibility — 재고 차감 책임 분리

**결정**: InventoryService 단독 책임 (B1=A).

- `Reservation`은 `DailyRoomInventory`를 직접 모름.
- `InventoryService.reserve(roomTypeId, dates)` / `.release(...)` 만 호출.

**왜**:
- 동시성 제어 로직(atomic UPDATE, 향후 락·캐시)을 한 곳에 응집 → 변경 격리.
- `Reservation` aggregate는 "어떤 상태인가"에만 집중.
- 테스트가 쉬워짐 (InventoryService를 mocking).

---

## event-bus — 환불·정산은 도메인 이벤트로 위임

**결정**: 취소 시 `ReservationCanceledEvent` 발행, 환불 산정은 후속 라운드 `RefundService`가 구독.

- ERD에 `outbox` 테이블 마련 (트랜잭션 안에서 INSERT, 별도 워커가 발행).

**왜**:
- `ReservationService`가 환불·정산까지 책임지면 aggregate 비대화.
- 환불 정책(P5)은 후속 라운드 범위 → 도메인 이벤트로 경계를 분리해두면 후속 작업이 깨끗.

**리스크**: outbox 워커 장애 시 이벤트 적체 → 모니터링 알람 필수. 이번 라운드는 구조만 마련.

---

## extensions-out-of-scope — 이번 라운드 제외 항목

| 항목 | 사유 | 후속 시점 |
|------|------|-----------|
| 다중 객실 예약 (E1) | 단일 RoomType 가정으로 시작 — 합의 모델·시퀀스 단순화 | 비즈니스 요구 발생 시 |
| 가변 가격 (E2) | 일자별 정적 요금 가정 | 가격 정책 도메인 도입 시 |
| 멤버십 등급별 가격 (E3) | 회원 등급 도메인 미정의 | commerce-user 확장 후 |
| 단체 예약 (E4) | 1 예약 = 1 RoomType + 1 인원수 | 별도 도메인 검토 |
| 특정 호실 식별 (B4) | (room_type_id, date) 수량 관리로 충분 | 호실 단위 식별 요구 발생 시 RoomInstance 도입 |
| 위약금·환불 산정 (P5) | 이벤트 발행까지만, 산정 로직은 후속 | cancellation-policy + refund-service 도메인 |

---

## transaction-lock — 쿠폰·재고·찜 트랜잭션 경계와 락 전략 (Round 4)

**결정**: 예약 생성은 단일 `@Transactional` 경계 안에서 **쿠폰 사용 → 재고 차감 → 예약 생성** 순으로 처리하고, 각 동시성 구간은 **도메인별 최적의 원자적 조건부 UPDATE**로 제어한다. 비관적/분산 락은 도입하지 않는다.

```
ReservationService.create()  ── 단일 트랜잭션 ──
  ├─ 1) 요금 스냅샷 조회 (DailyRoomRate 합산 → 원금)
  ├─ 2) 쿠폰 사용 처리  couponService.use()   // 할인 계산 → AVAILABLE→USED 조건부 UPDATE
  ├─ 3) 일자별 재고 차감 inventoryService.reserve()  // 날짜 오름차순 atomic UPDATE
  └─ 4) 예약 PENDING 생성 + ReservationNightly 스냅샷(원금/할인/최종)
  ※ 어느 단계든 실패하면 전체 롤백 (다일자 부분 성공 금지)
```

**도메인별 락 전략 (낙관적 성격의 조건부 UPDATE)**:

| 구간 | 동시성 위험 | 제어 방식 |
|------|-------------|-----------|
| 쿠폰 발급 | 같은 템플릿 중복 발급 | `UNIQUE(user_id, coupon_id)` + `DataIntegrityViolation → COUPON_ALREADY_ISSUED` 변환 |
| 쿠폰 사용 | 동일 쿠폰 동시 예약 | `UPDATE ... SET status='USED' WHERE id=? AND status='AVAILABLE'` (affected=0 → 실패) |
| 일자별 재고 | 더블부킹 | `UPDATE ... SET remaining=remaining-1 WHERE remaining>0` (affected≠nights → 롤백) |
| 찜 수 | 동시 찜/찜취소 Lost Update | `UPDATE ... SET count=count±1` 원자적 카운터 + read-your-writes |

**왜**:
- 단일 RDB로 충분한 트래픽 가정 → 별도 락 인프라(Redis 분산 락) 불필요. 조건부 UPDATE의 affected rows가 동시성 충돌을 자연 검출.
- 락 보유 시간을 최소화(조건부 UPDATE는 행 단위 짧은 락)하여 비관적 락 대비 동시 처리량 유지.
- 쿠폰을 재고보다 **먼저** 처리 → 더 비싼 다일자 재고 락을 늦게 잡아 보유 구간 단축.

**데드락 회피 — 다일자 재고 락 순서**:
- 체크인~체크아웃의 여러 재고 row를 차감할 때 **항상 날짜 오름차순**으로 락을 획득한다(`DateRange.datesExclusive()` 오름차순 보장).
- 두 예약이 겹치는 일자 집합을 동시에 차감해도 락 획득 순서가 동일하므로 순환 대기(데드락)가 발생하지 않는다.

**대안**:
- 비관적 락(`SELECT ... FOR UPDATE`) → 트랜잭션 길어져 동시성 ↓.
- 분산 락(Redis) → 인프라·장애 시나리오 복잡. 다중 인스턴스 RDB 진입 시 재검토.

**리스크**: 결제(PG) 미연동 라운드이므로 쿠폰은 PENDING 생성 시점에 즉시 `USED`로 소비된다. → Round 6에서 해소: **PENDING에서 취소·만료된 예약만** 쿠폰을 복구한다(`USED→AVAILABLE` 조건부 UPDATE, 멱등). CONFIRMED 취소의 쿠폰·환불 정책은 후속 환불 도메인에서 다룬다.

---

## payment-integration — PG 결제 연동과 복원력 (Round 6.5)

**결정**: 결제는 예약과 분리된 `Payment` 레코드(1 예약 : N 시도)로 관리하고, PG 호출(OpenFeign)은 **트랜잭션 밖**(`PaymentFacade`)에서, 결과의 DB 반영은 `PaymentService`의 **개별 트랜잭션**에서 수행한다. PG 장애는 예외가 아니라 상태로 흡수한다(Fallback).

**접수 결과 3분법** — "실패를 확정할 수 있는가"가 기준이다:

| 결과 | 판정 근거 | 처리 |
|------|-----------|------|
| Accepted | 정상 응답 + transactionKey | REQUESTED 전이, 콜백 대기 |
| Rejected | 에러 응답(거래 생성 전 단계 확인) / 서킷 오픈(호출 안 함) | REQUEST_FAILED 종결 — 사용자 즉시 재시도 가능 |
| Unknown | 타임아웃 등 응답 유실 | **CREATED 유지** — 거래 생성 여부 불명이므로 실패 확정 금지, 상태 동기화가 사후 확정 |

**왜**:
- PG 호출을 트랜잭션 안에서 하면 외부 지연(처리 1~5초)이 DB 커넥션·예약 행 잠금을 붙든다 — 경계 분리가 Must-Have.
- Unknown을 실패로 확정하면, 실제로는 접수된 거래가 살아있는 채 사용자가 재시도해 **이중 결제**가 날 수 있다.
- 결제 SUCCESS는 이미 커밋된 사실(돈이 빠져나감)이다 — 예약 확정 실패(홀드 만료 등) 시 결제를 롤백하지 않고 **수동 환불 필요 로그**로 드러낸다.

**콜백 검증**: 콜백 본문은 위·변조 가능하므로 신뢰하지 않는다 — transactionKey로 PG를 **재조회해 검증된 스냅샷만** 반영한다. 콜백은 best-effort(유실 가능·재전송 없음)이므로 검증 실패는 로그만 남기고 상태 동기화에 맡긴다.

**서킷 브레이커 2분리**: 결제 생성(`pg-payment`, 실패율 임계 60%)과 조회(`pg-query`, 50%)를 분리한다. 이 PG는 정상 상태에서도 요청 40%가 실패하므로, 결제 서킷 임계를 60%로 두어 "정상적 나쁨"에는 열리지 않고 그 이상의 장애에만 열리게 한다. 조회까지 한 서킷이면 결제 장애가 복구 조회 경로까지 차단한다.

**대안**: 요청 스레드 동기 재시도(Retry) → PG 처리 지연(1~5s)에 사용자 응답이 묶이고, 타임아웃 재시도는 이중 거래 위험. 폴링 전용(콜백 무시) → 확정 지연이 기본 30초+로 늘어남. 콜백+폴링 병행이 지연·유실 모두 커버.

---

## payment-sync — 결제 상태 동기화 (콜백 유실·타임아웃 복구)

**결정**: 30초 주기 스케줄러가 **1분 유예**가 지난 결과 미확정(CREATED/REQUESTED) 결제를 스캔해 PG 조회로 사후 확정한다. `scheduler.payment-sync.enabled`로 끌 수 있다(기본 on).

- **REQUESTED**(콜백 유실): transactionKey 단건 조회 → 결과 반영.
- **CREATED**(접수 응답 유실): orderId 주문 조회 — 결과를 3가지로 구분한다.
  - 거래 목록 → 존재 확정, 결과 반영
  - 404(빈 목록) → **없음 확정**, REQUEST_FAILED 종결(재시도 개방)
  - 조회 실패(null) → **불명**, 아무것도 확정하지 않고 다음 회차 재시도

**왜**:
- 조회 장애를 "거래 없음"으로 오인해 실패 확정하면, 살아있는 거래 위에 재시도가 얹혀 이중 결제가 난다 — 404만 없음의 증거다(PG 시뮬레이터가 거래 없는 주문에 404를 반환함을 소스로 확인).
- orderId는 예약 단위라 재시도 시 재사용된다 — 이전 시도의 거래가 조회에 섞이므로, **다른 결제에 이미 귀속된 transactionKey는 제외**(claimed-key 필터)하고 남은 거래만 이 시도의 것으로 본다.
- 1분 유예는 PG 처리 지연(1~5s)과 콜백 도착 시간을 기다려, 정상 콜백 경로와 폴링의 불필요한 경쟁을 줄인다(경쟁해도 행 잠금 + 종결 no-op으로 멱등).
