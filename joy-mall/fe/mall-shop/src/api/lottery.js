import request from '@/utils/request'

// 抽奖（V8）：活动列表/抽奖/我的记录/今日剩余次数
export const getLotteryActivities = () => request.get('/shop/lottery/activities')
export const drawLottery = (id, data) => request.post(`/shop/lottery/${id}/draw`, data)
export const getMyLotteryRecords = () => request.get('/shop/lottery/my')
export const getLotteryRemain = activityId => request.get('/shop/lottery/my/remain', { params: { activityId } })
