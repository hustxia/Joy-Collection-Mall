import request from '@/utils/request'

// 分销中心（V9）：申请/绑定/我的状态/团队/佣金/提现
export const getDistributorInfo = () => request.get('/shop/distributor/info')
export const applyDistributor = () => request.post('/shop/distributor/apply')
export const bindDistributor = data => request.post('/shop/distributor/bind', data)
export const getDistributorTeam = () => request.get('/shop/distributor/team')
export const getCommissionPage = params => request.get('/shop/distributor/commission', { params })
export const withdrawCommission = data => request.post('/shop/distributor/withdraw', data)
