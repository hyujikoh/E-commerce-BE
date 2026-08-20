# Week5 코드 리뷰 기록 2차 — 지터 적용 델타 (PR #13)

- **리뷰 일자**: 2026-08-20
- **대상**: `8edb4306..week5` — 1차 리뷰(`2026-08-19-week5-code-review.md`) 이후 커밋 3개 (`fba52c23`, `78734523`, `38e2e14a`)
- **방식**: OpenCodeReview delegation 모드 — `ocr delegate preview --from 8edb4306 --to week5` / `ocr delegate rule`, 호스트 에이전트(Claude Code)가 리뷰 수행
- **범위**: 리뷰 대상 3개 파일 (`.gitignore`, `PropertyRedisCacheRepository.kt`, `docs/perf/load/search-load.js`; 마크다운 문서 4개는 OCR 자동 제외)
- **원칙**: precision-over-recall — 근거가 확인된 항목만 보고

## 결론

**델타에 신규 지적 사항 없음.** 1차 리뷰 미해결 항목 5건은 전부 상태 그대로 (아래 현황표).

---

## ✅ 검증 통과 (델타)

| 항목 | 판정 근거 |
|------|-----------|
| 지터 산식 범위 | `nextLong(-10, 11)` → 양끝 포함 [-10, +10] → TTL 50~70초. 문서 서술("60초 ± 10초")과 일치 |
| 지터 난수 소스 | `ThreadLocalRandom.current()` — 스레드 세이프, 호출당 객체 생성 없음 (성능 함정 룰 통과) |
| KDoc 정합 | `SEARCH_TTL` 주석이 "지터 포함 최대 70초"로 갱신됨. `SEARCH_TTL_JITTER` 상수에 목적 주석 존재 |
| k6 날짜 산식 | `offset ∈ [0, 122−nights]` → checkOut 최대 2026-08-31 = 시드 범위 상한. UTC 고정 계산이라 로컬 TZ 무관 |
| k6 코드 규칙 | `const`만 사용, `===` 비교, 데드코드 없음, 시나리오 타이밍(hot 종료 2m20s → cold 시작 2m40s) 겹침 없음 |
| `.gitignore` | `.DS_Store` 패턴 추가 정상, 추적 중인 `.DS_Store` 없음 확인 |

## ❓ 관찰 (지적 아님, 선택 사항)

- k6 cold 시나리오의 임계값 `p(95)<1500`은 실측값(p95 4.2s, `round5-load-test.md` § 2)과 불일치 — 재현 실행마다 threshold 실패로 종료된다. "미달성 SLO 목표를 실패 신호로 남겨두는 것"이 의도면 유지, 아니면 실측 기준으로 조정. 정책 선택의 문제라 지적으로 올리지 않음.

---

## 1차 리뷰 미해결 항목 현황 (2026-08-20 재확인)

| 항목 | 상태 | 근거 |
|------|------|------|
| 🟠 M1 캐시 스탬피드 | **부분 해소** | 지터 적용·재측정 완료. 키별 만료 herd가 남아 stale-while-revalidate 또는 single-flight 후속 필요 (`round5-load-test.md` § 5) |
| 🟠 M2 COUNT 이중 실행 | 미해결 | `PropertySearchJdbcRepository.findPage`가 `COUNT_SQL`을 여전히 별도 실행 |
| 🟡 m1 wiki 찜 수 서술 | 미해결 | `design-decisions.md` "찜 수는 캐시하지 않고 매 조회 시 DB에서 병합한다" 문장에 한정어(상세 경로 기준) 없음 |
| 🟡 m2 coupon import 빈 줄 | 미해결 | `CouponService.kt` 등 3개 파일의 `PageResult` import가 여전히 빈 줄로 분리 |
| ❓ Q1 negative cache / Q2 `save()` 미사용 | 정책 결정 대기 | 코드 변화 없음 |
