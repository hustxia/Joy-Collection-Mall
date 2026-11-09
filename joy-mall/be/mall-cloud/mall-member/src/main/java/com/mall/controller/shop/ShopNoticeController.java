package com.mall.controller.shop;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.Result;
import com.mall.entity.MallNotice;
import com.mall.service.MallNoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C 端站内消息（V7）：订单/售后/营销通知，按会员隔离，memberId 只从 UserContext 取。
 */
@Tag(name = "商城前台-站内消息", description = "消息列表/未读数/已读")
@RestController
@RequestMapping("/shop/notice")
public class ShopNoticeController {

    private static final int MAX_PAGE_SIZE = 100;

    private final MallNoticeService mallNoticeService;

    public ShopNoticeController(MallNoticeService mallNoticeService) {
        this.mallNoticeService = mallNoticeService;
    }

    @Operation(summary = "我的消息分页（新→旧，type 可空：1订单2售后3营销）")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
                                            @RequestParam(value = "type", required = false) Integer type) {
        Long memberId = requireMemberId();
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        Page<MallNotice> page = mallNoticeService.pageOf(memberId, type, pageNum, size);

        List<Map<String, Object>> rows = page.getRecords().stream().map(notice -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", String.valueOf(notice.getId()));
            row.put("type", notice.getType());
            row.put("title", notice.getTitle());
            row.put("content", notice.getContent());
            row.put("bizId", notice.getBizId());
            row.put("readStatus", notice.getReadStatus());
            row.put("createTime", notice.getCreateTime());
            return row;
        }).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("total", page.getTotal());
        return Result.ok(data);
    }

    @Operation(summary = "未读消息数")
    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", mallNoticeService.unreadCount(requireMemberId()));
        return Result.ok(data);
    }

    @Operation(summary = "已读一条（仅本人消息，重复调用幂等）")
    @PostMapping("/read/{id}")
    public Result<Void> read(@PathVariable("id") Long id) {
        mallNoticeService.markRead(id, requireMemberId());
        return Result.ok();
    }

    @Operation(summary = "全部标记已读")
    @PostMapping("/read-all")
    public Result<Map<String, Object>> readAll() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("read", mallNoticeService.markAllRead(requireMemberId()));
        return Result.ok(data);
    }

    private Long requireMemberId() {
        Long memberId = UserContext.getUserId();
        if (memberId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return memberId;
    }
}
