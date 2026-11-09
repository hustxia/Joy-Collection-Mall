import request from '@/utils/request'

// 商品收藏（V7）；baseURL 已含 /shop，切换接口幂等
export const toggleFavorite = goodsId => request.post(`/favorite/${goodsId}`)
export const checkFavorite = goodsId => request.get(`/favorite/check/${goodsId}`)
export const getFavoriteList = params => request.get('/favorite/list', { params })
