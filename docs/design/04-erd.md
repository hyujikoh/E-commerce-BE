# 04. ERD (영속성 구조)

> 기준 문서: `01-requirements.md`, `02-sequence-diagrams.md`, `03-class-diagram.md`
> 표현: Mermaid `erDiagram`
> 다이어그램 작성 기준: `.claude/skills/requirements-analysis/SKILL.md` § 5️⃣

---

## 한눈에 보기 (비개발자용)

이 문서는 데이터베이스에 어떤 **표(테이블)** 들이 있고, 표 간 관계가 어떻게 연결되는지 그린다. 9개 테이블이 등장한다.

| 테이블 | 한 줄 설명 |
|--------|-----------|
| `users` | 1주차 회원 도메인이 만든 회원 테이블. 게스트·호스트 둘 다 여기에 있다. |
| `property` | 숙소 한 곳당 한 행. |
| `room_type` | 객실 타입 한 종류당 한 행. 어느 숙소(property_id)에 속하는지 가리킨다. |
| `daily_room_inventory` | "이 객실 타입의 이 날짜에 몇 개 남았는가". 한 (객실 타입, 날짜)에 정확히 한 행. |
| `daily_room_rate` | "이 객실 타입의 이 날짜의 가격은 얼마인가". 한 (객실 타입, 날짜)에 정확히 한 행. |
| `reservation` | 예약 1건당 한 행. 상태·만료 시각·총액을 보관. |
| `reservation_nightly` | 예약 1건이 점유한 N박을 일자별로 분해한 가격 박제. |
| `wishlist` | 찜 1건당 한 행. 같은 게스트가 같은 숙소를 두 번 찜할 수 없다. |
| `outbox` | "이벤트를 외부에 알려야 한다"는 알림장. 트랜잭션 안에서 적어두면 별도 워커가 보낸다. |

### 더블부킹 방지가 데이터베이스 차원에서 작동하는 방식

```
1. (room_type_id, date)는 일자별 재고 표의 "복합 기본키"다.
   → 한 객실 타입이 한 날짜에 두 행을 가질 수 없음.

2. 재고 수량(remaining)에는 "0 미만이 될 수 없다"는 규칙(CHECK)이 박혀있다.
   → 마이너스 재고가 저장되는 것이 물리적으로 불가능.

3. 차감은 단 한 줄의 SQL로 처리된다.
   → "remaining > 0인 행만 -1로 바꿔라" — 동시에 들어온 두 요청 중
     한쪽은 0행 변경(실패), 한쪽만 1행 변경(성공)으로 자연스럽게 갈린다.
```

다시 말해 **"앱이 실수해도 DB가 거부한다"** 가 이 ERD의 가장 큰 약속이다.

### 표기 가이드

- **PK (Primary Key)**: 그 행을 유일하게 식별하는 열.
- **FK (Foreign Key)**: 다른 표의 PK를 가리키는 열 (관계의 시작점).
- **PK_FK**: 양쪽 역할을 동시에 하는 열 (복합 키의 일부이면서 다른 표를 가리킴).
- **`||--o{`**: "왼쪽 한 행에 오른쪽 0개 이상 행이 매달림" (예: 1 숙소 — N 객실 타입).

---

## 0. 설계 원칙

- **복합 PK로 일자별 단위 강제** — `daily_room_inventory(room_type_id, date)` / `daily_room_rate(room_type_id, date)` 둘 다 복합 기본키. 한 일자에 동일 RoomType가 두 행 가질 수 없음을 스키마 차원에서 보장.
- **상태·사유는 문자열 enum** — DB에는 `VARCHAR`로 저장, 애플리케이션은 enum으로 매핑. 새 상태 추가 시 마이그레이션 불필요.
- **가격 잠금은 `reservation_nightly`에 일자별로 적재** — B2 합의의 스냅샷을 한 행 한 일자로 분해 보관. 환불 산정의 기반.
- **outbox 패턴 채택** — 도메인 이벤트는 트랜잭션 안에서 `outbox`에 INSERT, 별도 발행 워커가 published_at을 갱신하며 송출.

## 1. ERD

### 이 다이어그램이 필요한 이유

- 시퀀스의 모든 SQL이 어느 테이블·어느 인덱스에 떨어지는지 확정한다.
- 더블부킹 방지의 마지막 보루(복합 PK + atomic UPDATE)가 스키마에 박혀 있는지 확인한다.
- 1주차 회원 도메인(USER)과의 접합점을 표시한다.

### 다이어그램

```mermaid
erDiagram
  USER ||--o{ PROPERTY : "hosts (host_id)"
  USER ||--o{ RESERVATION : "guests"
  USER ||--o{ WISHLIST : "favorites"

  PROPERTY ||--o{ ROOM_TYPE : has
  PROPERTY ||--o{ WISHLIST : "referenced_by"

  ROOM_TYPE ||--o{ DAILY_ROOM_INVENTORY : "per date"
  ROOM_TYPE ||--o{ DAILY_ROOM_RATE      : "per date"
  ROOM_TYPE ||--o{ RESERVATION          : "booked as"

  RESERVATION ||--o{ RESERVATION_NIGHTLY : "price snapshot"
  RESERVATION ||--o{ OUTBOX              : "event source"

  USER {
    bigint id PK
    varchar login_id
    varchar password_hash
    varchar email
    varchar name
    date    birth_date
    varchar phone_number
    timestamp created_at
  }

  PROPERTY {
    bigint  id PK
    bigint  host_id FK
    varchar name
    varchar city
    varchar address
    text    description
    timestamp created_at
    timestamp updated_at
  }

  ROOM_TYPE {
    bigint  id PK
    bigint  property_id FK
    varchar name
    int     max_occupancy
    varchar bed_type
    timestamp created_at
    timestamp updated_at
  }

  DAILY_ROOM_INVENTORY {
    bigint  room_type_id PK_FK
    date    date         PK
    int     remaining
    timestamp updated_at
  }

  DAILY_ROOM_RATE {
    bigint  room_type_id PK_FK
    date    date         PK
    bigint  amount
    char3   currency
    timestamp updated_at
  }

  RESERVATION {
    bigint    id PK
    bigint    guest_id FK
    bigint    room_type_id FK
    date      check_in_date
    date      check_out_date
    varchar   status
    bigint    total_amount
    char3     currency
    timestamp expires_at
    varchar   cancel_reason "nullable"
    timestamp canceled_at   "nullable"
    timestamp created_at
    timestamp updated_at
  }

  RESERVATION_NIGHTLY {
    bigint  reservation_id PK_FK
    date    date           PK
    bigint  amount
    char3   currency
  }

  WISHLIST {
    bigint    id PK
    bigint    guest_id FK
    bigint    property_id FK
    timestamp added_at
  }

  OUTBOX {
    bigint    id PK
    varchar   aggregate_type
    bigint    aggregate_id
    varchar   event_type
    text      payload
    timestamp created_at
    timestamp published_at "nullable"
  }
```

## 2. 키·인덱스·제약 (스키마 부속 설계)

| 테이블 | PK | 인덱스 / 제약 | 용도 |
|--------|----|-----|------|
| `property` | `id` | IDX(`city`), IDX(`host_id`) | 검색 첫 단계, 호스트 대시보드 |
| `room_type` | `id` | IDX(`property_id`), IDX(`max_occupancy`) | 검색 시 propertyId IN + 인원 필터 |
| `daily_room_inventory` | (`room_type_id`, `date`) **복합 PK** | (PK가 곧 인덱스) | atomic UPDATE 차감·복구, 검색 가용성 판정 |
| `daily_room_rate` | (`room_type_id`, `date`) **복합 PK** | (PK가 곧 인덱스) | 검색 가격 합산, PENDING 스냅샷 적재 |
| `reservation` | `id` | UNIQUE 없음, IDX(`guest_id`, `created_at`), IDX(`room_type_id`, `check_in_date`), **IDX(`status`, `expires_at`)** | 사용자 예약 목록, 호스트 캘린더, 만료 스캐너 |
| `reservation_nightly` | (`reservation_id`, `date`) 복합 PK | FK(`reservation_id`) ON DELETE CASCADE | 일자별 가격 스냅샷 (환불 산정 기반) |
| `wishlist` | `id` | **UNIQUE(`guest_id`, `property_id`)**, IDX(`guest_id`, `added_at`) | 중복 찜 방지, 사용자별 최신순 정렬 |
| `outbox` | `id` | IDX(`published_at`) WHERE published_at IS NULL, IDX(`aggregate_type`, `aggregate_id`) | 미발행 이벤트 워커 picking |

### 더블부킹 방지의 스키마 보장

```
복합 PK (room_type_id, date)
  + remaining INT NOT NULL CHECK (remaining >= 0)
  + atomic UPDATE WHERE remaining > 0
= 부정합 상태가 RDB에 저장될 수 없는 구조
```

`remaining`이 음수가 될 수 없도록 `CHECK` 제약 + UPDATE의 `WHERE remaining > 0` 조건 두 겹으로 보호. 만약 부정합 시도(N건 차감 중 일부만 가용)면 atomic SQL의 `affected rows != N` 분기에서 즉시 롤백.

## 3. 이 구조에서 특히 봐야 할 포인트

1. **`daily_room_inventory`/`daily_room_rate`의 복합 PK는 곧 "최적 인덱스"** — 별도 인덱스 생성 불필요. PK 자체가 `WHERE room_type_id=? AND date IN (...)` 쿼리의 정답 인덱스.
2. **`reservation(status, expires_at)` 인덱스가 만료 스캐너의 심장** — `WHERE status='PENDING' AND expires_at < NOW()`가 풀스캔되면 매분 발화하는 스케줄러가 DB를 죽인다. 인덱스 필수.
3. **`reservation_nightly`는 가격 잠금의 영속화** — B2 합의가 ORM에 박혀있지 않고 별도 테이블로 분해 보관. 환불 산정 시 일자별로 가져갈 수 있어야 함.
4. **`USER` 테이블은 1주차 회원 도메인 소유** — 본 ERD에는 접합점으로만 표시. PR base가 `main`이면 회원 테이블 정의가 없으니, 머지 전에 `week1` (또는 1주차 PR이 들어있는 브랜치)을 main으로 merge하거나 base를 변경해야 함.
5. **`outbox`는 작아 보여도 도메인 일관성의 핵심** — 트랜잭션 안에서 INSERT, 별도 워커가 발행. 시퀀스 4의 `ReservationCanceledEvent`가 여기 적재됨.

## 4. 잠재 리스크 (이 ERD에 한정)

- **`reservation_nightly` row 폭증** — 평균 3박이면 reservation 1건당 3 row. 1년 1,000만 예약이면 nightly 3,000만 row. 파티셔닝(yearly) 또는 OLAP 분리 고려.
- **`daily_room_inventory`의 사전 적재 책임이 모호** — `(room_type_id, date)` row가 미리 INSERT 되어 있어야 atomic UPDATE가 의미를 갖는다. "RoomType 생성 시 향후 1년치를 미리 만든다" / "필요 시점에 생성" 중 하나를 운영 정책으로 정해야. 이번 라운드는 사전 적재 가정.
- **`status` 컬럼이 string인 부담** — 오타·대소문자 실수 위험. JPA 매핑 + DB CHECK 제약(`status IN (...)`) 으로 이중 방어 권장.
- **outbox 워커 장애 시 이벤트 적체** — 워커가 죽으면 outbox row가 published_at=NULL로 쌓임. 모니터링 알람 필수. 이번 라운드는 ERD 차원의 구조만 명시, 운영 정책은 다음 라운드.

## 5. 1주차 회원 도메인과의 통합 메모

- `USER`는 commerce-user 도메인에서 정의됨 (`UserModel`, 컬럼: `id`, `login_id`, `password_hash`, `email`, `name`, `birth_date`, `phone_number`, ...).
- 본 ERD의 `host_id`, `guest_id`는 모두 `USER.id`를 참조.
- 인증/세션은 1주차의 `@LoopersAuth` + `X-Loopers-LoginId/LoginPw` 헤더 그대로 사용 가능 (이번 라운드는 인증 변경 없음).
