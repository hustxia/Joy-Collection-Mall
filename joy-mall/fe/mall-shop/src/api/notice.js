import request from '@/utils/request'

// 站内消息（V7）：需会员登录；type 可空（1订单2售后3营销）
export const getNoticeList = params => request.get('/shop/notice/list', { params })
export const getUnreadCount = () => request.get('/shop/notice/unread-count')
export const readNotice = id => request.post(`/shop/notice/read/${id}`)
export const readAllNotices = () => request.post('/shop/notice/read-all')
