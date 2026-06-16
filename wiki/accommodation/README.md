# accommodation 도메인

숙박 커머스의 핵심 비즈니스 도메인. 숙소 검색·예약 생성·재고 차감·결제·상태 머신을 다룬다.

## 범위 (Round 2 — 설계)

- 도시·체크인·체크아웃·인원수 기반 **숙소 검색**
- 객실 타입 선택 후 **예약 생성** (PENDING → 결제 → CONFIRMED)
- **일자별 재고** 차감 + 더블부킹 방지
- **상태 머신**: PENDING → CONFIRMED → CHECKED_IN → CHECKED_OUT 또는 CANCELLED / NO_SHOW
- **찜** (Wishlist)
- **일자별 요금** 합산 (totalAmount, avgNightlyPrice)

## 범위 외 (이번 라운드)

- 다중 객실 예약 (E1)
- 가변 가격 정책 (E2)
- 멤버십 등급별 가격 (E3)
- 단체/그룹 예약 (E4)
- 위약금·환불 산정 (P5: 트리거·상태만 설계, 산정 로직은 후속)
- 특정 호실(RoomInstance) 지정 (B4)

## 문서 구조

```
wiki/accommodation/
├── README.md            ← 이 문서 (도메인 개요)
├── glossary.md          ← 용어집 (Property, RoomType, ...)
└── design-decisions.md  ← 의사결정 기록 (왜 이 모델·정책인가)
```

설계 산출물 (코드 저장소 내):

```
docs/design/
├── 01-requirements.md       ← 요구사항 명세 + 합의된 정책
├── 02-sequence-diagrams.md  ← 시퀀스 다이어그램 4종
├── 03-class-diagram.md      ← 도메인 객체 모델
└── 04-erd.md                ← 영속성 구조 + 인덱스
```

ontology 등록:

```
ontology/abox/accommodation.yaml  ← 14 entities + 29 relations (Round 4: 찜 entity 갱신)
ontology/abox/coupon.yaml          ← 쿠폰 도메인 (Round 4 신규, 예약과 cross-domain 연결)
```

## 1주차 commerce-user 도메인과의 통합

- `Reservation.guest_id`, `Property.host_id`, `Wishlist.guest_id` 모두 `users.id`(1주차 회원 도메인) 참조.
- 인증은 1주차 `@LoopersAuth` + `X-Loopers-LoginId/LoginPw` 헤더를 그대로 사용 — 이번 라운드 인증 변경 없음.
- 1주차 commerce-user는 아직 ontology에 정식 등록되지 않음 → `ontology/abox/cross-domain.yaml`의 후속 추가 메모 참조.

## 관련 도메인 (후속 라운드 검토 대상)

- `cancellation-policy` — 위약금·환불 규칙
- `refund-service` — 외부 PG 환불 호출 + 정산
- `host-management` — 호스트 운영자 콘솔 (Property/RoomType CRUD, 캘린더, 정산 조회)
