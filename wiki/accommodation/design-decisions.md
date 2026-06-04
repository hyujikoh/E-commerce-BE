# accommodation 도메인 의사결정 기록

> "왜 이렇게 만들었는가"를 기록한다. 코드만 봐서는 알 수 없는 합의·트레이드오프가 여기 모인다.
> 모든 결정은 `docs/design/01-requirements.md`의 합의 기록과 1:1 대응.

---

## search — 검색 흐름 책임 분리

**결정**: 검색은 4단 IN-절 조회로 (Property → RoomType → DailyRoomInventory → DailyRoomRate). 별도 read model을 두지 않는다.

**왜**:
- 이번 라운드는 단일 RDB에서 충분 (대량 트래픽 미발생 가정).
- 별도 검색 인덱스(Elasticsearch 등)를 도입하면 동기화 비용·일관성 이슈 발생.
- 성능 한계가 보이면 그 시점에 read model을 분리한다.

**대안**: 검색 전용 view / Elasticsearch 인덱스 → 도입 시점은 검색 TPS·응답시간 SLO를 기준으로 판단.

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
- 두 경로의 race는 `UPDATE ... WHERE status='PENDING'` affected rows로 가드.
- `FOR UPDATE SKIP LOCKED`로 스케줄러 다중 인스턴스 안전.

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
