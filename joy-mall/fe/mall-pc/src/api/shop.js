import request from '@/utils/request'

// 浏览侧公开接口（匿名可访问，白名单 GET）
export const getBanners = () => request.get('/banners')
export const getCategories = () => request.get('/categories')
export const getGoodsPage = params => request.get('/goods', { params })
export const getGoodsDetail = id => request.get(`/goods/${id}`)

// V10 店铺（mall-merchant /shop/merchant）：列表用于首页店铺导航，详情用于店铺主页
export const getMerchantList = () => request.get('/merchant/list')
export const getMerchantDetail = id => request.get(`/merchant/${id}`)
