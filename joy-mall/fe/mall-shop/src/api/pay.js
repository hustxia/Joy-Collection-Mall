import request from '@/utils/request'

// V12 预支付：按渠道创建/复用支付单，返回渠道支付参数
export const prepay = data => request.post('/shop/pay/prepay', data)
// V12 渠道可用性：configured=false 的渠道前端不展示可选
export const getPayChannels = () => request.get('/shop/pay/channels')
// 沙箱直付（内部等价 prepay + 自签名回调），重复请求幂等（replay=true）
export const mockPay = orderNo => request.post('/shop/pay/mock', { orderNo })
