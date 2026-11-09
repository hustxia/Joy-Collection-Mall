import request from '@/utils/request'

// 浏览侧公开接口（匿名可访问）
export const getBanners = () => request.get('/shop/banners')
export const getCategories = () => request.get('/shop/categories')
export const getGoodsPage = params => request.get('/shop/goods', { params })
export const getGoodsDetail = id => request.get(`/shop/goods/${id}`)

// V10 店铺（mall-merchant /shop/merchant）：首页店铺入口 + 店铺主页
export const getMerchantList = () => request.get('/shop/merchant/list')
export const getMerchantDetail = id => request.get(`/shop/merchant/${id}`)
