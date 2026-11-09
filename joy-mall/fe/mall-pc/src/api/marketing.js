import request from '@/utils/request'

// 营销中心（V8/V9）：秒杀 / 拼团 / 抽奖 / 分销；baseURL 已含 /shop

// ===== 秒杀 =====
// scope: 'ongoing' 进行中（默认）/ 'upcoming' 即将开始（V15）
export const getSeckillActivities = scope => request.get('/seckill/activities', { params: scope ? { scope } : {} })
export const getSeckillDetail = id => request.get(`/seckill/${id}`)
export const seckillBuy = (id, data) => request.post(`/seckill/${id}/buy`, data)

// ===== 拼团 =====
export const getGroupActivities = () => request.get('/group/activities')
export const getGroupDetail = id => request.get(`/group/${id}`)
export const openGroup = (id, data) => request.post(`/group/${id}/open`, data)
export const joinGroupTeam = (teamId, data) => request.post(`/group/team/${teamId}/join`, data)
export const getGroupMine = () => request.get('/group/my')

// ===== 抽奖 =====
export const getLotteryActivities = () => request.get('/lottery/activities')
export const lotteryDraw = (id, data) => request.post(`/lottery/${id}/draw`, data)
export const getLotteryMine = () => request.get('/lottery/my')
export const getLotteryRemain = activityId => request.get('/lottery/my/remain', { params: { activityId } })

// ===== 分销中心 =====
export const getDistributorInfo = () => request.get('/distributor/info')
export const applyDistributor = () => request.post('/distributor/apply')
export const bindDistributor = data => request.post('/distributor/bind', data)
export const getDistributorTeam = () => request.get('/distributor/team')
export const getCommissionPage = params => request.get('/distributor/commission', { params })
export const applyWithdraw = data => request.post('/distributor/withdraw', data)
