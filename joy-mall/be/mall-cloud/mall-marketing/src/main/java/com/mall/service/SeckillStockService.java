package com.mall.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 秒杀库存加速层（V8）：Redis Lua 原子预扣，DB 乐观锁兜底。
 *
 * <p>职责边界：Redis 只是「第一道闸门」挡流量，真正的防超卖闸门是
 * {@code UPDATE ... SET available_stock = available_stock - 1 WHERE available_stock > 0}。
 * Redis 不可用（连接失败/超时）时降级 DEGRADED，调用方直接走 DB 乐观锁，业务不中断。
 */
@Slf4j
@Service
public class SeckillStockService {

    /** 预扣结果 */
    public enum DeductResult {
        /** Redis 预扣成功（还需 DB 乐观锁确认） */
        OK,
        /** Redis 判定已售罄（快速失败） */
        SOLD_OUT,
        /** Redis 不可用，降级纯 DB 乐观锁 */
        DEGRADED
    }

    private static final String KEY_PREFIX = "mall:seckill:stock:";

    /** 原子预扣：库存 > 0 才 DECR 返 1，否则返 0（不存在也视为售罄，由调用方先惰性初始化） */
    private static final DefaultRedisScript<Long> DEDUCT_SCRIPT = new DefaultRedisScript<>("""
            local v = redis.call('get', KEYS[1])
            if v and tonumber(v) > 0 then
                redis.call('decr', KEYS[1])
                return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public SeckillStockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试 Redis 预扣。key 不存在时以 DB 当前剩余库存惰性初始化（SET NX，并发下最多有一个初始化成功，
     * 即便初始化值略旧也不影响正确性——DB 乐观锁是最终闸门）。
     */
    public DeductResult tryDeduct(Long activityId, int dbAvailableStock) {
        String key = KEY_PREFIX + activityId;
        try {
            redisTemplate.opsForValue().setIfAbsent(key, String.valueOf(dbAvailableStock));
            Long r = redisTemplate.execute(DEDUCT_SCRIPT, List.of(key));
            return r != null && r == 1L ? DeductResult.OK : DeductResult.SOLD_OUT;
        } catch (DataAccessException e) {
            log.warn("[seckill] Redis 不可用，降级 DB 乐观锁：activityId={}, err={}", activityId, e.getMessage());
            return DeductResult.DEGRADED;
        }
    }

    /** 补偿回滚（下单失败/事务回滚后把 Redis 预扣还回去）；Redis 仍不可用则静默——DB 口径为准 */
    public void compensate(Long activityId) {
        try {
            redisTemplate.opsForValue().increment(KEY_PREFIX + activityId);
        } catch (DataAccessException e) {
            log.warn("[seckill] Redis 补偿失败（DB 口径不受影响）：activityId={}, err={}", activityId, e.getMessage());
        }
    }
}
