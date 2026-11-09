package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.MallOrder;
import com.mall.mapper.MallOrderMapper;
import com.mall.service.MallOrderService;
import com.mall.dto.MallOrderSaveDTO;
import com.mall.dto.MallOrderItemSaveDTO;
import com.mall.entity.MallOrderItem;
import com.mall.mapper.MallOrderItemMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 订单 Service
 */
@Service
public class MallOrderServiceImpl extends ServiceImpl<MallOrderMapper, MallOrder> implements MallOrderService {

    // <gen:begin key="service-body">
    @Autowired
    private MallOrderItemMapper mallOrderItemMapper;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveWithChildren(MallOrderSaveDTO master,
                                 List<MallOrderItemSaveDTO> mallOrderItemRows) {
        MallOrder entity = new MallOrder();
        BeanUtils.copyProperties(master, entity);
        saveOrUpdate(entity);
        replaceMallOrderItemRows(entity.getId(), mallOrderItemRows);
    }
    /**
     * 订单明细 全量替换：先按外键逻辑删除旧行，再逐行插入，与主表同一事务。
     * rows 为 null 表示本次提交未涉及该子表（不动），空列表表示清空。
     */
    private void replaceMallOrderItemRows(Long masterId, List<MallOrderItemSaveDTO> rows) {
        if (masterId == null) {
            return;
        }
        if (rows == null) {
            return;
        }
        MallOrderItemMapper childMapper = mallOrderItemMapper;
        childMapper.delete(new LambdaQueryWrapper<MallOrderItem>()
                .eq(MallOrderItem::getOrderId, masterId));
        for (MallOrderItemSaveDTO dto : rows) {
            MallOrderItem child = new MallOrderItem();
            BeanUtils.copyProperties(dto, child);
            // 关键：旧行是逻辑删除（deleted=1，物理行仍在），沿用回传主键会撞 PRIMARY。
            // 一律清空主键与审计字段，让雪花 ID 重新生成、时间交给自动填充。
            child.setId(null);
            child.setCreateTime(null);
            child.setUpdateTime(null);
            child.setCreateBy(null);
            child.setUpdateBy(null);
            child.setOrderId(masterId);
            childMapper.insert(child);
        }
    }
    /**
     * V4.4 按删除级联模式删除主表（模式由生成时配置决定，三选一渲染）：
     * 级联=先删各子表行（含三级）再删主行；阻止=任一子表还有有效行就拒删；不处理=仅删主行。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeWithChildren(Long id) {
        // 订单明细：先清三级，再按外键逻辑删除本级
        mallOrderItemMapper.delete(new LambdaQueryWrapper<MallOrderItem>()
                .eq(MallOrderItem::getOrderId, id));
        removeById(id);
    }
    // <gen:end key="service-body">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====
}