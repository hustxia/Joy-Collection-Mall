package com.mall.controller.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.Result;
import com.mall.entity.MallAddress;
import com.mall.service.MallAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端地址管理（V3）：member_id 隔离，默认地址唯一，首条自动设默认。
 */
@Tag(name = "商城前台-地址", description = "收货地址管理")
@RestController
@RequestMapping("/shop/address")
public class ShopAddressController {

    @Autowired
    private MallAddressService mallAddressService;

    @Operation(summary = "我的地址列表")
    @GetMapping("/list")
    public Result<List<MallAddress>> list() {
        LambdaQueryWrapper<MallAddress> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallAddress::getMemberId, UserContext.getUserId())
                .orderByDesc(MallAddress::getIsDefault)
                .orderByDesc(MallAddress::getId);
        return Result.ok(mallAddressService.list(wrapper));
    }

    @Operation(summary = "新增地址（首条自动设默认）")
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> add(@RequestBody AddressDTO dto) {
        validate(dto);
        Long memberId = UserContext.getUserId();
        MallAddress address = new MallAddress();
        copy(dto, address);
        address.setMemberId(memberId);
        LambdaQueryWrapper<MallAddress> count = new LambdaQueryWrapper<>();
        count.eq(MallAddress::getMemberId, memberId);
        boolean first = mallAddressService.count(count) == 0;
        if (first || Integer.valueOf(1).equals(dto.getIsDefault())) {
            clearDefault(memberId);
            address.setIsDefault(1);
        } else {
            address.setIsDefault(0);
        }
        mallAddressService.save(address);
        return Result.ok();
    }

    @Operation(summary = "修改地址")
    @PutMapping
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> update(@RequestBody AddressDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BusinessException(400, "参数错误：id 必填");
        }
        validate(dto);
        MallAddress address = ownAddress(dto.getId());
        copy(dto, address);
        if (Integer.valueOf(1).equals(dto.getIsDefault())) {
            clearDefault(UserContext.getUserId());
            address.setIsDefault(1);
        } else {
            address.setIsDefault(0);
        }
        mallAddressService.updateById(address);
        return Result.ok();
    }

    @Operation(summary = "删除地址")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        ownAddress(id);
        mallAddressService.removeById(id);
        return Result.ok();
    }

    @Operation(summary = "设为默认地址")
    @PutMapping("/{id}/default")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> setDefault(@PathVariable("id") Long id) {
        MallAddress address = ownAddress(id);
        Long memberId = UserContext.getUserId();
        clearDefault(memberId);
        address.setIsDefault(1);
        mallAddressService.updateById(address);
        return Result.ok();
    }

    private void clearDefault(Long memberId) {
        LambdaUpdateWrapper<MallAddress> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MallAddress::getMemberId, memberId)
                .eq(MallAddress::getIsDefault, 1)
                .set(MallAddress::getIsDefault, 0);
        mallAddressService.update(wrapper);
    }

    private MallAddress ownAddress(Long id) {
        MallAddress address = mallAddressService.getById(id);
        if (address == null) {
            throw new BusinessException(400, "地址不存在");
        }
        if (!address.getMemberId().equals(UserContext.getUserId())) {
            throw new BusinessException(403, "无权操作他人地址");
        }
        return address;
    }

    private void validate(AddressDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getReceiverName())
                || !StringUtils.hasText(dto.getReceiverPhone()) || !StringUtils.hasText(dto.getDetail())) {
            throw new BusinessException(400, "收货人/联系电话/详细地址必填");
        }
    }

    private void copy(AddressDTO dto, MallAddress address) {
        address.setReceiverName(dto.getReceiverName());
        address.setReceiverPhone(dto.getReceiverPhone());
        address.setProvince(dto.getProvince());
        address.setCity(dto.getCity());
        address.setDistrict(dto.getDistrict());
        address.setDetail(dto.getDetail());
    }

    @Data
    public static class AddressDTO {
        private Long id;
        private String receiverName;
        private String receiverPhone;
        private String province;
        private String city;
        private String district;
        private String detail;
        private Integer isDefault;
    }
}
