package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.exception.BusinessException;
import com.mall.dto.MessageSendDTO;
import com.mall.dto.MessageTemplateSaveDTO;
import com.mall.entity.MallMessageLog;
import com.mall.entity.MallMessageTemplate;
import com.mall.mapper.MallMessageLogMapper;
import com.mall.mapper.MallMessageTemplateMapper;
import com.mall.message.MessageChannel;
import com.mall.message.MessageDispatcher;
import com.mall.message.MessageLogStore;
import com.mall.message.MessageRequest;
import com.mall.message.MessageTemplateRenderer;
import com.mall.service.MallMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息服务实现（V14）。
 *
 * <p>职责边界：本类负责「查模板 → 渲染 → 交给调度器 → 日志落库」，重试/降级策略全部在
 * {@link MessageDispatcher}（纯逻辑，单测覆盖）；这样改策略不用碰 Spring 代码。
 */
@Slf4j
@Service
public class MallMessageServiceImpl implements MallMessageService, MessageLogStore {

    private final MallMessageTemplateMapper templateMapper;
    private final MallMessageLogMapper logMapper;
    private final MessageDispatcher dispatcher;

    public MallMessageServiceImpl(MallMessageTemplateMapper templateMapper,
                                  MallMessageLogMapper logMapper,
                                  List<MessageChannel> channels,
                                  @Value("${app.message.max-retries:2}") int maxRetries) {
        this.templateMapper = templateMapper;
        this.logMapper = logMapper;
        this.dispatcher = new MessageDispatcher(channels, this, Clock.systemDefaultZone(), maxRetries);
    }

    @Override
    public Map<String, Object> send(MessageSendDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getSceneCode())) {
            throw new BusinessException(400, "场景码不能为空");
        }
        if (dto.getMemberId() == null) {
            throw new BusinessException(400, "接收人不能为空");
        }
        MallMessageTemplate template = templateOf(dto.getSceneCode());
        if (template == null) {
            throw new BusinessException(400, "场景模板不存在或已停用：" + dto.getSceneCode());
        }
        Map<String, Object> vars = new HashMap<>(dto.getVars() == null ? Map.of() : dto.getVars());
        vars.put("bizId", dto.getBizId());
        vars.put("sceneCode", dto.getSceneCode());

        MessageRequest request = new MessageRequest(
                dto.getSceneCode(),
                dto.getMemberId(),
                MessageTemplateRenderer.render(template.getTitleTemplate(), vars),
                MessageTemplateRenderer.render(template.getContentTemplate(), vars),
                dto.getBizId());

        MallMessageLog row = dispatcher.dispatch(request, template.getChannel());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("logId", row.getId() == null ? null : String.valueOf(row.getId()));
        data.put("sceneCode", row.getSceneCode());
        data.put("channel", row.getChannel());
        data.put("status", row.getStatus());
        data.put("statusText", statusText(row.getStatus()));
        data.put("retryCount", row.getRetryCount());
        data.put("degraded", Integer.valueOf(1).equals(row.getDegraded()));
        data.put("errorMsg", row.getErrorMsg());
        return data;
    }

    @Override
    public Map<String, Object> pageLog(Integer status, Integer degraded, String sceneCode,
                                       long pageNum, long pageSize) {
        LambdaQueryWrapper<MallMessageLog> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(MallMessageLog::getStatus, status);
        }
        if (degraded != null) {
            wrapper.eq(MallMessageLog::getDegraded, degraded);
        }
        if (StringUtils.hasText(sceneCode)) {
            wrapper.eq(MallMessageLog::getSceneCode, sceneCode);
        }
        wrapper.orderByDesc(MallMessageLog::getId);
        Page<MallMessageLog> page = logMapper.selectPage(new Page<>(Math.max(pageNum, 1),
                Math.min(Math.max(pageSize, 1), 100)), wrapper);
        return pageOf(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Override
    public Map<String, Object> pageTemplate(String sceneCode, long pageNum, long pageSize) {
        LambdaQueryWrapper<MallMessageTemplate> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(sceneCode)) {
            wrapper.eq(MallMessageTemplate::getSceneCode, sceneCode);
        }
        wrapper.orderByAsc(MallMessageTemplate::getId);
        Page<MallMessageTemplate> page = templateMapper.selectPage(new Page<>(Math.max(pageNum, 1),
                Math.min(Math.max(pageSize, 1), 100)), wrapper);
        return pageOf(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Override
    public void saveTemplate(MessageTemplateSaveDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getSceneCode())) {
            throw new BusinessException(400, "场景码不能为空");
        }
        if (!StringUtils.hasText(dto.getTitleTemplate()) || !StringUtils.hasText(dto.getContentTemplate())) {
            throw new BusinessException(400, "标题与内容模板不能为空");
        }
        String channel = StringUtils.hasText(dto.getChannel()) ? dto.getChannel().trim() : MessageChannel.CODE_SMS;
        if (!MessageChannel.CODE_SMS.equals(channel) && !MessageChannel.CODE_SITE.equals(channel)) {
            throw new BusinessException(400, "通道只支持 sms / site");
        }
        MallMessageTemplate template = dto.getId() == null ? null : templateMapper.selectById(dto.getId());
        if (template == null) {
            template = new MallMessageTemplate();
        }
        template.setSceneCode(dto.getSceneCode().trim());
        template.setSceneName(StringUtils.hasText(dto.getSceneName()) ? dto.getSceneName().trim()
                : dto.getSceneCode().trim());
        template.setTitleTemplate(dto.getTitleTemplate().trim());
        template.setContentTemplate(dto.getContentTemplate().trim());
        template.setChannel(channel);
        template.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        template.setRemark(dto.getRemark());
        if (template.getId() == null) {
            templateMapper.insert(template);
        } else {
            templateMapper.updateById(template);
        }
    }

    /** 日志落库（MessageLogStore 实现，调度器回调） */
    @Override
    public void save(MallMessageLog messageLog) {
        logMapper.insert(messageLog);
    }

    private MallMessageTemplate templateOf(String sceneCode) {
        return templateMapper.selectOne(new LambdaQueryWrapper<MallMessageTemplate>()
                .eq(MallMessageTemplate::getSceneCode, sceneCode.trim())
                .eq(MallMessageTemplate::getStatus, 1)
                .last("LIMIT 1"));
    }

    private String statusText(Integer status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case MallMessageLog.STATUS_SUCCESS -> "成功";
            case MallMessageLog.STATUS_DEGRADED -> "降级成功";
            default -> "失败";
        };
    }

    private Map<String, Object> pageOf(List<?> records, long total, long pageNum, long pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        // 分页字段统一为 records/total/pageNum/pageSize，与 MyBatis-Plus IPage 及各后台列表页同口径
        data.put("records", records);
        data.put("total", total);
        data.put("pageNum", pageNum);
        data.put("pageSize", pageSize);
        return data;
    }
}
