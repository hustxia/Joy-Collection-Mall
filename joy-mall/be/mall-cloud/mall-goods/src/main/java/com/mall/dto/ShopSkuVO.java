package com.mall.dto;

/**
 * 商城前台 SKU 视图——价格单位分。
 */
public class ShopSkuVO {

    private Long id;
    private String skuCode;
    /** 规格 JSON 字符串（原样透传，前台解析展示） */
    private String specJson;
    private Long price;
    private Integer stock;
    private Integer sales;
    private String image;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
    public String getSpecJson() { return specJson; }
    public void setSpecJson(String specJson) { this.specJson = specJson; }
    public Long getPrice() { return price; }
    public void setPrice(Long price) { this.price = price; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public Integer getSales() { return sales; }
    public void setSales(Integer sales) { this.sales = sales; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
}
