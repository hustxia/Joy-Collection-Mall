package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.entity.MallCategory;
import com.mall.mapper.MallCategoryMapper;
import com.mall.service.MallCategoryService;
import com.mall.vo.MallCategoryVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品类目 Service
 */
@Service
public class MallCategoryServiceImpl extends ServiceImpl<MallCategoryMapper, MallCategory> implements MallCategoryService {

    // <gen:begin key="service-body">
    @Override
    public List<MallCategoryVO> tree() {
        List<MallCategory> all = list(new LambdaQueryWrapper<MallCategory>().orderByAsc(MallCategory::getId));
        List<MallCategoryVO> vos = new ArrayList<>();
        for (MallCategory entity : all) {
            MallCategoryVO vo = new MallCategoryVO();
            BeanUtils.copyProperties(entity, vo);
            vos.add(vo);
        }
        Map<Long, MallCategoryVO> index = vos.stream()
                .collect(Collectors.toMap(MallCategoryVO::getId, Function.identity(), (a, b) -> a));
        List<MallCategoryVO> roots = new ArrayList<>();
        for (MallCategoryVO vo : vos) {
            Long parentId = vo.getParentId();
            Long parentKey = parentId == null ? null : Long.valueOf(String.valueOf(parentId));
            MallCategoryVO parent = parentKey == null ? null : index.get(parentKey);
            if (parent == null || parent == vo) {
                roots.add(vo);
            } else {
                parent.getChildren().add(vo);
            }
        }
        return roots;
    }
    // <gen:end key="service-body">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====
}