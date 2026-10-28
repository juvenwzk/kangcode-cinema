<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../../api'

const screenings = ref([])
const screeningId = ref(null)
const loading = ref(false)
const rows = ref([])

const ticketTypes = ['普通票', '学生票', 'IMAX厅票']

const publishForm = reactive({
  showDate: '',
  ticketType: '普通票',
  count: 50
})
const publishing = ref(false)

const priceVisible = ref(false)
const priceForm = reactive({ id: null, ticketType: '', priceYuan: 0 })
const savingPrice = ref(false)

const currentScreening = computed(() => screenings.value.find((s) => s.id === screeningId.value))

async function loadScreenings() {
  try {
    const data = await adminApi.screening.page({ page: 1, size: 100 })
    screenings.value = data.records || []
    if (screenings.value.length && !screeningId.value) {
      screeningId.value = screenings.value[0].id
      loadStock()
    }
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function loadStock() {
  if (!screeningId.value) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    rows.value = await adminApi.stock.list({ screeningId: screeningId.value })
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

async function publish() {
  if (!screeningId.value) {
    ElMessage.warning('请先选择场次')
    return
  }
  if (!publishForm.showDate) {
    ElMessage.warning('请选择放映日期')
    return
  }
  if (!publishForm.count || publishForm.count < 1) {
    ElMessage.warning('放票数量至少 1 张')
    return
  }
  publishing.value = true
  try {
    const remain = await adminApi.stock.publish({
      screeningId: screeningId.value,
      showDate: publishForm.showDate,
      ticketType: publishForm.ticketType,
      count: publishForm.count
    })
    ElMessage.success(`放票成功，当前余票 ${remain} 张`)
    loadStock()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    publishing.value = false
  }
}

function openPrice(row) {
  Object.assign(priceForm, {
    id: row.id,
    ticketType: row.ticketType,
    priceYuan: Number((row.price / 100).toFixed(2))
  })
  priceVisible.value = true
}

async function savePrice() {
  if (priceForm.priceYuan < 0) {
    ElMessage.warning('票价不能为负数')
    return
  }
  savingPrice.value = true
  try {
    // 后端存的是「分」，前端输入的是「元」：×100 前先取整，避免 34.99*100=3498.9999 这种浮点误差
    await adminApi.stock.updatePrice(priceForm.id, Math.round(priceForm.priceYuan * 100))
    ElMessage.success('票价已更新')
    priceVisible.value = false
    loadStock()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    savingPrice.value = false
  }
}

onMounted(loadScreenings)
</script>

<template>
  <el-card shadow="never" class="card">
    <div class="toolbar">
      <span class="label">场次</span>
      <el-select
        v-model="screeningId"
        placeholder="请选择场次"
        style="width: 300px"
        @change="loadStock"
      >
        <el-option
          v-for="s in screenings"
          :key="s.id"
          :label="`${s.screeningNo} · ${s.movieName}`"
          :value="s.id"
        />
      </el-select>
      <el-button @click="loadStock">刷新</el-button>
      <div class="spacer" />
      <span class="hint">库存 = 数据库 total/remain + Redis 余票缓存，放票会同时更新两边</span>
    </div>

    <el-alert
      v-if="currentScreening"
      type="info"
      :closable="false"
      show-icon
      class="alert"
      :title="`${currentScreening.screeningNo} · ${currentScreening.movieName}`"
      :description="`放映时间 ${currentScreening.startTime} ~ ${currentScreening.endTime}`"
    />

    <el-form :inline="true" class="publish-bar">
      <el-form-item label="放映日期">
        <el-date-picker
          v-model="publishForm.showDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择日期"
        />
      </el-form-item>
      <el-form-item label="票种">
        <el-select v-model="publishForm.ticketType" style="width: 140px">
          <el-option v-for="t in ticketTypes" :key="t" :label="t" :value="t" />
        </el-select>
      </el-form-item>
      <el-form-item label="放票数量">
        <el-input-number v-model="publishForm.count" :min="1" :max="9999" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="publishing" @click="publish">
          <el-icon><Plus /></el-icon> 放票
        </el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="rows" stripe style="width: 100%">
      <el-table-column prop="showDate" label="放映日期" width="130" />
      <el-table-column prop="ticketType" label="票种" width="130" />
      <el-table-column prop="total" label="总票数" width="100" />
      <el-table-column label="余票" width="100">
        <template #default="{ row }">
          <el-tag :type="row.remain > 0 ? 'success' : 'danger'" size="small">{{ row.remain }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="票价" width="120">
        <template #default="{ row }">¥{{ (row.price / 100).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="操作" align="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openPrice(row)">改价</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && rows.length === 0" description="该场次还没有放票记录" :image-size="80" />
  </el-card>

  <el-dialog v-model="priceVisible" title="调整票价" width="400px" destroy-on-close>
    <el-form label-width="80px">
      <el-form-item label="票种">
        <el-input :model-value="priceForm.ticketType" disabled />
      </el-form-item>
      <el-form-item label="票价(元)">
        <el-input-number v-model="priceForm.priceYuan" :min="0" :precision="2" :step="5" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="priceVisible = false">取消</el-button>
      <el-button type="primary" :loading="savingPrice" @click="savePrice">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.card {
  border-radius: 10px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.label {
  color: var(--ink-2);
  font-size: 13px;
}

.hint {
  color: var(--ink-3);
  font-size: 12px;
}

.spacer {
  flex: 1;
}

.alert {
  margin-bottom: 14px;
}

.publish-bar {
  padding: 12px 14px 0;
  background: var(--page-bg);
  border-radius: 8px;
  margin-bottom: 14px;
}
</style>
