import request from '@/utils/request'

/** 拼团活动管理（V8） */
export function pageGroup(data) {
  return request.post('/mall_group/page', data)
}

export function getGroup(id) {
  return request.get(`/mall_group/${id}`)
}

export function saveGroup(data) {
  return data && data.id ? request.put('/mall_group', data) : request.post('/mall_group', data)
}

export function removeGroup(id) {
  return request.delete(`/mall_group/${id}`)
}

/** 团台账分页：activityId/status（0拼团中1已成团2已失败）过滤 */
export function pageGroupTeam(data) {
  return request.post('/mall_group/team-page', data)
}
