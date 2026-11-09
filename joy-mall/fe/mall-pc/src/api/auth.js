import request from '@/utils/request'

// 会员认证（register/login 在 /shop 白名单里免登录）
export const register = data => request.post('/auth/register', data)
export const login = data => request.post('/auth/login', data)
export const logout = () => request.post('/auth/logout')
export const getProfile = () => request.get('/auth/profile')
export const updateProfile = data => request.put('/auth/profile', data)
