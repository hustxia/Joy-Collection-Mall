<template>
  <div class="app-container">
    <!-- <gen:begin key="vue-template"> -->
    <!-- 页头：一眼看清「我在哪个页面、这页能干什么」 -->
    <div class="page-head">
      <div class="page-head__main">
        <h2 class="page-title">订单</h2>
        <p class="page-desc">维护订单数据，可按条件筛选、编辑与导出</p>
      </div>
      <div class="page-head__meta">
        <span class="meta-pill">{{ total }} 条记录</span>
      </div>
    </div>
    <!-- 列表 -->
    <el-card shadow="never" class="table-card">
      <div slot="header" class="card-header">
        <div class="card-title">
          <span class="title">订单</span>
          <span class="subtitle">共 {{ total }} 条</span>
        </div>
        <div class="card-actions">
          <el-button v-hasPermi="['mall_mall_order:add']" type="primary" icon="el-icon-plus" size="small" @click="openDialog()">新增订单</el-button>
          <el-button icon="el-icon-download" size="small" @click="handleExport">导出</el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="list"
        class="data-table"
        stripe
        header-cell-class-name="table-head-cell"
        empty-text="暂无数据"
      >
        <el-table-column prop="id" label="ID" width="180" />
        <el-table-column prop="orderNo" label="订单号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="memberId" label="会员ID 关联mall_member" width="260" align="right" show-overflow-tooltip />
        <el-table-column prop="totalAmount" label="商品总额" width="138" align="right" show-overflow-tooltip />
        <el-table-column prop="freightAmount" label="运费" width="110" align="right" show-overflow-tooltip />
        <el-table-column prop="discountAmount" label="优惠金额" width="138" align="right" show-overflow-tooltip />
        <el-table-column prop="payAmount" label="实付金额" width="138" align="right" show-overflow-tooltip />
        <el-table-column prop="status" label="订单状态" min-width="260" show-overflow-tooltip
          :formatter="(row) => enumText('status', row.status)" />
        <el-table-column prop="payStatus" label="支付状态" min-width="222" show-overflow-tooltip
          :formatter="(row) => enumText('payStatus', row.payStatus)" />
        <el-table-column prop="payType" label="支付方式" min-width="208" show-overflow-tooltip
          :formatter="(row) => enumText('payType', row.payType)" />
        <el-table-column prop="payOrderNo" label="支付单号 关联pay_order" min-width="260" show-overflow-tooltip />
        <el-table-column prop="payTime" label="支付时间" width="170" align="center" show-overflow-tooltip />
        <el-table-column prop="receiverName" label="收货人" min-width="140" show-overflow-tooltip />
        <el-table-column prop="receiverPhone" label="联系电话" min-width="140" show-overflow-tooltip />
        <el-table-column prop="receiverAddress" label="收货地址" min-width="180" show-overflow-tooltip />
        <el-table-column prop="expressCompany" label="快递公司" min-width="140" show-overflow-tooltip />
        <el-table-column prop="expressNo" label="快递单号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="deliverTime" label="发货时间" width="170" align="center" show-overflow-tooltip />
        <el-table-column prop="finishTime" label="完成时间" width="170" align="center" show-overflow-tooltip />
        <el-table-column prop="buyerRemark" label="买家留言" min-width="140" show-overflow-tooltip />
        <el-table-column prop="remark" label="管理员备注" min-width="140" show-overflow-tooltip />
        <el-table-column prop="updateTime" label="更新时间" width="170" align="center" />
        <el-table-column label="操作" width="216" fixed="right" align="center">
          <template slot-scope="{ row }">
            <el-button type="text" @click="openDetail(row)">详情</el-button>
            <el-button v-hasPermi="['mall_mall_order:edit']" type="text" @click="openDialog(row)">编辑</el-button>
            <el-button v-hasPermi="['mall_mall_order:remove']" type="text" class="danger-btn" @click="handleDelete(row)">删除</el-button>
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
        :current-page.sync="query.page"
        @size-change="handleSizeChange"
        @current-change="fetchList"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="form.id ? '编辑订单' : '新增订单'" :visible.sync="dialogVisible" width="900px" top="6vh" class="form-dialog">
      <el-form ref="form" :model="form" :rules="rules" label-width="96px">
        <el-row :gutter="20">
          <el-col :span="12">
        <el-form-item label="订单号" prop="orderNo">
          <el-input v-model="form.orderNo" placeholder="请输入订单号" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="会员ID 关联mall_member" prop="memberId">
          <el-input-number v-model="form.memberId" controls-position="right" style="width: 100%" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="商品总额" prop="totalAmount">
          <el-input-number v-model="form.totalAmount" controls-position="right" style="width: 100%" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="运费" prop="freightAmount">
          <el-input-number v-model="form.freightAmount" controls-position="right" style="width: 100%" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="优惠金额" prop="discountAmount">
          <el-input-number v-model="form.discountAmount" controls-position="right" style="width: 100%" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="实付金额" prop="payAmount">
          <el-input-number v-model="form.payAmount" controls-position="right" style="width: 100%" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="订单状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择订单状态" clearable style="width: 100%">
            <el-option v-for="d in enumMap.status || []" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="支付状态" prop="payStatus">
          <el-select v-model="form.payStatus" placeholder="请选择支付状态" clearable style="width: 100%">
            <el-option v-for="d in enumMap.payStatus || []" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="支付方式" prop="payType">
          <el-select v-model="form.payType" placeholder="请选择支付方式" clearable style="width: 100%">
            <el-option v-for="d in enumMap.payType || []" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="支付单号 关联pay_order" prop="payOrderNo">
          <el-input v-model="form.payOrderNo" placeholder="请输入支付单号 关联pay_order" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="支付时间" prop="payTime">
          <el-date-picker v-model="form.payTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="请选择支付时间" style="width: 100%" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="收货人" prop="receiverName">
          <el-input v-model="form.receiverName" placeholder="请输入收货人" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="联系电话" prop="receiverPhone">
          <el-input v-model="form.receiverPhone" placeholder="请输入联系电话" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="收货地址" prop="receiverAddress">
          <el-input v-model="form.receiverAddress" placeholder="请输入收货地址" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="快递公司" prop="expressCompany">
          <el-input v-model="form.expressCompany" placeholder="请输入快递公司" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="快递单号" prop="expressNo">
          <el-input v-model="form.expressNo" placeholder="请输入快递单号" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="发货时间" prop="deliverTime">
          <el-date-picker v-model="form.deliverTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="请选择发货时间" style="width: 100%" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="完成时间" prop="finishTime">
          <el-date-picker v-model="form.finishTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" placeholder="请选择完成时间" style="width: 100%" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="买家留言" prop="buyerRemark">
          <el-input v-model="form.buyerRemark" placeholder="请输入买家留言" />
        </el-form-item>
          </el-col>
          <el-col :span="12">
        <el-form-item label="管理员备注" prop="remark">
          <el-input v-model="form.remark" placeholder="请输入管理员备注" />
        </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-tabs v-model="activeChildTab" type="card" class="child-tabs">
        <el-tab-pane label="订单明细" name="mallOrderItem">
          <div class="child-bar">
            <el-button type="primary" icon="el-icon-plus" size="mini" @click="addChildRow('mallOrderItem')">添加明细</el-button>
            <span class="child-tip">主表与子表在同一事务中保存（先清后插）</span>
          </div>
          <el-table :data="mallOrderItemRows" border size="mini" class="child-table">
            <el-table-column label="商品" min-width="130">
              <template slot-scope="{ row }">
                <el-input-number v-model="row.goodsId" controls-position="right" size="mini" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="SKU ID" min-width="130">
              <template slot-scope="{ row }">
                <el-input-number v-model="row.skuId" controls-position="right" size="mini" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="商品名称" min-width="130">
              <template slot-scope="{ row }">
                <el-input v-model="row.goodsName" size="mini" placeholder="商品名称" />
              </template>
            </el-table-column>
            <el-table-column label="SKU图URL" min-width="130">
              <template slot-scope="{ row }">
                <el-input v-model="row.skuImage" size="mini" placeholder="SKU图URL" />
              </template>
            </el-table-column>
            <el-table-column label="规格JSON" min-width="130">
              <template slot-scope="{ row }">
                <el-input v-model="row.specJson" size="mini" placeholder="规格JSON" />
              </template>
            </el-table-column>
            <el-table-column label="成交单价" min-width="130">
              <template slot-scope="{ row }">
                <el-input-number v-model="row.price" controls-position="right" size="mini" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="购买数量" min-width="130">
              <template slot-scope="{ row }">
                <el-input-number v-model="row.quantity" controls-position="right" size="mini" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="小计" min-width="130">
              <template slot-scope="{ row }">
                <el-input-number v-model="row.totalAmount" controls-position="right" size="mini" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="130">
              <template slot-scope="{ row }">
                <el-input v-model="row.remark" size="mini" placeholder="备注" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template slot-scope="{ row, $index }">
                <el-button type="text" class="danger-btn" @click="mallOrderItemRows.splice($index, 1)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>

      <div slot="footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </div>
    </el-dialog>

    <!-- 详情抽屉（只读）。主子表：宽抽屉 + el-tabs，tab1 主表字段，tab2..N 每张明细一张只读表；
         数据复用 /with-children 一次拉全，不逐 tab 发请求 -->
    <el-drawer title="订单详情" :visible.sync="detailVisible" size="62%">
      <el-tabs v-model="activeDetailTab" type="card" class="detail-tabs">
        <el-tab-pane label="主表信息" name="main">
          <div class="detail-body">
            <div class="detail-row">
              <span class="detail-label">ID</span>
              <span class="detail-value">{{ detail.id }}</span>
            </div>
          <div class="detail-row">
            <span class="detail-label">订单号</span>
            <span class="detail-value">{{ detail.orderNo }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">会员ID 关联mall_member</span>
            <span class="detail-value">{{ detail.memberId }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">商品总额</span>
            <span class="detail-value">{{ detail.totalAmount }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">运费</span>
            <span class="detail-value">{{ detail.freightAmount }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">优惠金额</span>
            <span class="detail-value">{{ detail.discountAmount }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">实付金额</span>
            <span class="detail-value">{{ detail.payAmount }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">订单状态</span>
            <span class="detail-value">{{ enumText('status', detail.status) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">支付状态</span>
            <span class="detail-value">{{ enumText('payStatus', detail.payStatus) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">支付方式</span>
            <span class="detail-value">{{ enumText('payType', detail.payType) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">支付单号 关联pay_order</span>
            <span class="detail-value">{{ detail.payOrderNo }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">支付时间</span>
            <span class="detail-value">{{ detail.payTime }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">收货人</span>
            <span class="detail-value">{{ detail.receiverName }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">联系电话</span>
            <span class="detail-value">{{ detail.receiverPhone }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">收货地址</span>
            <span class="detail-value">{{ detail.receiverAddress }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">快递公司</span>
            <span class="detail-value">{{ detail.expressCompany }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">快递单号</span>
            <span class="detail-value">{{ detail.expressNo }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">发货时间</span>
            <span class="detail-value">{{ detail.deliverTime }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">完成时间</span>
            <span class="detail-value">{{ detail.finishTime }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">买家留言</span>
            <span class="detail-value">{{ detail.buyerRemark }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">管理员备注</span>
            <span class="detail-value">{{ detail.remark }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">创建时间</span>
            <span class="detail-value">{{ detail.createTime }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">更新时间</span>
            <span class="detail-value">{{ detail.updateTime }}</span>
          </div>
          </div>
        </el-tab-pane>
        <el-tab-pane label="订单明细" name="mallOrderItem">
          <el-table :data="detailGroupRows('mallOrderItem')" border size="mini" max-height="520">
            <el-table-column label="订单ID 关联mall_order" min-width="120" prop="orderId">
              <template slot-scope="{ row }">
                {{ row.orderId }}
              </template>
            </el-table-column>
            <el-table-column label="商品" min-width="120" prop="goodsId">
              <template slot-scope="{ row }">
                {{ row.goodsId }}
              </template>
            </el-table-column>
            <el-table-column label="SKU ID" min-width="120" prop="skuId">
              <template slot-scope="{ row }">
                {{ row.skuId }}
              </template>
            </el-table-column>
            <el-table-column label="商品名称" min-width="120" prop="goodsName">
              <template slot-scope="{ row }">
                {{ row.goodsName }}
              </template>
            </el-table-column>
            <el-table-column label="SKU图URL" min-width="120" prop="skuImage">
              <template slot-scope="{ row }">
                {{ row.skuImage }}
              </template>
            </el-table-column>
            <el-table-column label="规格JSON" min-width="120" prop="specJson">
              <template slot-scope="{ row }">
                {{ row.specJson }}
              </template>
            </el-table-column>
            <el-table-column label="成交单价" min-width="120" prop="price">
              <template slot-scope="{ row }">
                {{ row.price }}
              </template>
            </el-table-column>
            <el-table-column label="购买数量" min-width="120" prop="quantity">
              <template slot-scope="{ row }">
                {{ row.quantity }}
              </template>
            </el-table-column>
            <el-table-column label="小计" min-width="120" prop="totalAmount">
              <template slot-scope="{ row }">
                {{ row.totalAmount }}
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="120" prop="remark">
              <template slot-scope="{ row }">
                {{ row.remark }}
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <!-- <gen:end key="vue-template"> -->
  </div>
</template>

<script>
import {
  getMallOrderList,
  getMallOrderWithChildren,
  saveMallOrderWithChildren,
  exportMallOrder,
  createMallOrder,
  updateMallOrder,
  deleteMallOrder
} from '@/api/mall_order'
// 字典兜底、唯一性查重、导入失败明细下载都直接用通用 request，不依赖宿主是否提供专用 api 模块
import request from '@/utils/request'

const defaultForm = () => ({
  id: undefined,
  orderNo: '',
  memberId: undefined,
  totalAmount: undefined,
  freightAmount: undefined,
  discountAmount: undefined,
  payAmount: undefined,
  status: '',
  payStatus: '',
  payType: '',
  payOrderNo: '',
  payTime: undefined,
  receiverName: '',
  receiverPhone: '',
  receiverAddress: '',
  expressCompany: '',
  expressNo: '',
  deliverTime: undefined,
  finishTime: undefined,
  buyerRemark: '',
  remark: '',
})
// 每张子表的新行工厂：键 = 子表 varName（与后端 children[].key 一致）
const defaultChildMap = {
  'mallOrderItem': () => ({ goodsId: undefined, skuId: undefined, goodsName: '', skuImage: '', specJson: '', price: undefined, quantity: undefined, totalAmount: undefined, remark: '' })
}

export default {
  name: 'MallOrder',
  data() {
    return {
      // <gen:begin key="vue-data">
      loading: false,
      list: [],
      total: 0,
      query: { page: 1, size: 10 },
      dialogVisible: false,
      submitting: false,
      detailVisible: false,
      detail: {},
      // V4.4 详情多 tab：children 组数组 [{key, comment, rows}]，与 /with-children 响应同构
      detailChildren: [],
      activeDetailTab: 'main',
      activeChildTab: 'mallOrderItem',
      mallOrderItemRows: [],
      dicts: { sys_status: [], mall_mall_order_pay_type: [] },
      // 注释枚举内联在页面里：不依赖后端字典接口，页面秒开即可回显
      enumMap: {
        status: [{value: 10, label: '待付款'}, {value: 20, label: '待发货'}, {value: 30, label: '已发货'}, {value: 40, label: '已完成'}, {value: 50, label: '已取消'}, {value: 60, label: '已关闭'}],
        payStatus: [{value: 1, label: '已支付'}, {value: 0, label: '未支付'}],
        payType: [{value: 1, label: '微信'}, {value: 2, label: '支付宝'}]
      },
      rules: {
        orderNo: [{ required: true, message: '请输入订单号', trigger: 'blur' }, { max: 32, message: '订单号长度不能超过32个字符', trigger: 'blur' }],
        memberId: [{ required: true, message: '请输入会员ID 关联mall_member', trigger: 'blur' }],
        totalAmount: [{ required: true, message: '请输入商品总额', trigger: 'blur' }],
        freightAmount: [{ required: true, message: '请输入运费', trigger: 'blur' }],
        discountAmount: [{ required: true, message: '请输入优惠金额', trigger: 'blur' }],
        payAmount: [{ required: true, message: '请输入实付金额', trigger: 'blur' }],
        status: [{ required: true, message: '请选择订单状态', trigger: 'change' }],
        payStatus: [{ required: true, message: '请选择支付状态', trigger: 'change' }],
        payOrderNo: [{ max: 64, message: '支付单号 关联pay_order长度不能超过64个字符', trigger: 'blur' }],
        receiverName: [{ required: true, message: '请输入收货人', trigger: 'blur' }, { max: 50, message: '收货人长度不能超过50个字符', trigger: 'blur' }],
        receiverPhone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }, { max: 20, message: '联系电话长度不能超过20个字符', trigger: 'blur' }],
        receiverAddress: [{ required: true, message: '请输入收货地址', trigger: 'blur' }, { max: 300, message: '收货地址长度不能超过300个字符', trigger: 'blur' }],
        expressCompany: [{ max: 50, message: '快递公司长度不能超过50个字符', trigger: 'blur' }],
        expressNo: [{ max: 50, message: '快递单号长度不能超过50个字符', trigger: 'blur' }],
        buyerRemark: [{ max: 255, message: '买家留言长度不能超过255个字符', trigger: 'blur' }],
        remark: [{ max: 255, message: '管理员备注长度不能超过255个字符', trigger: 'blur' }],
      },
      form: defaultForm()
      // <gen:end key="vue-data">
    }
  },
  created() {
    this.loadDicts()
    this.fetchList()
  },
  methods: {
    // <gen:begin key="vue-methods">
    async loadDicts() {
      const codes = ['sys_status', 'mall_mall_order_pay_type']
      await Promise.all(codes.map(async code => {
        try {
          // 直接走通用 request，不依赖宿主工程是否提供 @/api/dict；
          // 宿主没有字典接口时这里会抛错，下面 catch 把选项降级为空数组，页面照常可用
          const res = await request({ url: '/api/system/dict/data/type/' + code, method: 'get' })
          this.$set(this.dicts, code, (res && res.data) || [])
        } catch (e) {
          this.$set(this.dicts, code, [])
        }
      }))
    },
    dictText(code, value) {
      const hit = (this.dicts[code] || []).find(d => String(d.value) === String(value))
      return hit ? hit.label : (value === undefined || value === null ? '' : value)
    },
    // 枚举翻译：值可能是数字也可能是字符串，统一按字符串比，避免 1 与 '1' 对不上
    enumText(field, value) {
      const hit = (this.enumMap[field] || []).find(d => String(d.value) === String(value))
      return hit ? hit.label : (value === undefined || value === null || value === '' ? '' : value)
    },
    async fetchList() {
      this.loading = true
      try {
        const res = await getMallOrderList(this.query)
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
      this.query = { page: 1, size: this.query.size }
      this.fetchList()
    },
    handleSizeChange(size) {
      this.query.size = size
      this.query.page = 1
      this.fetchList()
    },
    openDetail(row) {
      this.detail = row || {}
      this.detailVisible = true
      this.activeDetailTab = 'main'
      this.loadDetailChildren(row && row.id)
    },
    /**
     * 详情抽屉的明细数据：复用 /with-children 一次拉全，不逐 tab 发请求
     */
    async loadDetailChildren(id) {
      this.detailChildren = []
      if (!id) return
      try {
        const res = await getMallOrderWithChildren(id)
        this.detailChildren = (res.data && res.data.children) || []
      } catch (e) {
        /* 拦截器已提示；明细保持为空 */
      }
    },
    detailGroupRows(key) {
      const group = this.detailChildren.find(g => g.key === key)
      return (group && group.rows) || []
    },
    openDialog(row) {
      this.form = row ? { ...defaultForm(), ...row } : defaultForm()
      this.mallOrderItemRows = []
      this.dialogVisible = true
      if (row && row.id) {
        this.loadChildren(row.id)
      }
      this.$nextTick(() => {
        if (this.$refs.form) this.$refs.form.clearValidate()
      })
    },
    async loadChildren(id) {
      this.mallOrderItemRows = []
      try {
        const res = await getMallOrderWithChildren(id)
        // 后端 children: [{key, tableName, comment, rows}]；key 即子表 varName
        const groups = (res.data && res.data.children) || []
        groups.forEach(g => {
          if (this[g.key + 'Rows'] !== undefined) {
            this[g.key + 'Rows'] = g.rows || []
          }
        })
      } catch (e) {
        /* 拦截器已提示；保持空明细 */
      }
    },
    addChildRow(tab) {
      const factory = defaultChildMap[tab]
      this[tab + 'Rows'].push(factory ? factory() : {})
    },
    async submitForm() {
      const valid = await this.$refs.form.validate().catch(() => false)
      if (!valid) return
      this.submitting = true
      try {
        // 子表按组提交：与后端约定 null=不动该子表、[]=清空；编辑页全部组都提交（全量替换）
        const payload = { master: this.form }
        payload.mallOrderItemRows = this.mallOrderItemRows
        await saveMallOrderWithChildren(payload)
        this.$message.success(this.form.id ? '更新成功' : '创建成功')
        this.dialogVisible = false
        this.fetchList()
      } catch (e) { /* 拦截器已提示 */ } finally {
        this.submitting = false
      }
    },
    handleDelete(row) {
      this.$confirm(`确定删除该订单吗？`, '提示', { type: 'warning' })
        .then(async () => {
          await deleteMallOrder(row.id)
          this.$message.success('删除成功')
          if (this.list.length === 1 && this.query.page > 1) this.query.page--
          this.fetchList()
        })
        .catch(() => {})
    },
    async handleExport() {
      try {
        const res = await exportMallOrder(this.query)
        const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = '订单.xlsx'
        a.click()
        window.URL.revokeObjectURL(url)
      } catch (e) { /* 拦截器已提示 */ }
    }
    // <gen:end key="vue-methods">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====
  }
}
</script>

<style scoped>
/* <gen:begin key="vue-style"> */
.app-container {
  padding: 0;
  background: transparent;
}

/* ===== 页头 ===== */
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

/* ===== 卡片 ===== */
.filter-card,
.table-card {
  border: none;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04), 0 6px 20px rgba(16, 24, 40, 0.05);
}

.filter-card {
  margin-bottom: 16px;
}

.filter-card >>> .el-card__body {
  padding: 18px 18px 4px;
}

.filter-card >>> .el-form-item {
  margin-right: 16px;
  margin-bottom: 14px;
}

.filter-card >>> .el-form-item__label {
  color: #5a6478;
  font-size: 13px;
}

.table-card >>> .el-card__header {
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

.card-actions >>> .el-button + .el-button {
  margin-left: 8px;
}

/* ===== 表格 ===== */
.data-table >>> .table-head-cell {
  background: #f7f9fc !important;
  color: #5c6270;
  font-weight: 600;
}

.data-table >>> td {
  padding: 11px 0;
}

.data-table >>> .el-table__row:hover > td {
  background: #f5f9ff;
}

.data-table >>> .el-button--text {
  padding: 4px 6px;
}

/* ===== 分页 ===== */
.pagination {
  margin-top: 18px;
  text-align: right;
}

.danger-btn {
  color: #f56c6c;
}

.child-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.child-tip {
  color: #909399;
  font-size: 12px;
}

.import-summary {
  margin-bottom: 8px;
}

.import-tpl-tip {
  margin-bottom: 10px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.7;
  color: #606266;
}

.import-summary .ok {
  color: #67c23a;
}

.import-summary .bad {
  color: #f56c6c;
}

.import-fail-box {
  max-height: 200px;
  overflow: auto;
  padding: 8px;
  background: #fdf6ec;
  border-radius: 4px;
}

.import-fail-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
  font-weight: 600;
}

.import-fail {
  color: #f56c6c;
  font-size: 12px;
  line-height: 20px;
}

.import-fail-more {
  color: #909399;
  font-size: 12px;
}

.child-table {
  margin-bottom: 8px;
}

.detail-body {
  padding: 0 20px;
}

.detail-row {
  display: flex;
  padding: 8px 0;
  border-bottom: 1px solid #ebeef5;
  font-size: 13px;
}

.detail-label {
  width: 110px;
  color: #909399;
  flex-shrink: 0;
}

.detail-value {
  color: #303133;
  word-break: break-all;
}

.import-result {
  margin-top: 12px;
  font-size: 13px;
}

.import-fail {
  color: #f56c6c;
}
</style>
/* <gen:end key="vue-style"> */
