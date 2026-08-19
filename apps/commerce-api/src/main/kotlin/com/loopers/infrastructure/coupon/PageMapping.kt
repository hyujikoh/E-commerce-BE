package com.loopers.infrastructure.coupon

import com.loopers.domain.common.PageResult
import org.springframework.data.domain.Page

/** Spring Data Page → 도메인 PageResult 변환. infrastructure 내부 전용. */
fun <T> Page<T>.toPageResult(): PageResult<T> =
    PageResult(
        content = content,
        page = number,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
    )
