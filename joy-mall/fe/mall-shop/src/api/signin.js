import request from '@/utils/request'

// 每日签到（V7）：需会员登录
export const signIn = () => request.post('/shop/signin')
export const getSignCalendar = month => request.get('/shop/signin/calendar', { params: { month } })
