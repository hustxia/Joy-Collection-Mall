package com.mall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.dto.MallMerchantApplyDTO;
import com.mall.dto.MallMerchantPageDTO;
import com.mall.dto.MallMerchantSaveDTO;
import com.mall.entity.MallMerchant;

/**
 * 商家（店铺）服务：入驻申请、审核、启停、资料维护。
 */
public interface MallMerchantService extends IService<MallMerchant> {

    Page<MallMerchant> pageQuery(MallMerchantPageDTO dto);

    /** 提交入驻申请（平台代提交）：同一执照未驳回前只允许一份申请 */
    MallMerchant apply(MallMerchantApplyDTO dto, String operator);

    /** 入驻审核：仅待审可审；驳回必须给原因 */
    void audit(Long id, boolean pass, String rejectReason, String operator);

    /** 店铺启用/停用：启用 2→1，停用 1→2 */
    void changeStatus(Long id, boolean enable, String operator);

    /** 修改店铺资料与抽成费率（不影响已生成的历史结算明细快照） */
    void updateProfile(MallMerchantSaveDTO dto);

    /** 取启用状态的商家，不存在/未启用直接 400 */
    MallMerchant requireEnabled(Long id);
}
