import request from '@/utils/request'

// 站内消息（V7）；baseURL 已含 /shop，type 可空（1订单2售后3营销）
export const getNoticeList = params => request.get('/notice/list', { params })
export const getUnreadCount = () => request.get('/notice/unread-count')
export const readNotice = id => request.post(`/notice/read/${id}`)
export const readAllNotices = () => request.post('/notice/read-all')
