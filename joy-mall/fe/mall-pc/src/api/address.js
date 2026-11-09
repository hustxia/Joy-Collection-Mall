import request from '@/utils/request'

export const getAddressList = () => request.get('/address/list')
export const addAddress = data => request.post('/address', data)
export const updateAddress = data => request.put('/address', data)
export const deleteAddress = id => request.delete(`/address/${id}`)
export const setDefaultAddress = id => request.put(`/address/${id}/default`)
