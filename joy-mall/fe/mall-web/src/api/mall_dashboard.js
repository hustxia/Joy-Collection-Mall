import request from '@/utils/request'

// 经营看板接口：mall_dashboard（经网关 /api/mall）
// <gen:begin key="api-fns">
// <gen:end key="api-fns">
// ===== 以下为手写区：自定义请求写在这里，重新生成不会被覆盖 =====

// V4 P0-2 经营看板：核心指标聚合
export function getDashboardOverview(range) {
  return request({
    url: '/api/mall/mall_dashboard/overview',
    method: 'get',
    params: { range: range || '30d' }
  })
}
