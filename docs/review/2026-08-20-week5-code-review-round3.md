# Week5 코드 리뷰 기록 — 3차 (리뷰 후속 수정분 델타) (PR #13)

- **리뷰 일자**: 2026-08-20
- **대상**: `15b791b8..week5` — 1·2차 리뷰 후속 수정 3커밋
  - `edc2e78c` docs(accommodation): 검색 캐시의 찜 수 신선도 서술 한정 (리뷰 m1)
  - `9bb9b663` style(coupon): PageResult import 빈 줄 분리 병합 (리뷰 m2)
  - `854f1ac0` perf(property): 검색 COUNT를 본 쿼리에 통합 (리뷰 M2)
- **방식**: 1·2차와 동일 — OpenCodeReview delegation 모드 (`ocr delegate preview` / `ocr delegate rule`), 호스트 에이전트(Claude Code)가 리뷰 수행
- **범위**: 리뷰 대상 3개 파일 (Kotlin main 3, 테스트 1·문서 3 자동 제외 — 테스트·문서는 아래 정합성 검증에 포함)
- **사전 검증**: ktlintCheck 통과, commerce-api 테스트 전체 통과 (854f1ac0 커밋 pre-check로 실제 실행, 신규 폴백 경로 테스트 포함)

## 결론

**신규 지적 없음.** M2 단일 쿼리 전환의 동작 변화(빈 검색 경로의 쿼리 수 증가)는 문서화된 의도적 트레이드오프로 판정. 1차 리뷰의 수정 권장 4건(M1 지터, M2, m1, m2)이 모두 반영 완료됐다.

## ✅ 검증 통과 (의심 후 근거 확인으로 기각한 항목)

| 항목 | 판정 근거 |
|------|-----------|
| 빈 검색("조건 일치 0건") 경로가 1쿼리 → 2쿼리(SELECT + COUNT 폴백)로 증가 | 결과 있는 검색(지배적 경로)이 2→1쿼리로 줄어드는 대가. 두 경우 모두 파생 테이블 비용은 동일하며, `round5-search-optimization.md` § 3에 트레이드오프와 실측(249→129 ms) 기록됨 |
| `var totalElements`를 RowMapper 람다가 캡처·변이 | RowMapper는 호출 스레드에서 행 단위 동기 실행 — 동시성 문제 없음. 행마다 동일 값 재대입은 무시 가능. `ResultSetExtractor` 대안은 가독성 손해만 있음 |
| `COUNT(*) OVER()`가 LIMIT/OFFSET의 영향을 받는지 | 윈도 함수는 외부 쿼리의 LIMIT 적용 전 전체 결과 기준으로 평가 — 통합 테스트(`paginates`, `returnsTotalOnOutOfRangePage`)와 실측으로 확인 |
| COUNT_SQL 폴백에 미사용 `:limit`/`:offset` 파라미터 전달 | NamedParameterJdbcTemplate 은 SQL 에 존재하는 플레이스홀더만 치환 — 잉여 파라미터는 무해 |
| totalElements=0 경계값 | totalPages = (0+size−1)/size = 0 — 구 early-return 의 `PageResult(emptyList(), page, size, 0, 0)` 과 동일한 결과 유지 |
| `queryForObject` null 처리 | COUNT 는 항상 1행 반환하나 시그니처상 nullable — `?: 0L` 로 방어됨 |
| 산술 오버플로 | offset 은 `page.toLong() * size`, totalPages 계산도 Long 연산 후 toInt — 실용 범위에서 안전 |
| coupon 2개 파일 import 재정렬 | 순수 스타일 변경(빈 줄 제거 + 사전순 병합), ktlintCheck 통과. `CouponRepository`는 이탈 없어 미변경 — 1차 m2 의 "3개 파일" 중 실제 이탈은 2개였음이 커밋 메시지에 기록됨 |
| 문서 정합성 (자동 제외분 수동 확인) | −48% = 120/249 산식 일치, TTL 70초 = 60s + 지터 최대 10s 일치, 1차 체크리스트 완료 표기가 실제 커밋과 일치, 신규 테스트 단언(content 비어 있음 · totalElements=3 · totalPages=2)이 폴백 경로를 정확히 커버 |

## 미해결 항목 현황 (코드 작업 아님 — 정책 결정 대기)

| 항목 | 상태 |
|------|------|
| M1 잔여: stale-while-revalidate 또는 single-flight | 후속 과제로 기록됨 (`round5-load-test.md` § 5) |
| Q1: 숙소 상세 negative cache | 정책 결정 대기 |
| Q2: `save` 포트의 프로덕션 미사용 | 의도 확인 대기 |
| k6 cold p95 임계값 1500 ms vs 실측 4.2 s | SLO 정책 선택 대기 (2차 리뷰 관찰 사항) |
