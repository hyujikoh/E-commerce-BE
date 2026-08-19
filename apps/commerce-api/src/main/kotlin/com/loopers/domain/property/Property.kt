package com.loopers.domain.property

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * 숙소 — 검색·상세 조회의 대상. Round 5 검색 슬라이스에 필요한 최소 필드(name, city)만 갖는다.
 *
 * idx_property_city: 검색이 항상 도시 필터로 시작하므로 city 를 선두로 둔다.
 * (선정 근거와 EXPLAIN 전후 비교는 docs/perf/round5-search-optimization.md 참조)
 */
@Entity
@Table(
    name = "property",
    indexes = [
        Index(name = "idx_property_city", columnList = "city"),
    ],
)
class Property(
    name: String,
    city: String,
) : BaseEntity() {
    @Column(name = "name", nullable = false)
    var name: String = name
        protected set

    @Column(name = "city", nullable = false, length = 50)
    var city: String = city
        protected set

    init {
        if (name.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "숙소 이름은 비어 있을 수 없습니다.")
        }
        if (city.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "도시는 비어 있을 수 없습니다.")
        }
    }
}
