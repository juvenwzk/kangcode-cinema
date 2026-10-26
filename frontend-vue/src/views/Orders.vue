<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi, payApi } from '../api'

const loading = ref(false)
const orders = ref([])

const STATUS = {
  0: { text: '待支付', type: 'warning' },
  1: { text: '已支付', type: 'success' },
  2: { text: '已取消', type: 'info' },
  3: { text: '已出票', type: 'success' },
  4: { text: '已退票', type: 'danger' }
}

const money = (fen) => `¥${((fen || 0) / 100).toFixed(2)}`

async function load() {
  loading.value = true
  try {
    orders.value = (await orderApi.my()) || []
  } finally {
    loading.value = false
  }
}

async function pay(row) {
  try {
    await payApi.callback({ orderNo: row.orderNo, payNo: `PAY${Date.now()}` })
    ElMessage.success('支付成功')
    await load()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function cancel(row) {
  try {
    await ElMessageBox.confirm(`确认取消订单 ${row.orderNo}？取消后库存会返还。`, '取消订单', {
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await orderApi.cancel(row.orderNo)
    ElMessage.success('已取消，库存已返还')
    await load()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function refund(row) {
  try {
    await ElMessageBox.confirm(`确认对订单 ${row.orderNo} 办理退票？退票后不可撤销。`, '退票', {
      type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await orderApi.refund(row.orderNo)
    ElMessage.success('退票成功，库存已返还')
    await load()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

onMounted(load)
</script>

<template>
  <div class="container">
    <div class="section-title">
      <h2>我的订单</h2>
      <el-button size="small" plain @click="load">刷新</el-button>
    </div>

    <div class="card table-card">
      <el-table :data="orders" v-loading="loading" style="width: 100%">
        <el-table-column prop="orderNo" label="订单号" width="190" />
        <el-table-column prop="showDate" label="放映日期" width="120" />
        <el-table-column label="票种" width="120">
          <template #default="{ row }">{{ row.ticketType || '—' }}</template>
        </el-table-column>
        <el-table-column label="张数" width="80" align="center">
          <template #default="{ row }">{{ row.ticketCount }} 张</template>
        </el-table-column>
        <el-table-column label="金额" width="110">
          <template #default="{ row }">
            <span class="price">{{ money(row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="STATUS[row.status]?.type || 'info'" size="small" effect="light">
              {{ STATUS[row.status]?.text || `状态${row.status}` }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="下单时间" min-width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button link type="primary" @click="pay(row)">去支付</el-button>
              <el-button link type="info" @click="cancel(row)">取消</el-button>
            </template>
            <template v-else-if="row.status === 1 || row.status === 3">
              <el-button link type="danger" @click="refund(row)">退票</el-button>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="还没有订单，去影院看看" />
        </template>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.table-card {
  padding: 8px 12px;
}
</style>
