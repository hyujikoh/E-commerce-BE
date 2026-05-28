# 02. 시퀀스 다이어그램

> 기준 문서: `01-requirements.md`
> 다이어그램 작성 기준: `.claude/skills/requirements-analysis/SKILL.md` § 5️⃣
> 표현: Mermaid `sequenceDiagram`

---

## 0. 시퀀스 작성 결정사항 (B5)

- **B5 = A** — 단일 RDB 트랜잭션 + atomic UPDATE.
- `InventoryService.reserve(roomTypeId, dates)`는 다음 SQL을 트랜잭션 안에서 1회 실행:

  ```sql
  UPDATE daily_room_inventory
     SET remaining = remaining - 1
   WHERE room_type_id = ?
     AND date IN (?, ?, ...)
     AND remaining > 0;
  ```

- `affected rows == nights` 면 성공, 그 외에는 즉시 롤백 → `OutOfStock` 예외. 부분 차감 상태가 남지 않는다.
- 트랜잭션 경계는 `InventoryService` 내부에서 시작·종료. `Reservation` 도메인은 결과만 받는다.

## 시퀀스 목록

1. **예약 생성 + 결제** (PENDING → CONFIRMED / CANCELLED) — 본 문서
2. PENDING 만료 + 재고 복구 (스케줄러) — 후속 커밋
3. 검색 + 가격 합산 — 후속 커밋
4. 수동 취소 (CONFIRMED → CANCELLED) — 후속 커밋

---

## 1. 예약 생성 + 결제 (가장 핵심 흐름)

### 이 다이어그램이 필요한 이유

- 요구사항의 4개 축 — 검색·예약·재고·상태 머신 — 중 **상태 머신과 재고가 외부 결제 시스템과 맞물리는 가장 위험한 구간**.
- "더블부킹 방지", "결제·예약·재고 일관성", "PENDING 소프트 홀드" 세 가지 합의가 시퀀스에 명시되는지 검증한다.

### 다이어그램

```mermaid
sequenceDiagram
  autonumber
  actor Guest
  participant Ctrl as ReservationController
  participant Svc as ReservationService
  participant Inv as InventoryService
  participant PG as PaymentService (외부)
  participant DB

  Guest->>Ctrl: POST /api/v1/reservations<br/>{roomTypeId, checkIn, checkOut, guestCount}
  Ctrl->>Svc: createReservation(...)

  Svc->>Svc: 기간 유효성 검증<br/>(checkIn < checkOut, 미래 일자)
  Svc->>DB: SELECT RoomType (maxOccupancy 검증)
  DB-->>Svc: roomType
  alt maxOccupancy < guestCount
    Svc-->>Ctrl: 400 BAD_REQUEST
    Ctrl-->>Guest: 인원 초과
  end

  Svc->>DB: SELECT DailyRoomRate<br/>WHERE room_type_id=? AND date IN (...)
  DB-->>Svc: nightlyRates[]
  Svc->>Svc: totalAmount = sum(nightlyRates)<br/>(B2 스냅샷)

  Svc->>Inv: reserve(roomTypeId, dates)
  Note over Inv,DB: 단일 RDB 트랜잭션 시작
  Inv->>DB: UPDATE daily_room_inventory<br/>SET remaining = remaining - 1<br/>WHERE room_type_id=? AND date IN (...)<br/>AND remaining > 0
  DB-->>Inv: affected rows

  alt affected rows < nights (재고 부족 / 동시성 충돌)
    Inv->>DB: ROLLBACK
    Inv-->>Svc: throw OutOfStock
    Svc-->>Ctrl: 409 CONFLICT
    Ctrl-->>Guest: 재고 부족 응답
  else 모든 일자 차감 성공
    Inv->>DB: COMMIT
    Inv-->>Svc: ok
    Svc->>DB: INSERT Reservation<br/>(status=PENDING,<br/> expiresAt=now+10min,<br/> totalAmount, nightlyBreakdown)
    DB-->>Svc: reservationId
    Svc-->>Ctrl: PENDING + 결제 페이지 URL
    Ctrl-->>Guest: 302 → 결제 페이지

    Guest->>PG: 결제 진행 (외부)
    PG-->>Ctrl: webhook(reservationId, status)

    alt 결제 성공
      Ctrl->>Svc: confirm(reservationId)
      Svc->>DB: UPDATE Reservation<br/>SET status=CONFIRMED
      Svc-->>Ctrl: ok
      Ctrl-->>Guest: 200 CONFIRMED
    else 결제 실패 / 타임아웃
      Ctrl->>Svc: fail(reservationId)
      Svc->>Inv: release(roomTypeId, dates)
      Inv->>DB: UPDATE daily_room_inventory<br/>SET remaining = remaining + 1<br/>WHERE room_type_id=? AND date IN (...)
      Svc->>DB: UPDATE Reservation<br/>SET status=CANCELLED
      Svc-->>Ctrl: ok
      Ctrl-->>Guest: 결제 실패 응답
    end
  end
```

### 이 구조에서 특히 봐야 할 포인트

1. **재고 차감이 Reservation INSERT보다 먼저** — 차감 실패 시 Reservation 레코드 자체가 생성되지 않는다. "PENDING이 존재하면 재고는 반드시 점유되어 있다"는 invariant 유지.
2. **PG 호출은 트랜잭션 경계 밖** — 외부 시스템 호출을 DB 트랜잭션 안에 두지 않는다. 결제 결과는 비동기 webhook으로 받아 별도 트랜잭션으로 상태 전이.
3. **release(보상)는 결제 실패 경로에서만 발동** — 정상 흐름에서는 release 호출 없음. PENDING이 만료되는 경로는 시퀀스 2에서 다룬다.
4. **`affected rows < nights` 분기가 더블부킹 방지의 핵심** — atomic SQL 결과로 동시성 충돌이 자연스럽게 검출됨. 별도 락 불필요.

### 잠재 리스크 (이 시퀀스에 한정)

- **PG webhook 유실** — 결제는 성공했는데 webhook이 안 오면 PENDING이 영원히 남는다. → 시퀀스 2의 만료 스케줄러가 안전망. 단, 그 사이 결제 성공 사용자에게 "결제 실패로 취소됨" 응답이 갈 위험.
- **webhook 중복** — 같은 결제에 대해 webhook이 두 번 오면 confirm/fail이 중복 실행될 수 있음. `Reservation.status`의 전이 invariant로 방어 (PENDING이 아니면 무시).
- **시계 불일치** — 만료 판정의 기준이 서버 시계라 멀티 인스턴스 간 클럭 스큐로 만료 직전·직후 결제가 애매하게 동작할 수 있음. UTC 기준 사용 + NTP 동기화 가정.
