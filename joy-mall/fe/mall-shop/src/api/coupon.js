import request from '@/utils/request'

// 优惠券
export const getCouponCenter = () => request.get('/shop/coupon/center')
export const receiveCoupon = couponId => request.post('/shop/coupon/receive', { couponId })
export const getMyCoupons = status => request.get('/shop/coupon/my', { params: { status } })
