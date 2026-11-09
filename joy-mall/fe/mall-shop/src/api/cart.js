import request from '@/utils/request'

// 购物车
export const getCartList = () => request.get('/shop/cart/list')
export const addCart = data => request.post('/shop/cart/add', data)
export const updateCart = data => request.put('/shop/cart/update', data)
export const deleteCart = id => request.delete(`/shop/cart/${id}`)
