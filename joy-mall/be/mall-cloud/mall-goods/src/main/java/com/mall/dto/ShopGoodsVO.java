package com.mall.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商城前台（PC/H5）商品视图——只暴露买家可见字段，不含 remark/审计字段。
 * 价格单位：分（Long），前台展示时 /100 转元。
 */
public class ShopGoodsVO {

    private Long id;
    private String goodsNo;
    private String name;
    private String subTitle;
    private String mainImage;
    /** 轮播图 URL 列表（mall_goods.slider_images JSON 数组字符串已解析） */
    private List<String> sliderImages;
    /** 商品详情富文本 HTML */
    private String detail;
    private Long priceMin;
    private Long priceMax;
    private Integer sales;
    private Long categoryId;
    private String categoryName;
    private Long brandId;
    private String brandName;
    /** 上架 SKU 列表（详情接口填充，列表接口为 null） */
    private List<ShopSkuVO> skus;
    /** 评价平均分（status=0，1 位小数，无评价为 0；详情接口填充） */
    private BigDecimal ratingAvg;
    /** 评价总数（status=0；详情接口填充） */
    private Integer ratingCount;
    /** V10 所属店铺ID：1=平台自营店铺 */
    private Long merchantId;
    /** V10 所属店铺名（列表/详情填充，商家域不可用时为 null） */
    private String merchantName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getGoodsNo() { return goodsNo; }
    public void setGoodsNo(String goodsNo) { this.goodsNo = goodsNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSubTitle() { return subTitle; }
    public void setSubTitle(String subTitle) { this.subTitle = subTitle; }
    public String getMainImage() { return mainImage; }
    public void setMainImage(String mainImage) { this.mainImage = mainImage; }
    public List<String> getSliderImages() { return sliderImages; }
    public void setSliderImages(List<String> sliderImages) { this.sliderImages = sliderImages; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Long getPriceMin() { return priceMin; }
    public void setPriceMin(Long priceMin) { this.priceMin = priceMin; }
    public Long getPriceMax() { return priceMax; }
    public void setPriceMax(Long priceMax) { this.priceMax = priceMax; }
    public Integer getSales() { return sales; }
    public void setSales(Integer sales) { this.sales = sales; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public Long getBrandId() { return brandId; }
    public void setBrandId(Long brandId) { this.brandId = brandId; }
    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }
    public List<ShopSkuVO> getSkus() { return skus; }
    public void setSkus(List<ShopSkuVO> skus) { this.skus = skus; }
    public BigDecimal getRatingAvg() { return ratingAvg; }
    public void setRatingAvg(BigDecimal ratingAvg) { this.ratingAvg = ratingAvg; }
    public Integer getRatingCount() { return ratingCount; }
    public void setRatingCount(Integer ratingCount) { this.ratingCount = ratingCount; }
    public Long getMerchantId() { return merchantId; }
    public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
}
