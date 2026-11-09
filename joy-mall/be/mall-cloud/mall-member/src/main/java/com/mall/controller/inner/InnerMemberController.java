package com.mall.controller.inner;

import com.mall.common.exception.BusinessException;
import com.mall.common.feign.dto.AddressBrief;
import com.mall.common.feign.dto.CartBrief;
import com.mall.common.feign.dto.MemberBenefitBrief;
import com.mall.common.feign.dto.MemberBrief;
import com.mall.entity.MallAddress;
import com.mall.entity.MallCart;
import com.mall.entity.MallMember;
import com.mall.service.MallAddressService;
import com.mall.service.MallCartService;
import com.mall.service.MallMemberPointLogService;
import com.mall.service.MallMemberService;
import com.mall.service.MallNoticeService;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会员域内部端点（/inner/**，免鉴权）：供订单域读取地址/购物车/会员、清空购物车。
 *
 * <p>内部 RPC 直接返回原始载荷（不套 Result 壳）；业务失败抛 BusinessException，
 * 由全局异常处理统一包成 Result（如积分不足 → code=400）。
 */
@RestController
@RequestMapping("/inner")
public class InnerMemberController {

    private final MallAddressService mallAddressService;
    private final MallCartService mallCartService;
    private final MallMemberService mallMemberService;
    private final MallMemberPointLogService pointLogService;
    private final MallNoticeService noticeService;
    private final com.mall.service.MallMemberLevelService memberLevelService;
    private final com.mall.service.MallPriceRemindService priceRemindService;
    private final JdbcTemplate jdbcTemplate;

    public InnerMemberController(MallAddressService mallAddressService,
                                 MallCartService mallCartService,
                                 MallMemberService mallMemberService,
                                 MallMemberPointLogService pointLogService,
                                 MallNoticeService noticeService,
                                 com.mall.service.MallMemberLevelService memberLevelService,
                                 com.mall.service.MallPriceRemindService priceRemindService,
                                 JdbcTemplate jdbcTemplate) {
        this.mallAddressService = mallAddressService;
        this.mallCartService = mallCartService;
        this.mallMemberService = mallMemberService;
        this.pointLogService = pointLogService;
        this.noticeService = noticeService;
        this.memberLevelService = memberLevelService;
        this.priceRemindService = priceRemindService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/address/{id}")
    public AddressBrief address(@PathVariable("id") Long id) {
        MallAddress a = mallAddressService.getById(id);
        if (a == null) {
            return null;
        }
        AddressBrief b = new AddressBrief();
        b.setId(a.getId());
        b.setMemberId(a.getMemberId());
        b.setReceiverName(a.getReceiverName());
        b.setReceiverPhone(a.getReceiverPhone());
        b.setProvince(a.getProvince());
        b.setCity(a.getCity());
        b.setDistrict(a.getDistrict());
        b.setDetail(a.getDetail());
        b.setIsDefault(a.getIsDefault());
        return b;
    }

    @GetMapping("/cart")
    public List<CartBrief> cart(@RequestParam("memberId") Long memberId,
                                @RequestParam(value = "checked", required = false) Integer checked) {
        return mallCartService.cartOf(memberId, checked).stream().map(c -> {
            CartBrief b = new CartBrief();
            b.setId(c.getId());
            b.setMemberId(c.getMemberId());
            b.setSkuId(c.getSkuId());
            b.setNum(c.getNum());
            b.setChecked(c.getChecked());
            return b;
        }).toList();
    }

    @GetMapping("/member/{id}")
    public MemberBrief member(@PathVariable("id") Long id) {
        MallMember m = mallMemberService.getById(id);
        return m == null ? null : toMemberBrief(m);
    }

    @GetMapping("/member/by-ids")
    public List<MemberBrief> membersByIds(@RequestParam("ids") List<Long> ids) {
        return mallMemberService.listByIds(ids).stream().map(this::toMemberBrief).toList();
    }

    @PostMapping("/cart/clear")
    public void clearCart(@RequestParam("memberId") Long memberId,
                          @RequestParam(value = "ids", required = false) List<Long> ids) {
        mallCartService.clearByIdIn(memberId, ids);
    }

    /**
     * 积分发放/扣减（V7）：changeType 1消费返 2签到 3下单抵扣 4退款扣回，points 正加负减。
     * 余额不足/会员不存在抛业务异常（调用方拿到 code=400 msg=积分不足）；成功返回变动后余额。
     */
    @PostMapping("/point/grant")
    public Map<String, Object> grantPoints(@RequestBody PointGrantDTO dto) {
        if (dto == null || dto.getMemberId() == null || dto.getChangeType() == null
                || dto.getPoints() == null || dto.getPoints() == 0) {
            throw new BusinessException(400, "参数错误：memberId/changeType/points 必填且 points≠0");
        }
        int balance = pointLogService.grant(dto.getMemberId(), dto.getChangeType(), dto.getPoints(),
                dto.getOrderId(), dto.getRemark());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("points", balance);
        return data;
    }

    /** 站内消息推送（V7）：type 1订单 2售后 3营销，返回消息ID */
    @PostMapping("/notice/push")
    public Long pushNotice(@RequestBody NoticePushDTO dto) {
        if (dto == null || dto.getMemberId() == null || dto.getType() == null
                || dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new BusinessException(400, "参数错误：memberId/type/title 必填");
        }
        return noticeService.push(dto.getMemberId(), dto.getType(), dto.getTitle(),
                dto.getContent(), dto.getBizId());
    }

    /**
     * 佣金余额增减（V9）：delta 正加负减，原子 UPDATE 并校验余额不为负；返回是否成功（false=余额不足或会员不存在）。
     */
    @PostMapping("/member/commission-balance")
    public Boolean adjustCommissionBalance(@RequestParam("memberId") Long memberId,
                                          @RequestParam("delta") Long delta) {
        if (memberId == null || delta == null) {
            return false;
        }
        int affected = jdbcTemplate.update(
                "UPDATE mall_member SET commission_balance = commission_balance + ? "
                        + "WHERE id = ? AND commission_balance + ? >= 0 AND deleted = 0",
                delta, memberId, delta);
        return affected == 1;
    }

    private MemberBrief toMemberBrief(MallMember m) {
        MemberBrief b = new MemberBrief();
        b.setId(m.getId());
        b.setNickname(m.getNickname());
        b.setAvatar(m.getAvatar());
        b.setPhone(m.getPhone());
        return b;
    }

    // ===== V13 会员等级与权益 =====

    /** 会员权益快照：订单域算「活动之后的会员折扣」并判断能否免运费 */
    @GetMapping("/member/benefit/{id}")
    public MemberBenefitBrief memberBenefit(@PathVariable("id") Long id) {
        return memberLevelService.benefitOf(id);
    }

    /** 扣一次包邮次数（条件 UPDATE，本月额度用完返回 false，订单照常按运费模板计费） */
    @PostMapping("/member/free-shipping/consume")
    public Boolean consumeFreeShipping(@RequestParam("memberId") Long memberId) {
        return memberLevelService.consumeFreeShipping(memberId);
    }

    /**
     * 成长值增减（V13）：消费 1 元 = 1 点、签到、评价三处共用；返回变动后成长值。
     * <p>升级判定与等级日志都在 Service 内，调用方不需要知道阈值。
     */
    @PostMapping("/member/growth")
    public Integer grantGrowth(@RequestParam("memberId") Long memberId,
                               @RequestParam("value") Long value,
                               @RequestParam(value = "reason", required = false) String reason) {
        if (memberId == null || value == null) {
            return 0;
        }
        return memberLevelService.grantGrowth(memberId, value, reason);
    }

    /** 降价提醒扫描（定时任务/运维手动触发；返回本轮通知条数） */
    @PostMapping("/price-remind/sweep")
    public Integer sweepPriceRemind() {
        return priceRemindService.sweep();
    }

    /** 积分发放入参（V7）：points 正加负减 */
    @Data
    public static class PointGrantDTO {

        private Long memberId;
        private Integer changeType;
        private Integer points;
        private Long orderId;
        private String remark;
    }

    /** 站内消息推送入参（V7） */
    @Data
    public static class NoticePushDTO {

        private Long memberId;
        private Integer type;
        private String title;
        private String content;
        private String bizId;
    }
}
