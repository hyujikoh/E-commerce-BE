package com.loopers.application.property

import com.loopers.domain.property.PropertySearchCondition

/**
 * 숙소 조회 캐시 포트. 캐시 대상이 application 조합 결과(Info)이므로 포트도 application 계층에 둔다.
 *
 * 구현은 장애 시 예외를 전파하지 않고 null(미스)로 폴백해야 한다 — 캐시는 가용성 최적화일 뿐,
 * 실패가 조회 자체를 실패시키면 안 된다.
 */
interface PropertyCacheRepository {
    fun findDetail(propertyId: Long): PropertyInfo.DetailBase?

    fun saveDetail(detail: PropertyInfo.DetailBase)

    fun findSearchPage(condition: PropertySearchCondition): PropertyInfo.SearchPage?

    fun saveSearchPage(condition: PropertySearchCondition, page: PropertyInfo.SearchPage)
}
