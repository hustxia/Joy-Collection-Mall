import request from '@/utils/request'

// 商品评价（V7）：提交需会员登录；商品评价列表匿名可读
export const submitReview = data => request.post('/shop/review', data)
export const getGoodsReviews = (goodsId, params) => request.get(`/shop/review/goods/${goodsId}`, { params })
export const getMyReviews = params => request.get('/shop/review/my', { params })

// V15 评价增强（REQ-P1-05）
/** 评价详情（含追评/有用数/标签/likedByMe） */
export const getReviewDetail = id => request.get(`/shop/review/${id}`)
/** 追评（原评价后 30 天内仅一次） */
export const appendReview = data => request.post('/shop/review/append', data)
/** 点/取消有用（同一会员幂等） */
export const toggleReviewUseful = data => request.post('/shop/review/useful', data)

// 评价图片上传：复用 /shop/upload
export const uploadReviewImage = formData => request.post('/shop/upload', formData, {
  headers: { 'Content-Type': 'multipart/form-data' },
})
