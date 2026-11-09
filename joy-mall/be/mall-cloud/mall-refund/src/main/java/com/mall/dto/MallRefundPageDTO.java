package com.mall.dto;

import java.time.LocalDateTime;

import com.mall.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 售后退款单分页查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MallRefundPageDTO extends PageQuery {

    // <gen:begin key="dto-fields">
    // <gen:end key="dto-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====

    /** V4 售后闭环：业务化查询条件 */
    private String refundNo;
    private String orderNo;
    /** 状态:1待审核2已同意3已拒绝4已退款5已关闭 */
    private Integer status;
    /** 售后类型:1仅退款2退货退款 */
    private Integer type;

    /** V4 收尾 P2-3：申请时间区间（起），对应 create_time，与导出同口径 */
    private LocalDateTime applyStartTime;
    /** V4 收尾 P2-3：申请时间区间（止），含当日 23:59:59.999 */
    private LocalDateTime applyEndTime;
}