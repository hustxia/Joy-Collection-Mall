<template>
  <div class="app-container">
    <!-- 页头 -->
    <div class="page-head">
      <div class="page-head__main">
        <h2 class="page-title">评价管理</h2>
        <p class="page-desc">查看商品评价、回复用户评价、隐藏违规评价（隐藏后 C 端列表不可见）</p>
      </div>
      <div class="page-head__meta">
        <span class="meta-pill">{{ total }} 条记录</span>
      </div>
    </div>

    <!-- 筛选 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" size="small" @submit.prevent>
        <el-form-item label="商品ID">
          <el-input v-model.trim="query.goodsId" placeholder="商品ID" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="评分">
          <el-select v-model="query.rating" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="r in [5, 4, 3, 2, 1]" :key="r" :label="r + ' 星'" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="正常" :value="0" />
            <el-option label="已隐藏" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="query.tag" placeholder="全部" clearable filterable allow-create style="width: 130px">
            <el-option v-for="t in tagOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleSearch">查询</el-button>
          <el-button icon="el-icon-refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <template #header><div class="card-header">
        <div class="card-title">
          <span class="title">商品评价</span>
          <span class="subtitle">共 {{ total }} 条</span>
        </div>
      </div></template>

      <el-table
        v-loading="loading"
        :data="list"
        class="data-table"
        stripe
        header-cell-class-name="table-head-cell"
        empty-text="暂无数据"
      >
        <el-table-column prop="id" label="ID" width="170" />
        <el-table-column prop="goodsId" label="商品ID" width="150" show-overflow-tooltip />
        <el-table-column prop="memberNickname" label="会员" min-width="120" show-overflow-tooltip />
        <el-table-column label="评分" width="150" align="center">
          <template #default="{ row }">
            <el-rate :value="row.rating" disabled />
          </template>
        </el-table-column>
        <el-table-column label="评价内容" min-width="220">
          <template #default="{ row }">
            <div class="rv-content">{{ row.content || '（无文字）' }}</div>
            <div v-if="row.appendContent" class="rv-append">
              <el-tag type="warning" size="mini" class="rv-append-tag">追评</el-tag>
              <span>{{ row.appendContent }}</span>
              <span v-if="row.appendTime" class="rv-append-time">{{ row.appendTime }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="标签" min-width="120">
          <template #default="{ row }">
            <template v-if="tagList(row.tags).length">
              <el-tag v-for="t in tagList(row.tags)" :key="t" size="mini" type="success" class="rv-tag">{{ t }}</el-tag>
            </template>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="图片" width="90" align="center">
          <template #default="{ row }">
            <el-image
              v-if="firstImage(row.images)"
              :src="thumb(firstImage(row.images), 80)"
              :preview-src-list="imageList(row.images).map(i => thumb(i, 400))"
              fit="cover"
              class="rv-thumb"
            />
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="usefulCount" label="有用数" width="80" align="center" />
        <el-table-column prop="reply" label="商家回复" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'info' : 'success'" size="mini">
              {{ row.status === 1 ? '已隐藏' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="评价时间" width="170" align="center" />
        <el-table-column label="操作" width="170" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-hasPermi="['mall_mall_review:reply']" type="text" @click="openReply(row)">回复</el-button>
            <el-button
              v-hasPermi="['mall_mall_review:hide']"
              type="text"
              :class="{ 'danger-btn': row.status === 0 }"
              @click="onToggleHide(row)"
            >{{ row.status === 1 ? '恢复' : '隐藏' }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :page-size="query.size"
        v-model:current-page="query.page"
        @size-change="handleSizeChange"
        @current-change="fetchList"
      />
    </el-card>

    <!-- 回复弹窗 -->
    <el-dialog title="回复评价" v-model="replyVisible" width="520px">
      <div class="reply-quote">
        <div class="reply-quote-title">{{ current.memberNickname || '匿名用户' }} 的评价</div>
        <div class="reply-quote-content">{{ current.content || '（无评价内容）' }}</div>
      </div>
      <el-input
        v-model.trim="replyText"
        type="textarea"
        :rows="4"
        maxlength="500"
        show-word-limit
        placeholder="请输入回复内容（必填）"
      />
      <template #footer><div>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReply">提交回复</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import { getMallReviewPage, replyMallReview, hideMallReview } from '@/api/mall_review'

export default {
  name: 'MallReview',
  data() {
    return {
      loading: false,
      list: [],
      total: 0,
      // V15：评价标签固定集合（与后端 tags 逗号分隔存储约定一致），支持自定义输入筛选
      tagOptions: ['物流快', '包装好', '质量好', '服务好', '性价比高', '外观漂亮'],
      query: { page: 1, size: 10, goodsId: '', rating: undefined, status: undefined, tag: '' },
      replyVisible: false,
      current: {},
      replyText: '',
      submitting: false
    }
  },
  created() {
    this.fetchList()
  },
  methods: {
    tagList(tags) {
      return tags ? String(tags).split(',').filter(Boolean) : []
    },
    imageList(images) {
      return images ? String(images).split(',').filter(Boolean) : []
    },
    firstImage(images) {
      return this.imageList(images)[0] || ''
    },
    async fetchList() {
      this.loading = true
      try {
        // 空字符串不带上，避免把 goodsId 传成 '' 导致后端 Long 转换失败
        const params = { ...this.query }
        if (!params.goodsId) delete params.goodsId
        if (!params.tag) delete params.tag
        const res = await getMallReviewPage(params)
        this.list = res.data.list || []
        this.total = res.data.total || 0
      } catch (e) {
        this.list = []
        this.total = 0
      } finally {
        this.loading = false
      }
    },
    handleSearch() {
      this.query.page = 1
      this.fetchList()
    },
    handleReset() {
      this.query = { page: 1, size: this.query.size, goodsId: '', rating: undefined, status: undefined, tag: '' }
      this.fetchList()
    },
    handleSizeChange(size) {
      this.query.size = size
      this.query.page = 1
      this.fetchList()
    },
    openReply(row) {
      this.current = row || {}
      this.replyText = row.reply || ''
      this.replyVisible = true
    },
    async submitReply() {
      if (!this.replyText) {
        this.$message.warning('请输入回复内容')
        return
      }
      this.submitting = true
      try {
        await replyMallReview(this.current.id, this.replyText)
        this.$message.success('回复成功')
        this.replyVisible = false
        this.fetchList()
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.submitting = false
      }
    },
    onToggleHide(row) {
      const next = row.status === 1 ? 0 : 1
      const action = next === 1 ? '隐藏' : '恢复'
      this.$confirm(`确定${action}该评价吗？${next === 1 ? '隐藏后 C 端不再展示。' : ''}`, '提示', { type: 'warning' })
        .then(async () => {
          await hideMallReview(row.id, next)
          this.$message.success(`${action}成功`)
          this.fetchList()
        })
        .catch(() => {})
    }
  }
}
</script>

<style scoped>
.app-container {
  padding: 0;
  background: transparent;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #111827;
  letter-spacing: 0.2px;
}

.page-desc {
  margin: 6px 0 0;
  font-size: 12px;
  color: #8a94a6;
}

.page-head__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 4px;
}

.meta-pill {
  padding: 4px 12px;
  border-radius: 20px;
  background: #eef2ff;
  color: #4d7cfe;
  font-size: 12px;
  font-weight: 500;
}

.filter-card,
.table-card {
  border: none;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04), 0 6px 20px rgba(16, 24, 40, 0.05);
}

.filter-card {
  margin-bottom: 16px;
}

.filter-card :deep(.el-card__body){
  padding: 18px 18px 4px;
}

.filter-card :deep(.el-form-item){
  margin-right: 16px;
  margin-bottom: 14px;
}

.filter-card :deep(.el-form-item__label){
  color: #5a6478;
  font-size: 13px;
}

.table-card :deep(.el-card__header){
  padding: 16px 18px;
  border-bottom: 1px solid #eef1f7;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  display: flex;
  align-items: baseline;
}

.card-title .title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.card-title .subtitle {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}

.data-table :deep(.table-head-cell){
  background: #f7f9fc !important;
  color: #5c6270;
  font-weight: 600;
}

.data-table :deep(td){
  padding: 11px 0;
}

.data-table :deep(.el-table__row:hover > td){
  background: #f5f9ff;
}

.data-table :deep(.el-button--text){
  padding: 4px 6px;
}

.pagination {
  margin-top: 18px;
  text-align: right;
}

.danger-btn {
  color: #f56c6c;
}

.rv-thumb {
  width: 40px;
  height: 40px;
  border-radius: 4px;
}

.rv-content {
  line-height: 1.5;
}

.rv-append {
  margin-top: 6px;
  padding: 6px 8px;
  background: #fdf6ec;
  border-radius: 4px;
  font-size: 12px;
  color: #b88230;
  line-height: 1.5;
}

.rv-append-tag {
  margin-right: 6px;
}

.rv-append-time {
  margin-left: 8px;
  color: #c0c4cc;
}

.rv-tag {
  margin: 2px 4px 2px 0;
}

.reply-quote {
  margin-bottom: 12px;
  padding: 10px 12px;
  background: #f7f8fa;
  border-radius: 6px;
}

.reply-quote-title {
  font-size: 12px;
  color: #909399;
}

.reply-quote-content {
  margin-top: 6px;
  font-size: 13px;
  color: #303133;
  line-height: 1.6;
}
</style>
