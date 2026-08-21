package com.loopers.application.property

import com.loopers.domain.property.PropertySearchCondition
import com.loopers.domain.property.PropertyService
import com.loopers.domain.wishlist.WishlistService
import org.springframework.stereotype.Component

/**
 * 숙소 조회 파사드 — cache-aside 캐시와 찜 수 병합을 담당한다.
 *
 * 캐시 전략:
 * - 상세: 찜 수를 제외한 기본 정보(DetailBase)만 TTL 10분 캐시. 찜 수는 매 요청
 *   DB(property_wishlist_count, 이미 비정규화된 카운터)에서 읽어 병합 — 변동이 잦은 값의 스테일 노출 방지.
 * - 검색: 조건별 페이지 전체를 TTL 60초 캐시. 재고·찜 변동의 반영 지연 상한이 60초라는 의미.
 * - 일자별 재고·요금 원본은 캐시하지 않는다. 검색 결과의 가용성·총액은 60초 스테일을 허용하고,
 *   결제(예약 생성) 시점에는 원자적 조건부 UPDATE(InventoryService.reserve)가 DB 최신 재고를 재확인한다.
 */
@Component
class PropertyFacade(
    private val propertyService: PropertyService,
    private val wishlistService: WishlistService,
    private val propertyCacheRepository: PropertyCacheRepository,
) {
    fun getDetail(propertyId: Long): PropertyInfo.Detail {
        val base = propertyCacheRepository.findDetail(propertyId)
            ?: PropertyInfo.DetailBase.from(propertyService.getDetail(propertyId))
                .also { propertyCacheRepository.saveDetail(it) }
        return PropertyInfo.Detail.of(base, wishlistService.getCount(propertyId))
    }

    fun search(condition: PropertySearchCondition): PropertyInfo.SearchPage {
        propertyCacheRepository.findSearchPage(condition)?.let { return it }
        return PropertyInfo.SearchPage.from(propertyService.search(condition))
            .also { propertyCacheRepository.saveSearchPage(condition, it) }
    }
}
