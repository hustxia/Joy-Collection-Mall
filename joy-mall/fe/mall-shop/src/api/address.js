import request from '@/utils/request'

// 收货地址
export const getAddressList = () => request.get('/shop/address/list')
export const addAddress = data => request.post('/shop/address', data)
export const updateAddress = data => request.put('/shop/address', data)
export const deleteAddress = id => request.delete(`/shop/address/${id}`)
export const setDefaultAddress = id => request.put(`/shop/address/${id}/default`)
