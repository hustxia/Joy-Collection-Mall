<template>
  <div class="page">
    <van-nav-bar title="我的评价" left-arrow fixed placeholder @click-left="router.back()" />

    <van-empty v-if="!loading && !list.length" description="还没有发表过评价" />

    <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="onLoad">
      <div v-for="it in list" :key="it.id" class="rv-item" @click="goGoods(it.goodsId)">
        <div class="rv-top">
          <van-rate :model-value="it.rating" readonly size="12" color="#ffb400" />
          <span class="rv-time">{{ it.createTime }}</span>
        </div>
        <div class="rv-content">{{ it.content || '此用户没有填写评价' }}</div>
        <div v-if="imgList(it.images).length" class="rv-imgs">
          <van-image
            v-for="(img, i) in imgList(it.images)"
            :key="i"
            :src="thumb(img, 120)"
            fit="cover"
            class="rv-img"
            radius="4"
            @click.stop="preview(imgList(it.images), i)"
          />
        </div>
        <div v-if="it.reply" class="rv-reply">商家回复：{{ it.reply }}</div>
        <!-- V15 追评展示 -->
        <div v-if="it.appendContent" class="rv-append">
          <span class="rv-append-badge">追评</span>
          <span class="rv-append-text">{{ it.appendContent }}</span>
          <span v-if="it.appendTime" class="rv-append-time">{{ it.appendTime }}</span>
        </div>
        <!-- V15 追评入口：未追评且原评后 30 天内 -->
        <div v-if="!it.appendContent && canAppend(it)" class="rv-actions">
          <van-button size="mini" plain type="primary" @click.stop="openAppend(it)">追评</van-button>
        </div>
        <div v-if="it.status === 1" class="rv-hidden">该评价已被平台隐藏</div>
      </div>
    </van-list>

    <!-- V15 追评弹层 -->
    <van-popup v-model:show="appendShow" position="bottom" round>
      <div class="append-sheet">
        <div class="append-title">追评（原评价后 30 天内仅一次）</div>
        <van-field
          v-model="appendText"
          type="textarea"
          rows="3"
          maxlength="500"
          show-word-limit
          placeholder="使用一段时间后来补充使用体验吧"
        />
        <van-button block round color="#4a6cf7" :loading="appending" @click="submitAppend">提交追评</van-button>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showImagePreview, showSuccessToast, showToast } from 'vant'
import { appendReview, getMyReviews } from '@/api/review'
import { thumb } from '@/utils/format'

const router = useRouter()
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)
const finished = ref(false)

// V15 追评
const appendShow = ref(false)
const appendText = ref('')
const appending = ref(false)
const appendTarget = ref(null)

const imgList = images => (images ? String(images).split(',').filter(Boolean) : [])
const preview = (images, i) => showImagePreview({ images, startPosition: i })

const canAppend = it => {
  if (!it.createTime) return false
  const created = new Date(String(it.createTime).replace(/-/g, '/')).getTime()
  return Number.isFinite(created) && Date.now() - created <= 30 * 24 * 3600 * 1000
}

const openAppend = it => {
  appendTarget.value = it
  appendText.value = ''
  appendShow.value = true
}

const submitAppend = async () => {
  if (!appendText.value.trim()) {
    showToast('请输入追评内容')
    return
  }
  appending.value = true
  try {
    await appendReview({ reviewId: appendTarget.value.id, content: appendText.value.trim() })
    appendShow.value = false
    showSuccessToast('追评成功')
    // 局部更新，免重拉列表
    appendTarget.value.appendContent = appendText.value.trim()
    appendTarget.value.appendTime = new Date().toISOString().slice(0, 19).replace('T', ' ')
  } catch (e) {
    /* 拦截器已提示（如已追评过/已过追评期） */
  } finally {
    appending.value = false
  }
}

const onLoad = async () => {
  try {
    const data = await getMyReviews({ pageNum: page.value, pageSize: size })
    const rows = data.records || []
    total.value = data.total || 0
    list.value = page.value === 1 ? rows : list.value.concat(rows)
    page.value += 1
    finished.value = list.value.length >= total.value
  } catch (e) {
    finished.value = true
  } finally {
    loading.value = false
  }
}

const goGoods = goodsId => goodsId && router.push(`/goods/${goodsId}`)
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f7f8fa;
}
.rv-item {
  background: #fff;
  padding: 12px 14px;
  border-bottom: 1px solid #f2f3f5;
}
.rv-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.rv-time {
  font-size: 11px;
  color: #969799;
}
.rv-content {
  margin-top: 8px;
  font-size: 13px;
  color: #323233;
  line-height: 1.5;
}
.rv-imgs {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.rv-img {
  width: 72px;
  height: 72px;
}
.rv-reply {
  margin-top: 8px;
  background: #f7f8fa;
  border-radius: 4px;
  padding: 8px 10px;
  font-size: 12px;
  color: #646566;
}
.rv-hidden {
  margin-top: 8px;
  font-size: 11px;
  color: #e1251b;
}

/* V15：追评 */
.rv-append {
  margin-top: 8px;
  padding: 8px 10px;
  background: #fff7ee;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
}
.rv-append-badge {
  display: inline-block;
  margin-right: 6px;
  padding: 0 6px;
  border-radius: 3px;
  background: #ff976a;
  color: #fff;
  font-size: 10px;
  line-height: 18px;
}
.rv-append-text {
  color: #b88230;
}
.rv-append-time {
  margin-left: 8px;
  color: #c8c9cc;
  font-size: 11px;
}
.rv-actions {
  margin-top: 8px;
  text-align: right;
}
.append-sheet {
  padding: 16px;
}
.append-title {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: #323233;
}
</style>
