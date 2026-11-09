import request from '@/utils/request'

// 商品收藏（V7）：需会员登录；切换接口幂等
export const toggleFavorite = goodsId => request.post(`/shop/favorite/${goodsId}`)
export const checkFavorite = goodsId => request.get(`/shop/favorite/check/${goodsId}`)
export const getFavoriteList = params => request.get('/shop/favorite/list', { params })
