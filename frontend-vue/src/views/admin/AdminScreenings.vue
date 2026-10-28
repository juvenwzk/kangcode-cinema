<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../api'
import ImageUpload from '../../components/ImageUpload.vue'

const loading = ref(false)
const rows = ref([])
const keyword = ref('')
const page = reactive({ current: 1, size: 10, total: 0 })

const cinemas = ref([])
const halls = ref([])

const createVisible = ref(false)
const createRef = ref()
const submitting = ref(false)
const createForm = reactive({
  screeningNo: '',
  movieName: '',
  posterUrl: '',
  cinemaId: null,
  hallId: null,
  startTime: '10:00:00',
  endTime: '12:00:00'
})

const editVisible = ref(false)
const editRef = ref()
const editForm = reactive({ id: null, movieName: '', posterUrl: '', status: 1 })

const createRules = {
  screeningNo: [{ required: true, message: '请输入场次号', trigger: 'blur' }],
  movieName: [{ required: true, message: '请输入影片名称', trigger: 'blur' }],
  cinemaId: [{ required: true, message: '请选择影院', trigger: 'change' }],
  hallId: [{ required: true, message: '请选择影厅', trigger: 'change' }]
}

const cinemaNameMap = computed(() => {
  const map = {}
  cinemas.value.forEach((c) => {
    map[c.id] = c.cinemaName
  })
  return map
})

async function load() {
  loading.value = true
  try {
    const data = await adminApi.screening.page({
      page: page.current,
      size: page.size,
      keyword: keyword.value || undefined
    })
    rows.value = data.records || []
    page.total = Number(data.total || 0)
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

async function loadCinemas() {
  try {
    cinemas.value = await adminApi.cinema.list()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function onCinemaChange(cinemaId) {
  createForm.hallId = null
  halls.value = []
  if (!cinemaId) return
  try {
    halls.value = await adminApi.cinema.halls(cinemaId)
  } catch (e) {
    /* 拦截器已提示 */
  }
}

function openCreate() {
  Object.assign(createForm, {
    screeningNo: '',
    movieName: '',
    posterUrl: '',
    cinemaId: null,
    hallId: null,
    startTime: '10:00:00',
    endTime: '12:00:00'
  })
  halls.value = []
  createVisible.value = true
}

async function submitCreate() {
  try {
    await createRef.value.validate()
  } catch (e) {
    return
  }
  if (createForm.endTime <= createForm.startTime) {
    ElMessage.warning('散场时间必须晚于开场时间')
    return
  }
  submitting.value = true
  try {
    await adminApi.screening.create({ ...createForm })
    ElMessage.success('场次已新增')
    createVisible.value = false
    page.current = 1
    load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

function openEdit(row) {
  Object.assign(editForm, {
    id: row.id,
    movieName: row.movieName,
    posterUrl: row.posterUrl || '',
    status: row.status
  })
  editVisible.value = true
}

async function submitEdit() {
  try {
    await editRef.value.validate()
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    await adminApi.screening.update(editForm.id, {
      movieName: editForm.movieName,
      posterUrl: editForm.posterUrl || '',
      status: editForm.status
    })
    ElMessage.success('场次已更新')
    editVisible.value = false
    load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

/**
 * 删除 vs 下架：
 * 有订单的场次后端会拒绝删除（订单是凭证），这时用「下架」——状态置 0，前台不再展示。
 */
async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除场次「${row.screeningNo}」吗？已有订单的场次不允许删除，只能下架。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    await adminApi.screening.remove(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    /* 后端会返回「已有 N 笔订单」 */
  }
}

async function toggleStatus(row) {
  const next = row.status === 1 ? 0 : 1
  try {
    await adminApi.screening.update(row.id, { movieName: row.movieName, status: next })
    ElMessage.success(next === 1 ? '已上架' : '已下架')
    load()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

onMounted(() => {
  loadCinemas()
  load()
})
</script>

<template>
  <el-card shadow="never" class="card">
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="按场次号 / 影片名搜索"
        clearable
        style="width: 260px"
        @keyup.enter="((page.current = 1), load())"
        @clear="((page.current = 1), load())"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button @click="((page.current = 1), load())">查询</el-button>
      <div class="spacer" />
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon> 新增场次
      </el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe style="width: 100%">
      <el-table-column prop="screeningNo" label="场次号" width="110" />
      <el-table-column label="海报" width="80">
        <template #default="{ row }">
          <el-image
            v-if="row.posterUrl"
            :src="row.posterUrl"
            :preview-src-list="[row.posterUrl]"
            preview-teleported
            fit="cover"
            class="poster-thumb"
          />
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="movieName" label="影片" min-width="160" />
      <el-table-column label="影院" min-width="150">
        <template #default="{ row }">
          {{ cinemaNameMap[row.cinemaId] || `#${row.cinemaId}` }}
        </template>
      </el-table-column>
      <el-table-column prop="hallId" label="影厅ID" width="90" />
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ row.startTime }} ~ {{ row.endTime }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '上映' : '下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" align="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(row)">
            {{ row.status === 1 ? '下架' : '上架' }}
          </el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :total="page.total"
        layout="total, prev, pager, next"
        @current-change="load"
      />
    </div>
  </el-card>

  <!-- 新增场次 -->
  <el-dialog v-model="createVisible" title="新增场次" width="500px" destroy-on-close>
    <el-form ref="createRef" :model="createForm" :rules="createRules" label-width="90px">
      <el-form-item label="场次号" prop="screeningNo">
        <el-input v-model="createForm.screeningNo" placeholder="如 S1003" />
      </el-form-item>
      <el-form-item label="影片" prop="movieName">
        <el-input v-model="createForm.movieName" placeholder="如 流浪地球3" />
      </el-form-item>
      <el-form-item label="电影海报">
        <ImageUpload v-model="createForm.posterUrl" dir="movie" :width="110" :height="150" tip="竖版海报效果最好，不超过 5MB" />
      </el-form-item>
      <el-form-item label="影院" prop="cinemaId">
        <el-select
          v-model="createForm.cinemaId"
          placeholder="请选择影院"
          style="width: 100%"
          @change="onCinemaChange"
        >
          <el-option
            v-for="c in cinemas"
            :key="c.id"
            :label="`${c.cinemaName}（${c.cinemaCode}）`"
            :value="c.id"
            :disabled="c.status === 0"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="影厅" prop="hallId">
        <el-select v-model="createForm.hallId" placeholder="请先选择影院" style="width: 100%" :disabled="!createForm.cinemaId">
          <el-option v-for="h in halls" :key="h.id" :label="`${h.hallNo} 号厅`" :value="h.id" />
        </el-select>
        <div v-if="createForm.cinemaId && halls.length === 0" class="tip">
          该影院还没有影厅数据，无法新建场次
        </div>
      </el-form-item>
      <el-form-item label="开场时间">
        <el-time-picker v-model="createForm.startTime" value-format="HH:mm:ss" placeholder="开场" style="width: 100%" />
      </el-form-item>
      <el-form-item label="散场时间">
        <el-time-picker v-model="createForm.endTime" value-format="HH:mm:ss" placeholder="散场" style="width: 100%" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitCreate">保存</el-button>
    </template>
  </el-dialog>

  <!-- 编辑场次 -->
  <el-dialog v-model="editVisible" title="编辑场次" width="460px" destroy-on-close>
    <el-form ref="editRef" :model="editForm" label-width="90px">
      <el-form-item label="影片名称" prop="movieName" :rules="[{ required: true, message: '请输入影片名称' }]">
        <el-input v-model="editForm.movieName" />
      </el-form-item>
      <el-form-item label="电影海报">
        <ImageUpload v-model="editForm.posterUrl" dir="movie" :width="110" :height="150" tip="竖版海报效果最好，不超过 5MB" />
      </el-form-item>
      <el-form-item label="状态">
        <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" active-text="上映" inactive-text="下架" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitEdit">保存</el-button>
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
  margin-bottom: 16px;
}

.spacer {
  flex: 1;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.tip {
  color: var(--ink-3);
  font-size: 12px;
  line-height: 1.4;
}

.poster-thumb {
  width: 44px;
  height: 60px;
  border-radius: 4px;
  background: var(--surface-2, #f5f5f5);
}

.muted {
  color: var(--ink-3, #999);
}
</style>
