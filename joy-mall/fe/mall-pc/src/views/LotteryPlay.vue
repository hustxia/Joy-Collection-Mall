<template>
  <div class="page-wrap">
    <div class="page-card" v-loading="loading">
      <div v-if="activity" class="lt-play">
        <div class="lt-head">
          <div>
            <h2 class="lt-title">{{ activity.name }}</h2>
            <div class="lt-meta">
              <span>每日限 {{ activity.dailyLimit }} 次</span>
              <span v-if="activity.consumePoints > 0">每次消耗 {{ activity.consumePoints }} 积分</span>
              <span v-else>免费参与</span>
              <span>距结束 <b class="lt-cd">{{ countdownText }}</b></span>
            </div>
          </div>
          <div class="lt-remain">
            今日剩余 <em>{{ remain }}</em> 次
          </div>
        </div>

        <!-- 九宫格抽奖盘：指针固定在上，转动时高亮轮播 -->
        <div class="lt-board" :class="{ spinning }">
          <div
            v-for="(cell, i) in cells"
            :key="i"
            class="lt-cell"
            :class="{ active: spinning && highlight === i, prize: cell.prize }"
          >
            <div class="lt-cell-name">{{ cell.name }}</div>
            <div class="lt-cell-type">{{ cell.type }}</div>
          </div>
          <div class="lt-go" @click="draw">
            <span>{{ spinning ? '抽奖中' : '抽奖' }}</span>
          </div>
        </div>

        <div class="lt-addr">
          <span class="lt-addr-label">实物收货地址（抽中实物时必填）：</span>
          <el-select v-model="addressId" size="small" placeholder="选择收货地址" style="width:260px">
            <el-option v-for="a in addresses" :key="a.id" :label="fullAddress(a)" :value="a.id" />
          </el-select>
        </div>

        <!-- 我的中奖记录 -->
        <div class="lt-records">
          <div class="lt-records-title">我的抽奖记录</div>
          <el-table :data="records" size="small" v-if="records.length">
            <el-table-column prop="createTime" label="时间" width="160">
              <template #default="{ row }">{{ shortTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column prop="activityName" label="活动" min-width="140" />
            <el-table-column label="结果" min-width="120">
              <template #default="{ row }">
                <el-tag size="mini" :type="row.isWin === 1 ? 'danger' : 'info'">
                  {{ row.isWin === 1 ? row.prizeName : '谢谢参与' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="还没有抽奖记录" :image-size="80" />
        </div>
      </div>
      <el-empty v-else-if="loaded" description="活动不存在或已结束" />
    </div>

    <!-- 抽奖结果弹窗 -->
    <el-dialog
      v-model="resultVisible"
      width="380px"
      center
      :show-close="false"
      :close-on-click-modal="false"
    >
      <div class="lt-result" :class="{ win: result && result.isWin === 1 }">
        <i :class="result && result.isWin === 1 ? 'el-icon-trophy' : 'el-icon-sad'" />
        <div class="lt-result-title">{{ result && result.isWin === 1 ? '恭喜中奖！' : '很遗憾' }}</div>
        <div class="lt-result-prize">{{ result ? result.prizeName : '' }}</div>
      </div>
      <template #footer><div>
        <el-button size="small" @click="resultVisible = false">关闭</el-button>
        <el-button v-if="result && result.isWin === 1 && result.orderId" type="danger" size="small"
          @click="$router.push(`/order/detail/${result.orderId}`)">查看奖品订单</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import { getLotteryActivities, lotteryDraw, getLotteryMine, getLotteryRemain } from '@/api/marketing'
import { getAddressList } from '@/api/address'
import { shortTime } from '@/utils/format'
import countdown from '@/utils/countdown'

// 奖品类型：0积分 1优惠券 2实物 3谢谢参与
const PRIZE_TYPE = { 0: '积分', 1: '优惠券', 2: '实物', 3: '谢谢参与' }

export default {
  name: 'LotteryPlay',
  mixins: [countdown],
  data() {
    return {
      activity: null,
      loading: false,
      loaded: false,
      remain: 0,
      spinning: false,
      highlight: -1,
      result: null,
      resultVisible: false,
      records: [],
      addresses: [],
      addressId: null
    }
  },
  computed: {
    // 九宫格：8 个奖品位 + 中心抽奖键；奖品不足 8 个时补「谢谢参与」占位
    cells() {
      const prizes = (this.activity && this.activity.prizes) || []
      const cells = prizes.slice(0, 8).map(p => ({
        name: p.name,
        type: this.prizeTypeText(p.prizeType),
        prize: p.prizeType !== 3
      }))
      while (cells.length < 8) {
        cells.push({ name: '谢谢参与', type: '谢谢参与', prize: false })
      }
      // 中心键占位（index 4）
      cells.splice(4, 0, null)
      return cells
    }
  },
  created() {
    this.load()
  },
  methods: {
    shortTime,
    prizeTypeText(t) {
      return PRIZE_TYPE[t] || '奖品'
    },
    fullAddress(a) {
      return [a.province, a.city, a.district, a.detail].filter(Boolean).join('')
    },
    async load() {
      this.loading = true
      try {
        const list = (await getLotteryActivities()) || []
        this.activity = list.find(a => String(a.id) === String(this.$route.params.id)) || null
        if (this.activity) {
          this.refreshRemain()
          this.loadRecords()
          this.loadAddresses()
        }
      } catch (e) {
        this.activity = null
      } finally {
        this.loading = false
        this.loaded = true
      }
    },
    async refreshRemain() {
      try {
        const data = await getLotteryRemain(this.$route.params.id)
        this.remain = (data && data.remain) || 0
      } catch (e) {
        this.remain = 0
      }
    },
    async loadRecords() {
      try {
        this.records = ((await getLotteryMine()) || []).filter(r => String(r.activityId) === String(this.$route.params.id))
      } catch (e) {
        this.records = []
      }
    },
    async loadAddresses() {
      try {
        this.addresses = (await getAddressList()) || []
        const def = this.addresses.find(a => Number(a.isDefault) === 1)
        if (def) this.addressId = def.id
      } catch (e) {
        this.addresses = []
      }
    },
    // 九宫格奖品位下标（跳过中心 4）
    cellIndexes() {
      return [0, 1, 2, 3, 5, 6, 7, 8]
    },
    async draw() {
      if (this.spinning) return
      if (!this.activity) return
      this.spinning = true
      const indexes = this.cellIndexes()
      let step = 0
      const roll = setInterval(() => {
        this.highlight = indexes[step % indexes.length]
        step++
      }, 120)
      try {
        const data = await lotteryDraw(this.$route.params.id, {
          addressId: this.addressId || undefined
        })
        // 起码转 2.4s 再出结果
        await new Promise(resolve => setTimeout(resolve, 2400))
        clearInterval(roll)
        this.spinning = false
        this.highlight = -1
        this.result = data
        this.resultVisible = true
        this.remain = data.remain
        this.loadRecords()
      } catch (e) {
        clearInterval(roll)
        this.spinning = false
        this.highlight = -1
        this.refreshRemain()
      }
    }
  }
}
</script>

<style scoped>
.lt-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.lt-title {
  margin: 0;
  font-size: 20px;
  color: #303133;
}
.lt-meta {
  margin-top: 10px;
  font-size: 13px;
  color: #909399;
  display: flex;
  gap: 16px;
}
.lt-cd {
  color: #e1251b;
  font-family: monospace;
}
.lt-remain {
  font-size: 14px;
  color: #606266;
  background: #fff7f6;
  border-radius: 8px;
  padding: 10px 16px;
}
.lt-remain em {
  font-style: normal;
  color: #e1251b;
  font-size: 20px;
  font-weight: 700;
}
.lt-board {
  margin: 28px auto 0;
  width: 480px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
.lt-cell {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 14px 8px;
  text-align: center;
  background: #fffdf9;
}
.lt-cell.prize {
  background: #fff7f6;
}
.lt-cell.active {
  border-color: #e1251b;
  background: #ffeceb;
}
.lt-cell-name {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lt-cell-type {
  margin-top: 6px;
  font-size: 11px;
  color: #909399;
}
.lt-go {
  border-radius: 8px;
  background: #e1251b;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-weight: 700;
  font-size: 16px;
}
.lt-go:hover {
  background: #c81e15;
}
.lt-board.spinning .lt-go {
  cursor: not-allowed;
  opacity: 0.8;
}
.lt-addr {
  margin-top: 20px;
  text-align: center;
  font-size: 13px;
  color: #606266;
}
.lt-records {
  margin-top: 32px;
  border-top: 1px solid #f0f0f0;
  padding-top: 20px;
}
.lt-records-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}
.lt-result {
  text-align: center;
}
.lt-result i {
  font-size: 52px;
  color: #909399;
}
.lt-result.win i {
  color: #e1251b;
}
.lt-result-title {
  margin-top: 10px;
  font-size: 16px;
  color: #303133;
  font-weight: 600;
}
.lt-result-prize {
  margin-top: 8px;
  font-size: 18px;
  color: #e1251b;
  font-weight: 700;
}
</style>
