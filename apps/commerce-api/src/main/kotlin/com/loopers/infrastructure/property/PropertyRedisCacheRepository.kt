package com.loopers.infrastructure.property

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.property.PropertyCacheRepository
import com.loopers.application.property.PropertyInfo
import com.loopers.config.redis.RedisConfig
import com.loopers.domain.property.PropertySearchCondition
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

/**
 * Redis cache-aside 구현. 읽기는 replica-preferred 템플릿, 쓰기는 master 템플릿을 사용한다.
 *
 * 모든 Redis 연산은 runCatching 으로 감싼다 — Redis 장애 시 조회는 미스(null)로,
 * 저장은 스킵으로 폴백하여 API 가용성을 유지한다(PropertyCacheRepository 계약).
 */
@Component
class PropertyRedisCacheRepository(
    defaultRedisTemplate: RedisTemplate<*, *>,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) masterRedisTemplate: RedisTemplate<*, *>,
    private val objectMapper: ObjectMapper,
) : PropertyCacheRepository {
    @Suppress("UNCHECKED_CAST")
    private val reader = defaultRedisTemplate as RedisTemplate<String, String>

    @Suppress("UNCHECKED_CAST")
    private val writer = masterRedisTemplate as RedisTemplate<String, String>

    override fun findDetail(propertyId: Long): PropertyInfo.DetailBase? =
        read(detailKey(propertyId), PropertyInfo.DetailBase::class.java)

    override fun saveDetail(detail: PropertyInfo.DetailBase) {
        write(detailKey(detail.id), detail, DETAIL_TTL)
    }

    override fun findSearchPage(condition: PropertySearchCondition): PropertyInfo.SearchPage? =
        read(searchKey(condition), PropertyInfo.SearchPage::class.java)

    override fun saveSearchPage(condition: PropertySearchCondition, page: PropertyInfo.SearchPage) {
        // 같은 시점에 적재된 검색 키들이 동시에 만료되어 DB로 몰리는 것(캐시 스탬피드)을 지터로 분산한다.
        val jitterSeconds = SEARCH_TTL_JITTER.seconds
        val jitter = ThreadLocalRandom.current().nextLong(-jitterSeconds, jitterSeconds + 1)
        write(searchKey(condition), page, SEARCH_TTL.plusSeconds(jitter))
    }

    private fun <T> read(key: String, type: Class<T>): T? = runCatching {
        reader.opsForValue().get(key)?.let { objectMapper.readValue(it, type) }
    }.getOrElse { e ->
        logger.warn("캐시 조회 실패 — DB 조회로 폴백한다. key={}", key, e)
        null
    }

    private fun write(key: String, value: Any, ttl: Duration) {
        runCatching {
            writer.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl)
        }.onFailure { e ->
            logger.warn("캐시 저장 실패 — 저장을 건너뛴다. key={}", key, e)
        }
    }

    private fun detailKey(propertyId: Long): String = "property:v1:detail:$propertyId"

    private fun searchKey(c: PropertySearchCondition): String =
        "property:v1:search:${c.city}:${c.checkIn}:${c.checkOut}:${c.guestCount}:${c.sort}:${c.page}:${c.size}"

    companion object {
        private val logger = LoggerFactory.getLogger(PropertyRedisCacheRepository::class.java)

        /** 상세 기본 정보 — 이름·객실 구성은 저빈도 변경이라 10분. */
        private val DETAIL_TTL = Duration.ofMinutes(10)

        /** 검색 페이지 — 재고·찜 변동 반영 지연 상한 60초(지터 포함 최대 70초). */
        private val SEARCH_TTL = Duration.ofSeconds(60)

        /** 동시 만료 스탬피드 방지 지터 폭 — 저장 시 TTL을 60초 ± 10초로 분산한다. */
        private val SEARCH_TTL_JITTER = Duration.ofSeconds(10)
    }
}
