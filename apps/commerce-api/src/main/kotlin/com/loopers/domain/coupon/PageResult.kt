package com.loopers.domain.coupon

/**
 * 도메인 포트가 Spring Data 타입(Page/Pageable)에 직접 의존하지 않도록 둔 단순 페이지 결과.
 * (admin 쿠폰/발급내역 목록에서만 사용한다.)
 */
data class PageResult<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    fun <R> map(transform: (T) -> R): PageResult<R> =
        PageResult(content.map(transform), page, size, totalElements, totalPages)
}
