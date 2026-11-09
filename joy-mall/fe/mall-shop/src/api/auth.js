import request from '@/utils/request'

// 认证相关接口
export const register = data => request.post('/shop/auth/register', data)
export const login = data => request.post('/shop/auth/login', data)
export const logout = () => request.post('/shop/auth/logout')
export const getProfile = () => request.get('/shop/auth/profile')
export const updateProfile = data => request.put('/shop/auth/profile', data)
