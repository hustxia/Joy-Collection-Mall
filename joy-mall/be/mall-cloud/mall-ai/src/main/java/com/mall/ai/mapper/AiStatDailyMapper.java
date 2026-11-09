package com.mall.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.ai.entity.AiStatDaily;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiStatDailyMapper extends BaseMapper<AiStatDaily> {

    /** 当日累加：会话/消息/AI 回复/转人工/兜底/token 一次 upsert，避免读改写竞态 */
    @Insert("INSERT INTO mall_ai_stat_daily (id, stat_date, session_count, message_count, ai_reply_count, "
            + "transfer_count, fallback_count, tokens, deleted, create_time, update_time) "
            + "VALUES (#{id}, CURDATE(), #{session}, #{message}, #{reply}, #{transfer}, #{fallback}, #{tokens}, 0, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE session_count = session_count + VALUES(session_count), "
            + "message_count = message_count + VALUES(message_count), "
            + "ai_reply_count = ai_reply_count + VALUES(ai_reply_count), "
            + "transfer_count = transfer_count + VALUES(transfer_count), "
            + "fallback_count = fallback_count + VALUES(fallback_count), "
            + "tokens = tokens + VALUES(tokens), update_time = NOW()")
    int accumulate(@Param("id") Long id, @Param("session") int session, @Param("message") int message,
                   @Param("reply") int reply, @Param("transfer") int transfer,
                   @Param("fallback") int fallback, @Param("tokens") long tokens);
}
