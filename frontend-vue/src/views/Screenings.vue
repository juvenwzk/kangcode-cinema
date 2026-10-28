<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { cinemaApi, screeningApi, ticketApi } from '../api'
import { TOKEN_KEY } from '../api/http'

const route = useRoute()
const router = useRouter()
const cinemaId = Number(route.params.id)

const loading = ref(true)
const cinema = ref(null)
const screenings = ref([])
const showDate = ref(formatDate(new Date()))

const dialogVisible = ref(false)
const current = ref(null)
const seats = ref([])
const seatLoading = ref(false)
const form = ref({ ticketType: '', ticketCount: 1, passengerName: '', idCardNo: '' })
const submitting = ref(false)

function formatDate(d) {
  const m = `${d.getMonth() + 1}`.padStart(2, '0')
  const day = `${d.getDate()}`.padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

const list = computed(() => screenings.value.filter((s) => s.cinemaId === cinemaId))

async function load() {
  loading.value = true
  try {
    const [cinemas, page] = await Promise.all([
      cinemaApi.list(),
      screeningApi.page({ page: 1, size: 200 })
    ])
    cinema.value = (cinemas || []).find((c) => c.id === cinemaId) || null
    screenings.value = page?.records || []

    // 自动定位到「已放票」的日期，避免用户点进去发现没票
    const mine = screenings.value.filter((s) => s.cinemaId === cinemaId)
    if (mine.length) {
      try {
        const dates = await screeningApi.dates(mine[0].id)
        if (dates && dates.length) showDate.value = dates[0]
      } catch (e) {
        /* 忽略：保持默认今天 */
      }
    }
  } finally {
    loading.value = false
  }
}

async function openBuy(s) {
  if (!localStorage.getItem(TOKEN_KEY)) {
    ElMessage.warning('请先登录')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  current.value = s
  form.value = { ticketType: '', ticketCount: 1, passengerName: '', idCardNo: '' }
  dialogVisible.value = true
  seatLoading.value = true
  try {
    seats.value = (await screeningApi.seats(s.id, showDate.value)) || []
    const first = seats.value.find((x) => x.remain > 0)
    if (first) form.value.ticketType = first.ticketType
  } finally {
    seatLoading.value = false
  }
}

async function refreshSeats() {
  if (!current.value) return
  seatLoading.value = true
  try {
    seats.value = (await screeningApi.seats(current.value.id, showDate.value)) || []
    if (!seats.value.some((x) => x.ticketType === form.value.ticketType && x.remain > 0)) {
      const first = seats.value.find((x) => x.remain > 0)
      form.value.ticketType = first ? first.ticketType : ''
    }
  } finally {
    seatLoading.value = false
  }
}

const totalAmount = computed(() => {
  const s = seats.value.find((x) => x.ticketType === form.value.ticketType)
  return s ? s.price * form.value.ticketCount : 0
})

async function submit() {
  if (!form.value.ticketType) {
    ElMessage.warning('请选择票种')
    return
  }
  submitting.value = true
  try {
    const res = await ticketApi.buy({
      screeningId: current.value.id,
      showDate: showDate.value,
      ticketType: form.value.ticketType,
      ticketCount: form.value.ticketCount,
      passengerName: form.value.passengerName || undefined,
      idCardNo: form.value.idCardNo || undefined
    })
    dialogVisible.value = false
    ElMessage.success(`下单成功，订单号 ${res.orderNo}，请在 30 分钟内支付`)
    router.push('/orders')
  } catch (e) {
    /* 错误提示已在拦截器里统一处理 */
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="container">
    <!-- 影院信息 -->
    <div class="card cinema-head" v-loading="loading">
      <div class="head-left">
        <div class="head-brand">
          <div v-if="cinema?.logoUrl" class="head-logo">
            <img :src="cinema.logoUrl" :alt="cinema.cinemaName" />
          </div>
          <div>
            <h1>{{ cinema?.cinemaName || '影院' }}</h1>
            <div class="head-meta">
              <el-icon><Location /></el-icon>
              {{ cinema?.city || '—' }}
              <span class="dot">·</span>
              今日 {{ list.length }} 个场次
            </div>
          </div>
        </div>
      </div>
      <el-button plain @click="router.push('/cinemas')">返回影院列表</el-button>
    </div>

    <!-- 日期 + 场次 -->
    <div class="section-title">
      <h2>场次</h2>
      <div class="date-bar">
        <span class="muted">放映日期</span>
        <el-date-picker
          v-model="showDate"
          type="date"
          value-format="YYYY-MM-DD"
          size="small"
          style="width: 150px"
          @change="refreshSeats"
        />
      </div>
    </div>

    <div v-loading="loading" class="card screening-list">
      <div v-for="s in list" :key="s.id" class="screening-row">
        <!-- 电影海报：没有就留一块灰底占位，保证时间/片名/按钮三列始终对齐 -->
        <div class="poster">
          <img v-if="s.posterUrl" :src="s.posterUrl" :alt="s.movieName" loading="lazy" />
          <span v-else>暂无<br />海报</span>
        </div>

        <div class="time-box">
          <div class="time">{{ (s.startTime || '').slice(0, 5) }}</div>
          <div class="end">{{ (s.endTime || '').slice(0, 5) }} 散场</div>
        </div>

        <div class="movie-box">
          <div class="movie-name">{{ s.movieName || '未命名影片' }}</div>
          <div class="movie-meta">
            <el-tag size="small" effect="plain" type="info">场次号 {{ s.screeningNo }}</el-tag>
            <el-tag size="small" effect="plain" type="info">影厅 {{ s.hallId }}</el-tag>
            <el-tag v-if="s.status === 0" size="small" type="warning" effect="plain">已下架</el-tag>
          </div>
        </div>

        <div class="screening-side">
          <el-button type="primary" round :disabled="s.status === 0" @click="openBuy(s)">
            选座购票
          </el-button>
        </div>
      </div>

      <el-empty v-if="!loading && !list.length" description="这家影院还没有排片" />
    </div>

    <!-- 购票弹窗 -->
    <el-dialog v-model="dialogVisible" title="选择票种" width="560px">
      <div class="dialog-movie">
        <img v-if="current?.posterUrl" :src="current.posterUrl" class="dialog-poster" :alt="current?.movieName" />
        <div class="dialog-info">
          <b>{{ current?.movieName }}</b>
          <span class="muted">
            {{ showDate }} {{ (current?.startTime || '').slice(0, 5) }} · 影厅 {{ current?.hallId }}
          </span>
        </div>
      </div>

      <div v-loading="seatLoading">
        <div v-if="!seats.length" class="empty-seats">
          <el-empty description="这天还没有放票，换个日期试试" :image-size="80" />
        </div>

        <div v-else class="seat-options">
          <div
            v-for="s in seats"
            :key="s.ticketType"
            class="seat-option"
            :class="{ active: form.ticketType === s.ticketType, soldOut: s.remain <= 0 }"
            @click="s.remain > 0 && (form.ticketType = s.ticketType)"
          >
            <div class="seat-type">{{ s.ticketType }}</div>
            <div class="seat-price price">
              ¥{{ (s.price / 100).toFixed(2) }}
            </div>
            <div class="seat-remain">
              {{ s.remain > 0 ? `余 ${s.remain} 张` : '已售罄' }}
            </div>
          </div>
        </div>
      </div>

      <el-form label-width="80px" style="margin-top: 16px">
        <el-form-item label="张数">
          <el-input-number v-model="form.ticketCount" :min="1" :max="5" />
        </el-form-item>
        <el-form-item label="乘客姓名">
          <el-input v-model="form.passengerName" placeholder="选填，不填记为「乘客」" />
        </el-form-item>
        <el-form-item label="证件号">
          <el-input v-model="form.idCardNo" placeholder="选填" />
        </el-form-item>
        <el-form-item label="合计">
          <span class="price total">¥{{ (totalAmount / 100).toFixed(2) }}</span>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交订单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.cinema-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px;
  margin-top: 16px;
}

.cinema-head h1 {
  margin: 0 0 8px;
  font-size: 20px;
}

.head-brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

.head-logo {
  width: 56px;
  height: 56px;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  flex: 0 0 56px;
}

.head-logo img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.poster {
  flex: 0 0 56px;
  width: 56px;
  height: 78px;
  border-radius: 6px;
  overflow: hidden;
  background: var(--surface-2, #f5f5f5);
  color: var(--ink-3, #aaa);
  font-size: 11px;
  line-height: 1.3;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.poster img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.head-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--ink-3);
  font-size: 13px;
}

.dot {
  margin: 0 2px;
}

.date-bar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.screening-list {
  padding: 6px 20px;
}

.screening-row {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 16px 0;
  border-bottom: 1px solid var(--line);
}

.screening-row:last-child {
  border-bottom: none;
}

.time-box {
  flex: 0 0 92px;
}

.time {
  font-size: 20px;
  font-weight: 700;
}

.end {
  color: var(--ink-3);
  font-size: 12px;
}

.movie-box {
  flex: 1;
  min-width: 0;
}

.movie-name {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 6px;
}

.movie-meta {
  display: flex;
  gap: 6px;
}

.screening-side {
  flex: 0 0 120px;
  text-align: right;
}

.dialog-movie {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.dialog-poster {
  width: 48px;
  height: 66px;
  border-radius: 6px;
  object-fit: cover;
  flex: 0 0 48px;
}

.dialog-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.seat-options {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.seat-option {
  flex: 1;
  min-width: 130px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  cursor: pointer;
  text-align: center;
  transition: all 0.15s;
}

.seat-option:hover {
  border-color: var(--el-color-primary-light-5);
}

.seat-option.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.seat-option.soldOut {
  cursor: not-allowed;
  opacity: 0.5;
}

.seat-type {
  font-weight: 600;
  margin-bottom: 4px;
}

.seat-price {
  font-size: 17px;
}

.seat-remain {
  color: var(--ink-3);
  font-size: 12px;
  margin-top: 2px;
}

.total {
  font-size: 20px;
}
</style>
