package com.mall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.dto.MallMerchantSettlementPageDTO;
import com.mall.entity.MallMerchantBillDetail;
import com.mall.entity.MallMerchantSettlement;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商家结算单：把「待出账」明细按期间聚合成单据凭证。
 */
public interface MallMerchantSettlementService extends IService<MallMerchantSettlement> {

    Page<MallMerchantSettlement> pageQuery(MallMerchantSettlementPageDTO dto);

    /** 生成结算单：聚合该商家 settlement_id 为空的明细，无明细则 400 */
    MallMerchantSettlement build(Long merchantId, LocalDateTime periodStart, LocalDateTime periodEnd,
                                 String operator);

    /** 结算单下的明细 */
    List<MallMerchantBillDetail> details(Long settlementId);
}
