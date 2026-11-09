import request from '@/utils/request'

// V12 物流：按订单查全部包裹与轨迹节点
export const getDeliveryTrack = orderId => request.get(`/shop/delivery/track/${orderId}`)
