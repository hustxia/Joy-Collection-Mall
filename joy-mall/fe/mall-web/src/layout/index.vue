<template>
  <div class="layout">
    <!-- 一级菜单：业务域，左侧竖排常驻 -->
    <aside class="sidebar">
      <div class="brand" @click="go('/home')">
        <div class="brand-mark">M</div>
        <div class="brand-text">
          <div class="brand-name">悦集商城后台</div>
          <div class="brand-sub">Yueji Mall Console</div>
        </div>
      </div>

      <nav class="primary-menu">
        <div v-for="grp in menus" :key="grp.path" class="primary-block">
          <div
            class="primary-item"
            :class="{ 'is-active': isGrpActive(grp), 'is-open': isOpen(grp) }"
            @click="toggleGroup(grp)"
          >
            <i class="primary-icon" :class="iconOf(grp.path)"></i>
            <span class="primary-title">{{ grp.title }}</span>
            <span class="primary-count">{{ countPages(grp) }}</span>
            <i class="primary-arrow el-icon-arrow-down"></i>
          </div>
          <!-- 二级下拉：展开当前业务域下的页面，不再全部平铺到顶部横条 -->
          <el-collapse-transition>
            <div v-show="isOpen(grp)" class="sub-list">
              <div
                v-for="page in flatPages(grp)"
                :key="page.path"
                class="sub-item"
                :class="{ 'is-active': $route.path === page.path }"
                @click.stop="go(page.path)"
              >
                <span class="sub-dot"></span>
                <span class="sub-title">{{ page.title }}</span>
              </div>
            </div>
          </el-collapse-transition>
        </div>
      </nav>

      <div class="sidebar-foot">
        <div class="foot-item" :class="{ 'is-active': $route.path === '/home' }" @click="go('/home')">
          <i class="el-icon-s-home"></i><span>工作台首页</span>
        </div>
      </div>
    </aside>

    <!-- 主区域 -->
    <div class="main">
      <header class="header">
        <div class="header-left">
          <el-breadcrumb separator="/" class="crumbs">
            <el-breadcrumb-item v-for="(crumb, i) in crumbs" :key="i">{{ crumb }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <span class="env-tag">DEV</span>
          <el-dropdown trigger="click" @command="onUserCmd">
            <div class="user-chip">
              <span class="avatar">{{ (userName || '管').charAt(0) }}</span>
              <span class="user-name">{{ userName }}</span>
              <i class="el-icon-arrow-down"></i>
            </div>
            <template #dropdown><el-dropdown-menu>
              <el-dropdown-item command="logout" icon="el-icon-switch-button">退出登录</el-dropdown-item>
            </el-dropdown-menu></template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script>
import request from '@/utils/request'

/**
 * 两级菜单：
 *   一级 = 业务域，左侧竖排常驻；
 *   二级 = 业务域下的页面，顶部横向排布，宽度超出时可向右滑动（箭头 / 滚轮 / 拖拽）。
 * 菜单树直接从路由表推导（this.$router.options.routes），路由与菜单永远同源；
 * 也不能静态 import 路由表——gen-routes 与本组件互相引用会形成循环依赖，
 * 一旦加载顺序不利，Layout 会被解析成 undefined，所有生成页面直接白屏。
 */
function buildMenu(routes, parentPath) {
  const list = []
  ;(routes || []).forEach(route => {
    if (!route || route.hidden) return
    const path = route.path && route.path.charAt(0) === '/'
      ? route.path
      : (parentPath + '/' + route.path).replace(/\/{2,}/g, '/')
    const children = buildMenu(route.children, path)
    // 只保留「有子节点」或「有实际页面」的节点，Layout 壳子本身不进菜单
    const hasComponent = typeof route.component === 'function'
    if (!children.length && !hasComponent) return
    list.push({
      path,
      title: (route.meta && route.meta.title) || route.name || path,
      children
    })
  })
  return list
}

/** 一级菜单图标：按业务域语义匹配，兜底按序取色板 */
const GROUP_ICONS = [
  { match: /AI 客服|智能客服|机器人/i, icon: 'el-icon-chat-dot-round' },
  { match: /看板|总览|数据|统计/i, icon: 'el-icon-s-data' },
  { match: /商品|类目|SKU|品牌|评价/i, icon: 'el-icon-goods' },
  { match: /订单|流转|售后|退款/i, icon: 'el-icon-s-order' },
  { match: /会员|收货|地址|消息/i, icon: 'el-icon-user' },
  { match: /营销|优惠|券|轮播/i, icon: 'el-icon-present' },
  { match: /商家|结算|提现/i, icon: 'el-icon-s-shop' },
  { match: /交易|运费|发货|发票|支付/i, icon: 'el-icon-s-finance' }
]
const FALLBACK_ICONS = ['el-icon-menu', 'el-icon-s-grid', 'el-icon-s-data', 'el-icon-collection']

export default {
  name: 'Layout',
  data() {
    return {
      userName: '管理员',
      // 当前展开的业务域路径（手风琴：同时只展开一个，避免 27 个页面把侧栏撑爆）
      openPath: ''
    }
  },
  computed: {
    menus() {
      // 顶层「/」是 Layout 壳（包首页，侧栏底部单独入口），不重复展示；
      // /login 是独立页；/merchant/** 是商家独立后台（有自己的布局与令牌），
      // 都不能混进平台后台菜单——否则会出现「商家登录 / 商家端」这类假菜单项。
      // 平台只有一个系统时下钻一层，把 8 个业务域提升为一级菜单。
      const tree = buildMenu(this.$router.options.routes, '')
        .filter(m => m.path !== '/'
          && m.path !== '/home'
          && m.path !== '/login'
          && m.path.indexOf('/merchant') !== 0)
      if (tree.length === 1 && tree[0].children.length) {
        return tree[0].children
      }
      return tree
    },
    currentGroup() {
      const p = this.$route.path
      return this.menus.find(g => this.isGrpActive(g)) || null
    },
    currentPages() {
      return this.currentGroup ? this.flatPages(this.currentGroup) : []
    },
    crumbs() {
      const list = this.$route.matched
        .map(item => (item.meta && item.meta.title) || '')
        .filter(Boolean)
      return list.length ? list : ['首页']
    }
  },
  watch: {
    '$route.path': {
      immediate: true,
      handler() {
        // 路由落到哪个业务域，就展开哪个（刷新/直接输地址也能定位）
        const grp = this.menus.find(g => this.isGrpActive(g))
        if (grp) this.openPath = grp.path
      }
    }
  },
  methods: {
    iconOf(path) {
      const grp = this.menus.find(g => g.path === path)
      const name = grp ? grp.title : ''
      const hit = GROUP_ICONS.find(g => g.match.test(name))
      if (hit) return hit.icon
      return FALLBACK_ICONS[this.menus.findIndex(g => g.path === path) % FALLBACK_ICONS.length]
    },
    // 分组下可能还有中间层（GroupView 壳），拍平到真实页面
    flatPages(grp) {
      const out = []
      const walk = nodes => {
        ;(nodes || []).forEach(n => {
          if (n.children && n.children.length) walk(n.children)
          else out.push(n)
        })
      }
      walk(grp.children)
      return out
    },
    countPages(grp) {
      return this.flatPages(grp).length
    },
    isGrpActive(grp) {
      const p = this.$route.path
      return p === grp.path || p.indexOf(grp.path + '/') === 0
    },
    // 一级点击：展开该业务域的下拉；当前域再次点击则收起
    toggleGroup(grp) {
      const pages = this.flatPages(grp)
      if (this.openPath === grp.path) {
        this.openPath = ''
        return
      }
      this.openPath = grp.path
      // 切到别的业务域时直接落到它的第一个页面，避免「菜单变了内容没变」
      if (pages.length && !this.isGrpActive(grp)) this.go(pages[0].path)
    },
    isOpen(grp) {
      return this.openPath === grp.path
    },
    onUserCmd(cmd) {
      if (cmd === 'logout') this.logout()
    },
    async logout() {
      try {
        await request({ url: '/auth/logout', method: 'post' })
      } catch (e) { /* 本地清理优先，后端登出失败不阻塞 */ }
      localStorage.removeItem('token')
      this.$router.push('/login')
    },
    go(path) {
      if (!path || this.$route.path === path) return
      this.$router.push(path)
    }
  }
}
</script>

<style>
:root {
  --cg-sidebar-bg: linear-gradient(180deg, #1c2340 0%, #151b31 100%);
  --cg-accent: #4d7cfe;
  --cg-accent-soft: rgba(77, 124, 254, 0.16);
}

.layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: #f3f5fa;
}

/* ---------- 一级菜单（左侧） ---------- */
.sidebar {
  flex: 0 0 224px;
  width: 224px;
  display: flex;
  flex-direction: column;
  background: var(--cg-sidebar-bg);
  box-shadow: 4px 0 18px rgba(15, 21, 40, 0.18);
  z-index: 20;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 64px;
  padding: 0 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
  cursor: pointer;
}
.brand-mark {
  width: 36px;
  height: 36px;
  line-height: 36px;
  text-align: center;
  border-radius: 10px;
  background: linear-gradient(135deg, #4d7cfe 0%, #7c5bf0 100%);
  color: #fff;
  font-weight: 700;
  font-size: 16px;
  box-shadow: 0 4px 14px rgba(77, 124, 254, 0.4);
  flex: 0 0 36px;
}
.brand-name {
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1px;
}
.brand-sub {
  color: rgba(199, 210, 228, 0.45);
  font-size: 10px;
  letter-spacing: 0.5px;
}

.primary-menu {
  flex: 1;
  overflow-y: auto;
  padding: 10px 10px 16px;
}
.primary-menu::-webkit-scrollbar { width: 4px; }
.primary-menu::-webkit-scrollbar-thumb { background: rgba(255, 255, 255, 0.14); border-radius: 2px; }

.primary-item {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 44px;
  padding: 0 12px;
  margin-bottom: 3px;
  border-radius: 10px;
  color: #a9b4cd;
  font-size: 13.5px;
  cursor: pointer;
  transition: background 0.16s ease, color 0.16s ease;
}
.primary-icon { font-size: 16px; width: 18px; text-align: center; }
.primary-title { flex: 1; min-width: 0; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.primary-count {
  font-size: 10px;
  padding: 0 6px;
  height: 16px;
  line-height: 16px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.09);
  color: rgba(199, 210, 228, 0.7);
}
.primary-item:hover { background: rgba(255, 255, 255, 0.07); color: #e6ecf8; }
.primary-item.is-active {
  background: var(--cg-accent-soft);
  color: #fff;
  font-weight: 500;
  box-shadow: inset 3px 0 0 #7c9bff;
}
.primary-item.is-active .primary-icon { color: #7c9bff; }

.sidebar-foot {
  flex: 0 0 auto;
  border-top: 1px solid rgba(255, 255, 255, 0.07);
  padding: 8px 10px 12px;
}
.foot-item {
  display: flex;
  align-items: center;
  gap: 9px;
  height: 38px;
  padding: 0 12px;
  border-radius: 10px;
  color: #a9b4cd;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.16s ease, color 0.16s ease;
}
.foot-item:hover { background: rgba(255, 255, 255, 0.07); color: #e6ecf8; }
.foot-item.is-active { background: var(--cg-accent-soft); color: #fff; }

/* ---------- 主区域 ---------- */
.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.header {
  flex: 0 0 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 22px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.06);
  z-index: 10;
}
.header-left { display: flex; align-items: center; gap: 14px; }
.crumbs { font-size: 13px; }
.header-right { display: flex; align-items: center; gap: 16px; }
.env-tag {
  padding: 2px 9px;
  border-radius: 10px;
  background: #eef2ff;
  color: #4d7cfe;
  font-size: 11px;
  font-weight: 600;
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px 4px 4px;
  border-radius: 20px;
  cursor: pointer;
  transition: background 0.16s ease;
}
.user-chip:hover { background: #f2f5fb; }
.avatar {
  width: 30px;
  height: 30px;
  line-height: 30px;
  text-align: center;
  border-radius: 50%;
  background: linear-gradient(135deg, #4d7cfe 0%, #7c5bf0 100%);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}
.user-name { font-size: 13px; color: #3a4358; }
.user-chip .el-icon-arrow-down { font-size: 11px; color: #97a1b5; }

/* ---------- 二级下拉（业务域内展开，不再平铺 27 项） ---------- */
.primary-block { margin-bottom: 2px; }
.primary-arrow {
  font-size: 11px;
  color: rgba(199, 210, 228, 0.5);
  transition: transform 0.2s ease;
}
.primary-item.is-open .primary-arrow { transform: rotate(180deg); color: #7c9bff; }

.sub-list {
  padding: 2px 0 6px;
  overflow: hidden;
}
.sub-item {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 12px 0 34px;
  margin: 1px 0;
  border-radius: 8px;
  color: #97a3bb;
  font-size: 12.5px;
  cursor: pointer;
  transition: background 0.16s ease, color 0.16s ease;
}
.sub-item:hover { background: rgba(255, 255, 255, 0.06); color: #e6ecf8; }
.sub-item.is-active {
  background: rgba(77, 124, 254, 0.2);
  color: #fff;
  font-weight: 500;
}
.sub-dot {
  flex: 0 0 4px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.5;
}
.sub-item.is-active .sub-dot { opacity: 1; background: #7c9bff; }
.sub-title { flex: 1; min-width: 0; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }

.content {
  flex: 1;
  padding: 20px 22px 28px;
  overflow-y: auto;
  background: #f3f5fa;
}
</style>
