# accommodation 도메인 용어집

> ontology entity의 `wiki_doc` 앵커 대상. 코드 밖 비즈니스 의미가 여기 정의된다.

---

## Property

호텔·펜션·모텔·리조트 단위의 **판매 단위**. 한 Property는 N개 RoomType을 보유하며, 호스트가 등록·운영한다.

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | bigint | PK |
| `host_id` | bigint | 호스트 회원 ID (`users.id` 참조) |
| `name` | varchar | 숙소명 |
| `city` | varchar | 도시 (검색 1차 키) |
| `address` | varchar | 상세 주소 |
| `description` | text | 소개 |

**불변식**: 한 Property는 정확히 한 호스트가 소유한다. 호스트 변경은 별도 이관 프로세스 (이번 라운드 범위 외).

**구현 (Round 5)**: `domain/property/Property` — 검색 슬라이스에 필요한 `name`, `city`만 구현. `city` 단일 인덱스(`idx_property_city`). host_id/address/description은 후속.

---

## RoomType

한 Property가 판매하는 **객실 카테고리**. 예: "스탠다드 더블", "디럭스 트윈".

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | bigint | PK |
| `property_id` | bigint | FK → Property |
| `name` | varchar | 객실 타입명 |
| `max_occupancy` | int | 최대 수용 인원 (검색 시 `guestCount` 필터 기준, P1) |
| `bed_type` | varchar | 침대 종류 |

**불변식**:
- 1 Property : N RoomType (B3 합의)
- 같은 RoomType이 여러 Property에 속하지 않음
- 특정 호실(101호 등) 식별 없음 (B4 합의)

**구현 (Round 5)**: `domain/property/RoomType` — `name`, `capacity`(설계의 max_occupancy)만 구현. `(property_id, capacity)` 복합 인덱스(`idx_room_type_property_capacity`). bed_type은 후속.

---

## DailyRoomInventory

특정 `(room_type_id, date)`의 **잔여 객실 수량**. 더블부킹 방지의 핵심 테이블.

| 필드 | 타입 | 설명 |
|------|------|------|
| `room_type_id` | bigint | 복합 PK·FK |
| `date` | date | 복합 PK |
| `remaining` | int | 잔여 수량 (`>= 0` CHECK) |

**불변식**:
- `(room_type_id, date)` 복합 PK로 한 일자에 한 row만 존재
- `remaining >= 0` 보장 (DB CHECK + atomic UPDATE의 `WHERE remaining > 0` 이중 방어)
- 차감·복구는 `InventoryService` 만이 수행 (B1 합의)

---

## DailyRoomRate

특정 `(room_type_id, date)`의 **1박 요금**.

| 필드 | 타입 | 설명 |
|------|------|------|
| `room_type_id` | bigint | 복합 PK·FK |
| `date` | date | 복합 PK |
| `amount` | bigint | 1박 금액 |
| `currency` | char(3) | 통화 (예: KRW) |

**용도**:
- 검색 시 합산 → totalAmount, avgNightlyPrice (P8: 단순평균)
- 예약 PENDING 생성 시점에 `ReservationNightly`로 스냅샷 (B2 합의)

---

## Reservation

게스트의 객실 점유 **계약**. 상태 머신의 주인공.

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | bigint | PK |
| `guest_id` | bigint | FK → users |
| `room_type_id` | bigint | FK → RoomType |
| `check_in_date` | date | 체크인일 |
| `check_out_date` | date | 체크아웃일 (exclusive) |
| `status` | enum | PENDING / CONFIRMED / CHECKED_IN / CHECKED_OUT / CANCELLED / NO_SHOW |
| `total_amount` | bigint | PENDING 시점 스냅샷 합산액 (B2) |
| `currency` | char(3) | KRW |
| `expires_at` | timestamp | PENDING 만료 시각 (생성+10분, P4) |
| `cancel_reason` | enum nullable | USER_REQUEST / EXPIRED / PAYMENT_FAILED |
| `canceled_at` | timestamp nullable | 취소 발생 시각 |

**상태 머신**:

```
PENDING ──(결제 성공)─→ CONFIRMED ──(체크인)─→ CHECKED_IN ──(스케줄러)─→ CHECKED_OUT
   │                       │
   │ (10분 만료/결제실패)    │ (체크인일 종료까지 미체크인)
   ↓                       ↓
CANCELLED               NO_SHOW
   ↑
   │ (CONFIRMED → 사용자 취소)
```

**불변식**:
- 상태 전이는 `Reservation` 도메인 메서드(`confirm/cancel/expire/checkIn/checkOut/markNoShow`)만 수행
- CHECKED_IN 이후 취소 불가
- PENDING은 expires_at 도래 시 자동 CANCELLED + InventoryService.release

---

## ReservationNightly

Reservation 1건의 일자별 **가격 스냅샷**. 환불 산정의 기반.

| 필드 | 타입 | 설명 |
|------|------|------|
| `reservation_id` | bigint | 복합 PK·FK |
| `date` | date | 복합 PK |
| `amount` | bigint | 해당 일자 1박 금액 (PENDING 시점 DailyRoomRate.amount) |
| `currency` | char(3) | KRW |

---

## Wishlist

게스트가 Property를 즐겨찾기한 기록.

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | bigint | PK |
| `guest_id` | bigint | FK → users |
| `property_id` | bigint | FK → Property |
| `added_at` | timestamp | 찜한 시각 |

**불변식**:
- `(guest_id, property_id)` UNIQUE — 중복 찜 불가
- RoomType이 아닌 Property 단위 (객실 타입별 찜은 후속 검토)

---

## PropertyWishlistCount

숙소(Property)별 **찜 수 카운터**. 찜 목록 매번 집계(`COUNT(*)`) 대신 별도 카운터 row를 두어 조회를 빠르게 하고, 동시 찜/찜취소의 정합성을 보장한다.

| 필드 | 타입 | 설명 |
|------|------|------|
| `property_id` | bigint | PK·FK → Property (숙소당 1 row) |
| `count` | bigint | 현재 찜 수 (`>= 0`) |

**불변식**:
- 숙소당 정확히 1 row (`property_id` 단일 PK)
- 증감은 **원자적 조건부 UPDATE**(`SET count = count + 1` / `count = count - 1 WHERE count > 0`)로만 수행 → Lost Update 차단
- `WishlistService`가 찜/찜취소와 **같은 트랜잭션**에서 증감 후 카운트를 읽어 반환(read-your-writes)하여, 응답 카운트가 타 요청 증감에 오염되지 않음

---

## Payment

예약 1건에 대한 **결제 시도 기록**. 예약(Reservation)과 별개의 생애주기를 가지며, 실패한 시도 뒤에 새 시도가 쌓일 수 있다(1 예약 : N 결제 시도).

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | bigint | PK |
| `reservation_id` | bigint | FK → Reservation |
| `guest_id` | bigint | 예약에서 확정한 소유자 |
| `order_id` | varchar | PG 주문 번호 — 예약 ID 6자리 제로 패딩 (PG 제약: 6자리 이상) |
| `card_type` | enum | SAMSUNG / KB / HYUNDAI |
| `card_no` | varchar | 카드 번호 (시뮬레이터 스펙 그대로 보관) |
| `amount` / `currency` | bigint / char(3) | 예약 가격 스냅샷에서 확정 (요청 본문 아님) |
| `status` | enum | CREATED / REQUESTED / REQUEST_FAILED / SUCCESS / FAILED |
| `transaction_key` | varchar nullable | PG 거래 식별자 (접수 후 부여) |
| `failure_reason` | varchar nullable | 실패 사유 |

**상태 머신**:

```
CREATED ──(PG 접수)─→ REQUESTED ──(콜백/동기화 SUCCESS)─→ SUCCESS
   │                      └──────(콜백/동기화 FAILED)──→ FAILED
   ├──(PG 확정 거절·서킷 오픈)─→ REQUEST_FAILED
   └──(타임아웃)─→ CREATED 유지 — 상태 동기화가 사후 확정
```

**불변식**:
- 금액·orderId·guestId는 예약에서 확정한다 — 요청 본문의 금액을 신뢰하지 않음
- 진행 중(CREATED/REQUESTED)/성공 결제가 있으면 새 시도를 만들지 않는다(멱등, 예약 행 잠금으로 직렬화)
- 실패 건(REQUEST_FAILED/FAILED)만 있으면 재시도로 새 결제 생성 가능
- 종결 상태(SUCCESS/FAILED/REQUEST_FAILED)는 다시 바뀌지 않는다 — 중복 콜백·동시 폴링은 no-op

---

## Coupon (쿠폰 템플릿)

발급 가능한 **쿠폰의 정의**. admin이 등록·수정·삭제하며, 사용자는 이 템플릿을 발급받아 `IssuedCoupon`을 보유한다.

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | bigint | PK |
| `name` | varchar | 쿠폰명 (예: "여름 휴가 10% 할인") |
| `type` | enum | `FIXED`(정액) / `RATE`(정률) |
| `value` | bigint | 정액: 할인 금액(원), 정률: 퍼센트(%) |
| `min_order_amount` | bigint nullable | 최소 결제 금액 조건 (선택) |
| `expired_at` | timestamp | 만료 시각 |

**할인 계산**:
- `FIXED`: `discount = min(value, orderAmount)` — 원금 초과 할인 방지
- `RATE`: `discount = floor(orderAmount × value / 100)` — 원 단위 절사, 원금 캡 적용
- `min_order_amount` 미달 시 사용 불가

---

## IssuedCoupon (발급 쿠폰)

사용자가 발급받아 **소유한 쿠폰 1장**. 재사용 불가하며 예약 결제 시 1건당 1장만 적용된다.

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | bigint | PK |
| `user_id` | bigint | 소유자 (`users.id` 참조) |
| `coupon_id` | bigint | FK → Coupon 템플릿 |
| `status` | enum | `AVAILABLE` / `USED` / `EXPIRED` |
| `used_at` | timestamp nullable | 사용 시각 |

**불변식**:
- `(user_id, coupon_id)` UNIQUE — 1인당 같은 템플릿 1회 발급 (중복 발급은 `COUPON_ALREADY_ISSUED`)
- 사용은 `AVAILABLE` 상태에서만 1회 가능 — 조건부 UPDATE로 동시 사용 시 1건만 성공(`COUPON_ALREADY_USED`)
- 사용 시점에 만료(`expired_at` 경과)면 `COUPON_EXPIRED`
- 타 유저 소유 쿠폰 사용 시 `COUPON_NOT_OWNED`
