# 00. 설계 문서 진입 가이드

> 이 폴더(`docs/design/`)는 **Round 2 Design Quest**의 산출물을 담는다.
> 4종 문서 + ontology + wiki를 어떻게 읽어야 의도가 가장 잘 전달되는지를 안내한다.

---

## 누구를 위한 문서인가

| 역할 | 어디서부터 읽으면 좋은가 | 어디까지 들어가면 충분한가 |
|------|------------------------|---------------------------|
| **PO · PM · 디자이너** | `01-requirements.md § 2 유저 스토리/기능 흐름/유비쿼터스 언어` | `03-class-diagram.md § 한눈에 보기` 까지 |
| **백엔드 개발자** | `01-requirements.md` 전체 | `02 → 03 → 04` 순서대로 다 |
| **QA / 테스트 설계자** | `01-requirements.md § 2 기능 흐름 (Main/Alt/Exception)` | `02-sequence-diagrams.md` 4개 시퀀스 — 분기·예외 경로가 테스트 시나리오의 원천 |
| **리뷰어 / 아키텍트** | `01-requirements.md § 4 합의된 결정사항` | `wiki/accommodation/design-decisions.md` — "왜 이 결정?"의 근거 |
| **신규 합류자 / 다음 라운드 작업자** | `00-overview.md` (이 문서) | `ontology/abox/accommodation.yaml` 로 코드 위치 추적 → 필요한 곳만 깊게 |

---

## 문서 4종의 관계

```
01-requirements.md   ── "무엇을, 왜?" ───────────┐
       │                                          │
       ▼                                          ▼
02-sequence-diagrams ── "흐름은 어떻게?" ──→ 책임 분리·트랜잭션 경계
       │
       ▼
03-class-diagram     ── "객체는 무엇을?" ──→ 도메인 모델·서비스 경계
       │
       ▼
04-erd.md            ── "DB에는 어떻게?" ──→ 테이블·인덱스·제약
```

- **01 → 04 방향**은 점점 구체적·기술적이 된다. PO는 위쪽, DBA는 아래쪽이 핵심.
- **04 → 01 방향**은 "이 SQL이 왜 이렇게 생겼는가?"를 거꾸로 추적할 때 쓴다.
- 각 문서는 **두 층(비개발자용 한눈에 보기 / 개발자용 상세)** 으로 나뉘어 있다.

---

## 합의된 결정 (한 번에 모아 보기)

비개발자도 "이 도메인이 어떤 선택을 했는가"만 이해하면 의사결정에 참여할 수 있다.

| ID | 결정 | 한 줄 요약 |
|----|------|-----------|
| P1 | 인원수 = `maxOccupancy` 이상 객실만 검색 | 한 객실에 일행 전원이 머무는 가정 |
| P2 | 객실요금만 표시 (세금·수수료 후속) | 단순 모델 우선 |
| P3 | 소프트 홀드 모델 | 결제 중 다른 사용자가 채가지 못함 (10분) |
| P4 | 10분 만료 후 자동 취소 + 재고 복구 | 사용자 시간 확보 ↔ 재고 회전 균형 |
| P5 | 취소는 트리거·상태만 (위약금 후속) | 환불 산정은 별도 도메인 |
| P6 | 노쇼는 별도 `NO_SHOW` 상태 | 정산·통계에서 일반 취소와 구분 |
| P7 | 체크아웃 스케줄러 자동 전이 | 호스트 누락 방지 |
| P8 | 1박 평균 = 단순평균 | 계산 단순, 평일·주말 표기 후속 |
| B1 | InventoryService가 재고 단독 책임 | 동시성 코드 응집 |
| B2 | PENDING 시점 가격 스냅샷 | 결제 중 가격 변경 청구 차단 |
| B3 | 1 Property : N RoomType 고정 | RoomType 공유 없음 |
| B4 | RoomInstance 없음 (수량만 관리) | 호실 단위 지정은 후속 |
| B5 | 단일 RDB TX + atomic UPDATE | 별도 락 인프라 불필요 |

각 결정의 **상세 근거·대안**은 `wiki/accommodation/design-decisions.md` 참조.

---

## 도해 표기 가이드 (4종 공통)

`02-sequence-diagrams.md § 도해 표기 가이드`, `04-erd.md § 표기 가이드`에 표기 의미를 정리해두었다. 비개발자라도 이 두 가이드를 먼저 보면 다이어그램이 읽힌다.

특히 시퀀스의 색상 블록:

| 색 | 의미 |
|----|------|
| 베이지 | 재고 차감 트랜잭션 |
| 초록 | 정상 확정 트랜잭션 |
| 빨강 | 보상 트랜잭션 (취소·만료·복구) |
| 파랑 | 읽기 전용 (검색) |

---

## 산출물 위치

```
docs/design/                       ← 본 폴더 (설계 문서)
├── 00-overview.md                 ← 이 문서 (진입 가이드)
├── 01-requirements.md             ← 요구사항·유저 스토리·합의
├── 02-sequence-diagrams.md        ← 흐름 4종 + 도해 가이드
├── 03-class-diagram.md            ← 도메인 모델
└── 04-erd.md                      ← 영속성 구조

ontology/abox/accommodation.yaml   ← 도메인 entity·relation 지도 (코드 위치까지 추적)

wiki/accommodation/
├── README.md                      ← 도메인 개요 + 1주차 통합
├── glossary.md                    ← 용어집 (불변식 포함)
└── design-decisions.md            ← "왜 이 결정?" 8건 + 대안

.claude/skills/requirements-analysis/
└── SKILL.md                       ← 본 라운드에서 작성한 분석 흐름 스킬 (7단계)
```

---

## 다음 라운드 진입 시 체크리스트

- [ ] 1주차 commerce-user 도메인을 `ontology/` 에 정식 등록 (현재 코드에만 존재)
- [ ] `ontology/abox/cross-domain.yaml` 에 `commerce-user ↔ accommodation` 관계 채우기
- [ ] PR base 정렬 — main에 1주차 회원 도메인을 머지하거나, accommodation 구현 브랜치 base를 week1로 잡기
- [ ] entity status `planned → active` 전이 (구현 진입 시점)
- [ ] 후속 라운드 추가 도메인 검토: `cancellation-policy`, `refund-service`, `host-management`
