import request from '@/utils/request'

// 购物车：id 是雪花 ID（19 位），一律字符串透传
export const getCartList = () => request.get('/cart/list')
export const addCart = data => request.post('/cart/add', data)
export const updateCart = data => request.put('/cart/update', data)
export const deleteCart = id => request.delete(`/cart/${id}`)
