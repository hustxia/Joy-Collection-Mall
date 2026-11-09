package com.mall.common.feign;

import com.mall.common.feign.dto.AddressBrief;
import com.mall.common.feign.dto.CartBrief;
import com.mall.common.feign.dto.MemberBenefitBrief;
import com.mall.common.feign.dto.MemberBrief;
import com.mall.common.feign.dto.NoticePushDTO;
import com.mall.common.feign.dto.PointGrantDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 会员域内部客户端：订单域创建订单时读取地址/购物车、下单后清空购物车、读取会员信息。
 * <p>静态地址：url 取 feign.clients.mall-member，缺省 http://localhost:8203。
 */
@FeignClient(name = "mall-member", url = "${feign.clients.mall-member:http://localhost:8203}")
public interface MemberInnerClient {

    @GetMapping("/inner/address/{id}")
    AddressBrief address(@PathVariable("id") Long id);

    @GetMapping("/inner/cart")
    List<CartBrief> cartList(@RequestParam("memberId") Long memberId,
                             @RequestParam(value = "checked", required = false) Integer checked);

    @GetMapping("/inner/member/{id}")
    MemberBrief member(@PathVariable("id") Long id);

    @GetMapping("/inner/member/by-ids")
    List<MemberBrief> membersByIds(@RequestParam("ids") List<Long> ids);

    /** 订单创建成功后清空已购购物车项 */
    @PostMapping("/inner/cart/clear")
    void clearCart(@RequestParam("memberId") Long memberId,
                   @RequestParam(value = "ids", required = false) List<Long> ids);

    /** V7 积分发放/扣减：返回变动后余额 {points}；余额不足抛业务异常（Feign 侧表现为 400） */
    @PostMapping("/inner/point/grant")
    Map<String, Object> grantPoints(@RequestBody PointGrantDTO dto);

    /** V7 站内消息推送：返回消息ID */
    @PostMapping("/inner/notice/push")
    Long pushNotice(@RequestBody NoticePushDTO dto);

    /** V9 佣金余额增减：返回是否成功（余额不足/会员不存在为 false） */
    @PostMapping("/inner/member/commission-balance")
    Boolean commissionBalanceAdjust(@RequestParam("memberId") Long memberId,
                                    @RequestParam("delta") Long delta);

    /**
     * V13 会员权益快照：等级折扣率 + 本月剩余包邮次数。订单域拿它算「活动之后的会员折扣」，
     * 并在下单成功后扣一次包邮次数。
     */
    @GetMapping("/inner/member/benefit/{id}")
    MemberBenefitBrief memberBenefit(@PathVariable("id") Long id);

    /** V13 扣一次包邮次数（幂等口径：本月次数用完返回 false，订单照常按运费模板计费） */
    @PostMapping("/inner/member/free-shipping/consume")
    Boolean consumeFreeShipping(@RequestParam("memberId") Long memberId);

    /** V13 成长值增减（消费/签到/评价），返回变动后成长值；负数允许（退款回退） */
    @PostMapping("/inner/member/growth")
    Integer grantGrowth(@RequestParam("memberId") Long memberId,
                        @RequestParam("value") Long value,
                        @RequestParam(value = "reason", required = false) String reason);
}
