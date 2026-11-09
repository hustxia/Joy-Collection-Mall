package com.mall.controller.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.common.feign.OrderInnerClient;
import com.mall.common.feign.dto.GoodsBrief;
import com.mall.common.feign.dto.OrderBrief;
import com.mall.common.feign.dto.PromoOrderCreateDTO;
import com.mall.common.feign.dto.SkuBrief;
import com.mall.common.result.Result;
import com.mall.entity.MallGroupActivity;
import com.mall.entity.MallGroupMember;
import com.mall.entity.MallGroupTeam;
import com.mall.service.MallGroupActivityService;
import com.mall.service.MallGroupMemberService;
import com.mall.service.MallGroupTeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * C 端拼团（V8）：活动列表/详情/开团/参团/我的拼团。
 *
 * <p>成团状态机：团 0拼团中 →（人数满）1已成团 /（expire_time 过）2已失败。
 * 过期判定走懒加载：参团与查询时发现 status=0 且已过期 → 条件更新置 2（本步不做定时任务）。
 * 开团/参团均按拼团价建单（order_type=2），任一步失败整体回滚。
 */
@Tag(name = "商城前台-拼团", description = "拼团活动/开团/参团")
@RestController
@RequestMapping("/shop/group")
public class ShopGroupController {

    private final MallGroupActivityService groupActivityService;
    private final MallGroupTeamService groupTeamService;
    private final MallGroupMemberService groupMemberService;
    private final GoodsInnerClient goodsInnerClient;
    private final OrderInnerClient orderInnerClient;
    /** 只用于本域 mall_group_team 的人数条件更新/成团置位，不跨域 */
    private final JdbcTemplate jdbcTemplate;
    /** 懒过期置失败用 REQUIRES_NEW 独立提交，避免被业务异常的回滚带走 */
    private final PlatformTransactionManager transactionManager;

    public ShopGroupController(MallGroupActivityService groupActivityService,
                               MallGroupTeamService groupTeamService,
                               MallGroupMemberService groupMemberService,
                               GoodsInnerClient goodsInnerClient,
                               OrderInnerClient orderInnerClient,
                               JdbcTemplate jdbcTemplate,
                               PlatformTransactionManager transactionManager) {
        this.groupActivityService = groupActivityService;
        this.groupTeamService = groupTeamService;
        this.groupMemberService = groupMemberService;
        this.goodsInnerClient = goodsInnerClient;
        this.orderInnerClient = orderInnerClient;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
    }

    @Operation(summary = "拼团活动列表：进行中（status=1 且在时间窗内），含进行中团数")
    @GetMapping("/activities")
    public Result<List<Map<String, Object>>> activities() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<MallGroupActivity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallGroupActivity::getStatus, 1)
                .le(MallGroupActivity::getStartTime, now)
                .ge(MallGroupActivity::getEndTime, now)
                .orderByDesc(MallGroupActivity::getId);
        List<MallGroupActivity> activities = groupActivityService.list(wrapper);
        return Result.ok(toActivityRows(activities));
    }

    @Operation(summary = "拼团活动详情（含可参团的进行中团列表）")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        MallGroupActivity activity = groupActivityService.getById(id);
        if (activity == null) {
            throw new BusinessException(400, "活动不存在");
        }
        List<Map<String, Object>> rows = toActivityRows(List.of(activity));
        Map<String, Object> data = rows.get(0);
        data.put("teams", joinableTeams(activity));
        return Result.ok(data);
    }

    @Operation(summary = "开团：校验时间窗 → 建团（含团长）→ 按拼团价建单（事务）")
    @PostMapping("/{id}/open")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> open(@PathVariable("id") Long id, @RequestBody JoinDTO dto) {
        Long memberId = requireLogin();
        if (dto == null || dto.getAddressId() == null) {
            throw new BusinessException(400, "请选择收货地址");
        }
        MallGroupActivity activity = groupActivityService.getById(id);
        LocalDateTime now = LocalDateTime.now();
        if (activity == null || !Integer.valueOf(1).equals(activity.getStatus())
                || now.isBefore(activity.getStartTime()) || now.isAfter(activity.getEndTime())) {
            throw new BusinessException(400, "活动不在进行中");
        }

        MallGroupTeam team = new MallGroupTeam();
        team.setActivityId(id);
        team.setLeaderId(memberId);
        team.setCurrentSize(1);
        team.setStatus(0);
        team.setExpireTime(now.plusHours(activity.getValidHours() == null ? 24 : activity.getValidHours()));
        groupTeamService.save(team);

        MallGroupMember leader = new MallGroupMember();
        leader.setTeamId(team.getId());
        leader.setMemberId(memberId);
        leader.setIsLeader(1);
        groupMemberService.save(leader);

        OrderBrief order = createPromoOrder(activity, memberId, dto);
        leader.setOrderId(order.getId());
        groupMemberService.updateById(leader);

        Map<String, Object> data = orderView(order);
        data.put("teamId", String.valueOf(team.getId()));
        data.put("teamStatus", team.getStatus());
        data.put("currentSize", team.getCurrentSize());
        data.put("groupSize", activity.getGroupSize());
        data.put("expireTime", team.getExpireTime());
        return Result.ok(data);
    }

    @Operation(summary = "参团：懒过期判定 → 防重复 → 人数条件更新 → 建单 → 满员成团（事务）")
    @PostMapping("/team/{teamId}/join")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> join(@PathVariable("teamId") Long teamId, @RequestBody JoinDTO dto) {
        Long memberId = requireLogin();
        if (dto == null || dto.getAddressId() == null) {
            throw new BusinessException(400, "请选择收货地址");
        }
        MallGroupTeam team = groupTeamService.getById(teamId);
        if (team == null) {
            throw new BusinessException(400, "团不存在");
        }
        lazyFailIfExpired(team);
        if (!Integer.valueOf(0).equals(team.getStatus())) {
            throw new BusinessException(400, team.getStatus() == 1 ? "该团已成团" : "该团已过期");
        }
        MallGroupActivity activity = groupActivityService.getById(team.getActivityId());
        if (activity == null) {
            throw new BusinessException(400, "活动不存在");
        }
        int groupSize = activity.getGroupSize() == null ? 2 : activity.getGroupSize();

        LambdaQueryWrapper<MallGroupMember> dupWrapper = new LambdaQueryWrapper<>();
        dupWrapper.eq(MallGroupMember::getTeamId, teamId).eq(MallGroupMember::getMemberId, memberId);
        if (groupMemberService.count(dupWrapper) > 0) {
            throw new BusinessException(400, "请勿重复参团");
        }

        // 容量闸门：条件更新，并发下绝不超员；同时兜住并发过期
        int rows = jdbcTemplate.update(
                "UPDATE mall_group_team SET current_size = current_size + 1"
                        + " WHERE id = ? AND status = 0 AND current_size < ? AND expire_time > NOW() AND deleted = 0",
                teamId, groupSize);
        if (rows == 0) {
            throw new BusinessException(400, "团已满员或已过期");
        }

        MallGroupMember member = new MallGroupMember();
        member.setTeamId(teamId);
        member.setMemberId(memberId);
        member.setIsLeader(0);
        try {
            groupMemberService.save(member);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(400, "请勿重复参团");
        }

        OrderBrief order = createPromoOrder(activity, memberId, dto);
        member.setOrderId(order.getId());
        groupMemberService.updateById(member);

        // 满员成团（条件更新，幂等）
        Integer newSize = jdbcTemplate.queryForObject(
                "SELECT current_size FROM mall_group_team WHERE id = ?", Integer.class, teamId);
        int currentSize = newSize == null ? 0 : newSize;
        int teamStatus = 0;
        if (currentSize >= groupSize) {
            jdbcTemplate.update("UPDATE mall_group_team SET status = 1 WHERE id = ? AND status = 0", teamId);
            teamStatus = 1;
        }

        Map<String, Object> data = orderView(order);
        data.put("teamId", String.valueOf(teamId));
        data.put("teamStatus", teamStatus);
        data.put("currentSize", currentSize);
        data.put("groupSize", groupSize);
        data.put("expireTime", team.getExpireTime());
        return Result.ok(data);
    }

    @Operation(summary = "我的拼团：我参与/发起的团（含活动与商品快照）")
    @GetMapping("/my")
    public Result<List<Map<String, Object>>> my() {
        Long memberId = requireLogin();
        LambdaQueryWrapper<MallGroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallGroupMember::getMemberId, memberId).orderByDesc(MallGroupMember::getId).last("LIMIT 50");
        List<MallGroupMember> memberships = groupMemberService.list(wrapper);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (memberships.isEmpty()) {
            return Result.ok(rows);
        }
        List<Long> teamIds = memberships.stream().map(MallGroupMember::getTeamId).distinct().toList();
        Map<Long, MallGroupTeam> teamMap = groupTeamService.listByIds(teamIds).stream()
                .collect(Collectors.toMap(MallGroupTeam::getId, Function.identity()));
        // 懒过期判定：把已过期的拼团中团置为失败后再展示
        List<MallGroupTeam> expired = teamMap.values().stream()
                .filter(t -> Integer.valueOf(0).equals(t.getStatus())
                        && t.getExpireTime() != null && t.getExpireTime().isBefore(LocalDateTime.now()))
                .toList();
        for (MallGroupTeam t : expired) {
            lazyFailIfExpired(t);
        }
        List<Long> activityIds = teamMap.values().stream().map(MallGroupTeam::getActivityId).distinct().toList();
        Map<Long, MallGroupActivity> activityMap = activityIds.isEmpty() ? Map.of()
                : groupActivityService.listByIds(activityIds).stream()
                        .collect(Collectors.toMap(MallGroupActivity::getId, Function.identity()));
        Map<Long, GoodsBrief> goodsMap = goodsMapOf(activityMap.values().stream()
                .map(MallGroupActivity::getGoodsId).distinct().toList());

        for (MallGroupMember ms : memberships) {
            MallGroupTeam team = teamMap.get(ms.getTeamId());
            if (team == null) {
                continue;
            }
            MallGroupActivity activity = activityMap.get(team.getActivityId());
            GoodsBrief g = activity == null ? null : goodsMap.get(activity.getGoodsId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("teamId", String.valueOf(team.getId()));
            m.put("activityId", String.valueOf(team.getActivityId()));
            m.put("goodsId", activity == null ? null : String.valueOf(activity.getGoodsId()));
            m.put("goodsName", g == null ? null : g.getName());
            m.put("mainImage", g == null ? null : g.getMainImage());
            m.put("groupPrice", activity == null ? null : activity.getGroupPrice());
            m.put("groupSize", activity == null ? null : activity.getGroupSize());
            m.put("currentSize", team.getCurrentSize());
            m.put("teamStatus", team.getStatus());
            m.put("isLeader", ms.getIsLeader());
            m.put("orderId", ms.getOrderId() == null ? null : String.valueOf(ms.getOrderId()));
            m.put("expireTime", team.getExpireTime());
            m.put("createTime", team.getCreateTime());
            rows.add(m);
        }
        return Result.ok(rows);
    }

    // ===== 内部小工具 =====

    /** 可参团的进行中团：拼团中 + 未过期 + 未满员 */
    private List<Map<String, Object>> joinableTeams(MallGroupActivity activity) {
        LambdaQueryWrapper<MallGroupTeam> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallGroupTeam::getActivityId, activity.getId())
                .eq(MallGroupTeam::getStatus, 0)
                .gt(MallGroupTeam::getExpireTime, LocalDateTime.now())
                .orderByDesc(MallGroupTeam::getId)
                .last("LIMIT 10");
        int groupSize = activity.getGroupSize() == null ? 2 : activity.getGroupSize();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MallGroupTeam t : groupTeamService.list(wrapper)) {
            if (t.getCurrentSize() != null && t.getCurrentSize() >= groupSize) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("teamId", String.valueOf(t.getId()));
            m.put("leaderId", String.valueOf(t.getLeaderId()));
            m.put("currentSize", t.getCurrentSize());
            m.put("groupSize", groupSize);
            m.put("expireTime", t.getExpireTime());
            rows.add(m);
        }
        return rows;
    }

    /** 懒过期：status=0 且 expire_time 已过 → 条件更新置 2 并同步内存态（独立事务，拒绝时不随回滚丢失） */
    private void lazyFailIfExpired(MallGroupTeam team) {
        if (!Integer.valueOf(0).equals(team.getStatus()) || team.getExpireTime() == null
                || !team.getExpireTime().isBefore(LocalDateTime.now())) {
            return;
        }
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNew.executeWithoutResult(s -> jdbcTemplate.update(
                "UPDATE mall_group_team SET status = 2 WHERE id = ? AND status = 0", team.getId()));
        team.setStatus(2);
    }

    private List<Map<String, Object>> toActivityRows(List<MallGroupActivity> activities) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (activities.isEmpty()) {
            return rows;
        }
        Map<Long, GoodsBrief> goodsMap = goodsMapOf(activities.stream()
                .map(MallGroupActivity::getGoodsId).distinct().toList());
        for (MallGroupActivity a : activities) {
            GoodsBrief g = goodsMap.get(a.getGoodsId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(a.getId()));
            m.put("goodsId", String.valueOf(a.getGoodsId()));
            m.put("skuId", String.valueOf(a.getSkuId()));
            m.put("goodsName", g == null ? null : g.getName());
            m.put("mainImage", g == null ? null : g.getMainImage());
            m.put("originPrice", a.getOriginPrice());
            m.put("groupPrice", a.getGroupPrice());
            m.put("groupSize", a.getGroupSize());
            m.put("validHours", a.getValidHours());
            m.put("startTime", a.getStartTime());
            m.put("endTime", a.getEndTime());
            rows.add(m);
        }
        return rows;
    }

    private Map<Long, GoodsBrief> goodsMapOf(List<Long> goodsIds) {
        if (goodsIds.isEmpty()) {
            return Map.of();
        }
        List<GoodsBrief> briefs = goodsInnerClient.goodsByIds(goodsIds);
        return briefs == null ? Map.of()
                : briefs.stream().collect(Collectors.toMap(GoodsBrief::getId, Function.identity()));
    }

    /** 订单域建单（营销域定价格快照，order_type=2 拼团）；远程失败统一转业务异常触发回滚 */
    private OrderBrief createPromoOrder(MallGroupActivity activity, Long memberId, JoinDTO dto) {
        PromoOrderCreateDTO req = new PromoOrderCreateDTO();
        req.setMemberId(memberId);
        req.setAddressId(dto.getAddressId());
        req.setGoodsId(activity.getGoodsId());
        req.setSkuId(activity.getSkuId());
        req.setPrice(activity.getGroupPrice());
        req.setNum(1);
        req.setOrderType(2);
        req.setBuyerRemark(dto.getBuyerRemark());
        List<SkuBrief> skus = goodsInnerClient.skuByIds(List.of(activity.getSkuId()));
        SkuBrief sku = skus == null || skus.isEmpty() ? null : skus.get(0);
        List<GoodsBrief> goods = goodsInnerClient.goodsByIds(List.of(activity.getGoodsId()));
        GoodsBrief g = goods == null || goods.isEmpty() ? null : goods.get(0);
        req.setGoodsName(g == null ? null : g.getName());
        req.setSkuImage(sku != null && sku.getImage() != null ? sku.getImage()
                : g == null ? null : g.getMainImage());
        req.setSpecJson(sku == null ? null : sku.getSpecJson());
        try {
            OrderBrief order = orderInnerClient.createPromoOrder(req);
            if (order == null || order.getId() == null) {
                throw new BusinessException(500, "下单失败，请稍后重试");
            }
            return order;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "下单失败，请稍后重试");
        }
    }

    private Map<String, Object> orderView(OrderBrief order) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", String.valueOf(order.getId()));
        m.put("orderNo", order.getOrderNo());
        m.put("payAmount", order.getPayAmount());
        return m;
    }

    private Long requireLogin() {
        Long memberId = UserContext.getUserId();
        if (memberId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return memberId;
    }

    @Data
    public static class JoinDTO {
        private Long addressId;
        private String buyerRemark;
    }
}
