package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.MallOrder;
import com.mall.dto.MallOrderSaveDTO;
import com.mall.dto.MallOrderItemSaveDTO;
import java.util.List;

/**
 * 订单 Service
 */
public interface MallOrderService extends IService<MallOrder> {

    // <gen:begin key="service-body">
    /**
     * 主表 + 子表同事务保存（每张子表先清后插；参数为 null 表示该子表不动，空列表表示清空）
     */
    void saveWithChildren(MallOrderSaveDTO master,
                          List<MallOrderItemSaveDTO> mallOrderItemRows);

    /**
     * V4.4 按表的删除级联模式删除主表：
     * 级联=同事务删光全部子表（含三级）；阻止=还有子行就拒删；不处理=仅删主行。
     */
    void removeWithChildren(Long id);
    // <gen:end key="service-body">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====
}