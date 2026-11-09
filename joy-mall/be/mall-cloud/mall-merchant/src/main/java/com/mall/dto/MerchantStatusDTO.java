package com.mall.dto;

import lombok.Data;

/**
 * 店铺启用/停用入参
 */
@Data
public class MerchantStatusDTO {

    private Long id;

    /** true=启用，false=停用 */
    private Boolean enable;
}
