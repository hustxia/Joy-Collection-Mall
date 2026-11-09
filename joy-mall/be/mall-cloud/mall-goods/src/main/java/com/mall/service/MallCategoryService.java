package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.MallCategory;
import com.mall.vo.MallCategoryVO;
import java.util.List;

/**
 * 商品类目 Service
 */
public interface MallCategoryService extends IService<MallCategory> {

    // <gen:begin key="service-body">
    /**
     * 树形列表（父子嵌套）
     */
    List<MallCategoryVO> tree();
    // <gen:end key="service-body">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====
}