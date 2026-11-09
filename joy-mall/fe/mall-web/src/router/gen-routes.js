/* eslint-disable */
// 该文件由代码生成器自动维护，请勿手工编辑。
// 注册/删除菜单后会自动重建；要移除某个生成页面，请先删除表配置。
// 结构为三级：系统 → 业务域（一级菜单） → 页面（二级菜单）。
// 一级菜单按业务逻辑编排，二级菜单在顶部横向排布（超出宽度可向右滑动）。
import { h } from 'vue'
import { RouterView } from 'vue-router'
import Layout from '@/layout/index.vue'
// 必须传组件引用 RouterView，不能传字符串 'router-view'
// Vue3 的 h() 会把字符串当 HTML 原生标签名，导致 Vue Router 识别不到 → 嵌套路由全部白屏
const GroupView = { name: 'GroupView', render: () => h(RouterView) }

export default [
  {
    path: '/mall',
    component: Layout,
    meta: { title: '商城后台' },
    children: [
      {
        // 经营总览：数据看板类
        path: 'dashboard',
        component: GroupView,
        meta: { title: '经营看板' },
        children: [
          {
            path: 'mall_dashboard/index',
            name: 'MallDashboard',
            component: () => import('@/views/mall_dashboard/index.vue'),
            meta: { title: '经营总览' }
          }
        ]
      },
      {
        // 商品域：类目 → 商品 → SKU → 品牌 → 评价
        path: 'product',
        component: GroupView,
        meta: { title: '商品管理' },
        children: [
          {
            path: 'mall_category/list',
            name: 'MallCategory',
            component: () => import('@/views/mall_category/index.vue'),
            meta: { title: '商品类目' }
          },
          {
            path: 'mall_goods/list',
            name: 'MallGoods',
            component: () => import('@/views/mall_goods/index.vue'),
            meta: { title: '商品' }
          },
          {
            path: 'mall_sku/list',
            name: 'MallSku',
            component: () => import('@/views/mall_sku/index.vue'),
            meta: { title: '商品SKU' }
          },
          {
            path: 'mall_brand/list',
            name: 'MallBrand',
            component: () => import('@/views/mall_brand/index.vue'),
            meta: { title: '品牌' }
          },
          {
            path: 'mall_review/list',
            name: 'MallReview',
            component: () => import('@/views/mall_review/index.vue'),
            meta: { title: '评价管理' }
          }
        ]
      },
      {
        // 交易域：下单 → 流转 → 售后
        path: 'order',
        component: GroupView,
        meta: { title: '订单管理' },
        children: [
          {
            path: 'mall_order/list',
            name: 'MallOrder',
            component: () => import('@/views/mall_order/index.vue'),
            meta: { title: '订单' }
          },
          {
            path: 'mall_order_log/list',
            name: 'MallOrderLog',
            component: () => import('@/views/mall_order_log/index.vue'),
            meta: { title: '订单流转日志' }
          },
          {
            path: 'mall_refund/list',
            name: 'MallRefund',
            component: () => import('@/views/mall_refund/index.vue'),
            meta: { title: '售后退款单' }
          }
        ]
      },
      {
        // 会员域：会员 → 收货地址 → 站内消息
        path: 'member',
        component: GroupView,
        meta: { title: '会员管理' },
        children: [
          {
            path: 'mall_member/list',
            name: 'MallMember',
            component: () => import('@/views/mall_member/index.vue'),
            meta: { title: '会员' }
          },
          {
            path: 'mall_address/list',
            name: 'MallAddress',
            component: () => import('@/views/mall_address/index.vue'),
            meta: { title: '收货地址' }
          },
          {
            path: 'mall_notice/list',
            name: 'MallNotice',
            component: () => import('@/views/mall_notice/index.vue'),
            meta: { title: '站内消息' }
          }
        ]
      },
      {
        // 营销域：券 → 领券记录 → 轮播图
        path: 'marketing',
        component: GroupView,
        meta: { title: '营销管理' },
        children: [
          {
            path: 'mall_coupon/list',
            name: 'MallCoupon',
            component: () => import('@/views/mall_coupon/index.vue'),
            meta: { title: '优惠券' }
          },
          {
            path: 'mall_coupon_record/list',
            name: 'MallCouponRecord',
            component: () => import('@/views/mall_coupon_record/index.vue'),
            meta: { title: '领券记录' }
          },
          {
            path: 'mall_banner/list',
            name: 'MallBanner',
            component: () => import('@/views/mall_banner/index.vue'),
            meta: { title: '轮播图' }
          },
          {
            // V13 促销引擎：满减/满折/折扣/套装，与券并行（活动先算、券后算）
            path: 'mall_promotion/list',
            name: 'MallPromotion',
            component: () => import('@/views/mall_promotion/index.vue'),
            meta: { title: '促销活动' }
          },
          {
            // V13 会员等级：成长值 → 自动升级，折扣与包邮次数在下单试算生效
            path: 'mall_member_level/list',
            name: 'MallMemberLevel',
            component: () => import('@/views/mall_member_level/index.vue'),
            meta: { title: '会员等级' }
          },
          {
            // V8 秒杀活动：Redis 预扣 + 乐观锁兜底
            path: 'mall_seckill/list',
            name: 'MallSeckill',
            component: () => import('@/views/mall_seckill/index.vue'),
            meta: { title: '秒杀活动' }
          },
          {
            // V8 拼团活动：团状态机 0拼团中 → 1已成团 / 2已失败
            path: 'mall_group/list',
            name: 'MallGroup',
            component: () => import('@/views/mall_group/index.vue'),
            meta: { title: '拼团活动' }
          },
          {
            // V8 抽奖活动：奖品池权重随机，库存 -1 不限量
            path: 'mall_lottery/list',
            name: 'MallLottery',
            component: () => import('@/views/mall_lottery/index.vue'),
            meta: { title: '抽奖活动' }
          },
          {
            // 分销：分销员/佣金台账/佣金规则/提现审核（4 tabs）
            path: 'mall_distribution/list',
            name: 'MallDistribution',
            component: () => import('@/views/mall_distribution/index.vue'),
            meta: { title: '分销管理' }
          }
        ]
      },
      {
        // 商家域：入驻 → 结算 → 提现
        path: 'merchant',
        component: GroupView,
        meta: { title: '商家管理' },
        children: [
          {
            path: 'mall_merchant/list',
            name: 'MallMerchant',
            component: () => import('@/views/mall_merchant/index.vue'),
            meta: { title: '商家入驻' }
          },
          {
            path: 'mall_merchant_settlement/list',
            name: 'MallMerchantSettlement',
            component: () => import('@/views/mall_merchant_settlement/index.vue'),
            meta: { title: '商家结算' }
          },
          {
            path: 'mall_merchant_withdrawal/list',
            name: 'MallMerchantWithdrawal',
            component: () => import('@/views/mall_merchant_withdrawal/index.vue'),
            meta: { title: '商家提现' }
          }
        ]
      },
      {
        // 交易设置：履约 → 票据 → 资金
        path: 'trade',
        component: GroupView,
        meta: { title: '交易设置' },
        children: [
          {
            path: 'mall_freight_template/list',
            name: 'MallFreightTemplate',
            component: () => import('@/views/mall_freight_template/index.vue'),
            meta: { title: '运费模板' }
          },
          {
            path: 'mall_delivery/list',
            name: 'MallDelivery',
            component: () => import('@/views/mall_delivery/index.vue'),
            meta: { title: '发货单' }
          },
          {
            path: 'mall_invoice/list',
            name: 'MallInvoice',
            component: () => import('@/views/mall_invoice/index.vue'),
            meta: { title: '发票管理' }
          },
          {
            path: 'mall_payment/list',
            name: 'MallPayment',
            component: () => import('@/views/mall_payment/index.vue'),
            meta: { title: '支付流水' }
          },
          {
            path: 'mall_pay_reconcile/list',
            name: 'MallPayReconcile',
            component: () => import('@/views/mall_pay_reconcile/index.vue'),
            meta: { title: '支付对账' }
          }
        ]
      },
      {
        // V14 客服与消息：工单 → 消息台账/模板
        path: 'service',
        component: GroupView,
        meta: { title: '客服与消息' },
        children: [
          {
            path: 'mall_ticket/list',
            name: 'MallTicket',
            component: () => import('@/views/mall_ticket/index.vue'),
            meta: { title: '客服工单' }
          },
          {
            path: 'mall_message/index',
            name: 'MallMessage',
            component: () => import('@/views/mall_message/index.vue'),
            meta: { title: '消息触达' }
          }
        ]
      },
      {
        // V16 AI 客服：会话记录 → FAQ 知识库 → 用量看板
        path: 'aichat',
        component: GroupView,
        meta: { title: 'AI 客服' },
        children: [
          {
            path: 'mall_chat_session/index',
            name: 'MallChatSession',
            component: () => import('@/views/mall_chat_session/index.vue'),
            meta: { title: '会话记录' }
          },
          {
            path: 'mall_chat_faq/index',
            name: 'MallChatFaq',
            component: () => import('@/views/mall_chat_faq/index.vue'),
            meta: { title: 'FAQ 知识库' }
          },
          {
            path: 'mall_chat_stat/index',
            name: 'MallChatStat',
            component: () => import('@/views/mall_chat_stat/index.vue'),
            meta: { title: '用量看板' }
          }
        ]
      }
    ]
  },
]
