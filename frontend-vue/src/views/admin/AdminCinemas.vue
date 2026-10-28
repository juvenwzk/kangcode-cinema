<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../api'
import ImageUpload from '../../components/ImageUpload.vue'

const loading = ref(false)
const rows = ref([])
const keyword = ref('')

const dialogVisible = ref(false)
const mode = ref('create')
const formRef = ref()
const submitting = ref(false)

const form = reactive({
  id: null,
  cinemaCode: '',
  cinemaName: '',
  city: '',
  logoUrl: '',
  status: 1
})

const rules = {
  cinemaCode: [
    { required: true, message: '请输入影院编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9]{2,16}$/, message: '只能 2~16 位字母或数字', trigger: 'blur' }
  ],
  cinemaName: [
    { required: true, message: '请输入影院名称', trigger: 'blur' },
    { max: 32, message: '最长 32 位', trigger: 'blur' }
  ],
  city: [{ max: 32, message: '最长 32 位', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    rows.value = await adminApi.cinema.list(keyword.value || undefined)
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

function openCreate() {
  mode.value = 'create'
  Object.assign(form, { id: null, cinemaCode: '', cinemaName: '', city: '', logoUrl: '', status: 1 })
  dialogVisible.value = true
}

function openEdit(row) {
  mode.value = 'edit'
  Object.assign(form, { ...row, logoUrl: row.logoUrl || '' })
  dialogVisible.value = true
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    if (mode.value === 'create') {
      await adminApi.cinema.create({
        cinemaCode: form.cinemaCode,
        cinemaName: form.cinemaName,
        city: form.city,
        logoUrl: form.logoUrl || '',
        status: form.status
      })
      ElMessage.success('影院已新增')
    } else {
      // 影编码是唯一业务键，改它等于换一家影院，所以只允许改名称、城市和 Logo。
      // logoUrl 传空串 = 清空 Logo（后端按「null 不改、空串清空」区分语义）
      await adminApi.cinema.update(form.id, {
        cinemaName: form.cinemaName,
        city: form.city,
        logoUrl: form.logoUrl || ''
      })
      ElMessage.success('影院已更新')
    }
    dialogVisible.value = false
    load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除影院「${row.cinemaName}」吗？影院下还有场次时无法删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return // 用户取消
  }
  try {
    await adminApi.cinema.remove(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    /* 后端会返回「该影院下还有 N 个场次」 */
  }
}

onMounted(load)
</script>

<template>
  <el-card shadow="never" class="card">
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="按影院名称 / 编码搜索"
        clearable
        style="width: 260px"
        @keyup.enter="load"
        @clear="load"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button @click="load">查询</el-button>
      <div class="spacer" />
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon> 新增影院
      </el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="Logo" width="90">
        <template #default="{ row }">
          <el-image
            v-if="row.logoUrl"
            :src="row.logoUrl"
            :preview-src-list="[row.logoUrl]"
            preview-teleported
            fit="contain"
            class="thumb"
          />
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="cinemaCode" label="影院编码" width="120" />
      <el-table-column prop="cinemaName" label="影院名称" min-width="180" />
      <el-table-column prop="city" label="城市" width="100" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog
    v-model="dialogVisible"
    :title="mode === 'create' ? '新增影院' : '编辑影院'"
    width="460px"
    destroy-on-close
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="影院编码" prop="cinemaCode">
        <el-input v-model="form.cinemaCode" :disabled="mode === 'edit'" placeholder="如 GZ003" />
      </el-form-item>
      <el-form-item label="影院名称" prop="cinemaName">
        <el-input v-model="form.cinemaName" placeholder="如 万达影城·天河店" />
      </el-form-item>
      <el-form-item label="城市" prop="city">
        <el-input v-model="form.city" placeholder="如 广州" />
      </el-form-item>
      <el-form-item label="影院 Logo">
        <ImageUpload v-model="form.logoUrl" dir="cinema" :width="160" :height="100" />
      </el-form-item>
      <el-form-item v-if="mode === 'create'" label="状态">
        <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="正常" inactive-text="停用" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
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

.thumb {
  width: 64px;
  height: 40px;
  border-radius: 4px;
  background: var(--surface-2, #f5f5f5);
}

.muted {
  color: var(--ink-3, #999);
}
</style>
