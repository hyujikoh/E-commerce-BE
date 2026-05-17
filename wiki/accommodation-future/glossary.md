# 숙박 커머스 도메인 (차기 주차 예정)

> 본 문서는 Week1 quest의 "도메인 메모" 지시에 따라 향후 주차에서 다룰 숙박 커머스 핵심 용어를 미리 인지하기 위한 글로서리다. 이번 PR에는 코드 구현이 없다.

## 핵심 용어

| 용어 | 영문 | 정의 |
|------|------|------|
| 숙소 | Property | 호텔/펜션/모텔/리조트 단위의 판매 단위. 1개 Property는 N개 RoomType을 보유 |
| 객실 타입 | RoomType | 한 숙소가 판매하는 객실 카테고리 (예: 스탠다드 더블, 디럭스 트윈). 1 Property : N RoomType |
| 일자별 재고 | DailyRoomInventory | 특정 날짜의 객실 잔여 수량. RoomType × 날짜 단위. 예약 시 차감 |
| 예약 | Reservation | 체크인~체크아웃 기간 동안 객실을 점유하는 계약. User × RoomType × 기간 조합 |
| 찜 | Wishlist | 유저가 숙소를 찜한 기록. User × Property 관계 |

## 관계 예상

```
Property 1 ─── N RoomType ─── N DailyRoomInventory
                  │
                  │ N
                  ├─── Reservation N ─── 1 User
                  │
                  │ N
                  └─── Wishlist N ─── 1 User
```

## 후속 주차 결정 필요

- 동시 예약 시 재고 점유 전략 (낙관적 락 vs 비관적 락 vs 분산 락)
- 예약 취소 정책 + 환불 흐름
- 찜 한도 / 만료 정책
- 검색·정렬 (Property 단위, RoomType 단위, 가격대)
- 가격 정책 (시즌별, 평일/주말, 프로모션)

## 통합 정합성 메모

- 이번 PR(commerce-user)의 회원 식별이 Reservation·Wishlist의 키가 됨
- `@LoopersAuth` ArgumentResolver가 그대로 재활용 가능
- BCrypt + 헤더 인증은 차기 주차에서 토큰 전환 검토 (PRD Q2)
