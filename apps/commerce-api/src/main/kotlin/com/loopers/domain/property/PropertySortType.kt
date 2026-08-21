package com.loopers.domain.property

/**
 * 숙소 검색 정렬 기준.
 *
 * RECOMMENDED(추천순)는 찜 수와 가격의 가중 스코어로 정의한다:
 * `0.7 * LN(1 + 찜수) - 0.3 * LN(MAX(총액, 1))` 내림차순.
 * 로그 스케일을 쓰는 이유 — 찜 수·가격 모두 분포가 한쪽으로 치우쳐 있어(멱분포)
 * 원값 그대로 가중하면 한 축이 스코어를 독점하기 때문이다.
 */
enum class PropertySortType {
    /** 총액(기간 합, 숙소 내 최저가 객실 기준) 오름차순 */
    PRICE_ASC,

    /** 찜 수 내림차순 */
    WISHLIST_DESC,

    /** 찜수+가격 가중 스코어 내림차순 */
    RECOMMENDED,
}
