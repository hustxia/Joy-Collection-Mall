import request from '@/utils/request'

export const getCouponCenter = () => request.get('/coupon/center')
export const receiveCoupon = couponId => request.post('/coupon/receive', { couponId })
export const getMyCoupons = status => request.get('/coupon/my', { params: { status } })
