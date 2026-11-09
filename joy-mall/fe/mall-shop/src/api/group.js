import request from '@/utils/request'

// 拼团（V8）：活动列表/详情/开团/参团/我的拼团
export const getGroupActivities = () => request.get('/shop/group/activities')
export const getGroupDetail = id => request.get(`/shop/group/${id}`)
export const openGroup = (id, data) => request.post(`/shop/group/${id}/open`, data)
export const joinGroupTeam = (teamId, data) => request.post(`/shop/group/team/${teamId}/join`, data)
export const getMyGroups = () => request.get('/shop/group/my')
