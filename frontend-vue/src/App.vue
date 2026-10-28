<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi, userApi } from './api'
import { ROLE_KEY, TOKEN_KEY } from './api/http'
import ImageUpload from './components/ImageUpload.vue'

const router = useRouter()
const route = useRoute()
const user = ref(null)

/** 头像弹窗 */
const avatarVisible = ref(false)
const avatarDraft = ref('')
const avatarSaving = ref(false)

function openAvatar() {
  avatarDraft.value = user.value?.avatarUrl || ''
  avatarVisible.value = true
}

async function saveAvatar() {
  if (!avatarDraft.value) {
    ElMessage.warning('请先上传一张头像')
    return
  }
  avatarSaving.value = true
  try {
    await userApi.updateAvatar(avatarDraft.value)
    // 本地同步一份，避免为了看新头像再请求一次 /user/me
    user.value = { ...user.value, avatarUrl: avatarDraft.value }
    avatarVisible.value = false
    ElMessage.success('头像已更新')
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    avatarSaving.value = false
  }
}

/** 没上传头像时用昵称首字兜底 */
const avatarText = computed(() => (user.value?.nickname || user.value?.username || 'U').slice(0, 1))

/** 管理端用独立布局（自己的侧边栏），不能再套用户端这层头部和页脚 */
const isAdminRoute = computed(() => route.path.startsWith('/admin'))

const isAdmin = computed(() => (user.value?.roles || []).includes('ADMIN'))

const navs = computed(() => {
  const list = [
    { label: '首页', to: '/' },
    { label: '影院', to: '/cinemas' },
    { label: '我的订单', to: '/orders' }
  ]
  // 只有管理员才看到入口；即便手动敲 /admin，路由守卫和后端也还有两道校验
  if (isAdmin.value) {
    list.push({ label: '管理后台', to: '/admin/cinemas' })
  }
  return list
})

async function loadUser() {
  if (!localStorage.getItem(TOKEN_KEY)) {
    user.value = null
    localStorage.removeItem(ROLE_KEY)
    return
  }
  try {
    user.value = await authApi.me()
    // 角色同步到本地：路由守卫是同步的，只能读本地缓存做第一道判断
    localStorage.setItem(ROLE_KEY, user.value.roles?.[0] || 'USER')
    localStorage.setItem('mt_user', user.value.nickname || user.value.username || '')
  } catch (e) {
    user.value = null
  }
}

async function handleLogout() {
  try {
    await authApi.logout()
  } catch (e) {
    /* 忽略：本地也已经登出了 */
  }
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(ROLE_KEY)
  localStorage.removeItem('mt_user')
  user.value = null
  ElMessage.success('已退出登录')
  router.push('/cinemas')
}

onMounted(loadUser)

defineExpose({ loadUser })
</script>

<template>
  <!-- 管理端：交给 AdminLayout 自己套壳 -->
  <router-view v-if="isAdminRoute" />

  <div v-else class="app">
    <header class="header">
      <div class="container header-inner">
        <router-link to="/cinemas" class="logo">
          <span class="logo-mark">影</span>
          <span class="logo-text">影票</span>
        </router-link>

        <span class="city">
          <el-icon><Location /></el-icon>
          广州
        </span>

        <nav class="nav">
          <router-link
            v-for="n in navs"
            :key="n.to"
            :to="n.to"
            class="nav-item"
            :class="{ active: n.to === '/' ? route.path === '/' : route.path.startsWith(n.to) }"
          >
            {{ n.label }}
          </router-link>
        </nav>

        <div class="user">
          <template v-if="user">
            <el-dropdown trigger="click">
              <span class="user-name">
                <el-avatar :size="26" :src="user.avatarUrl || undefined" class="avatar">
                  {{ avatarText }}
                </el-avatar>
                {{ user.nickname || user.username }}
                <el-icon><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="router.push('/orders')">我的订单</el-dropdown-item>
                  <el-dropdown-item @click="openAvatar">更换头像</el-dropdown-item>
                  <el-dropdown-item v-if="isAdmin" @click="router.push('/admin/cinemas')">
                    管理后台
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <router-link v-else to="/login" class="login-btn">登录 / 注册</router-link>
        </div>
      </div>
    </header>

    <main class="main">
      <router-view @logined="loadUser" />
    </main>

    <footer class="footer">
      <div class="container">影票 · Spring Boot + Vue3 + Element Plus 课程项目</div>
    </footer>

    <!-- 更换头像：先传 OSS 拿 URL，点保存才落库 -->
    <el-dialog v-model="avatarVisible" title="更换头像" width="360px">
      <div class="avatar-dialog">
        <ImageUpload v-model="avatarDraft" dir="avatar" :width="120" :height="120" tip="正方形图片效果最好，不超过 5MB" />
      </div>
      <template #footer>
        <el-button @click="avatarVisible = false">取消</el-button>
        <el-button type="primary" :loading="avatarSaving" @click="saveAvatar">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.header {
  background: #fff;
  border-bottom: 1px solid var(--line);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-inner {
  display: flex;
  align-items: center;
  height: 64px;
  gap: 24px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
}

.logo-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--el-color-primary);
  color: #fff;
  font-weight: 700;
}

.logo-text {
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 1px;
}

.city {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--ink-2);
  font-size: 13px;
}

.nav {
  display: flex;
  height: 100%;
  margin-left: 12px;
}

.nav-item {
  display: flex;
  align-items: center;
  padding: 0 18px;
  color: var(--ink-1);
  font-size: 15px;
  border-bottom: 2px solid transparent;
}

.nav-item:hover {
  color: var(--el-color-primary);
}

.nav-item.active {
  color: #fff;
  background: var(--el-color-primary);
  border-bottom-color: var(--el-color-primary);
}

.user {
  margin-left: auto;
}

.user-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 13px;
}

.avatar {
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
  font-weight: 600;
}

.login-btn {
  color: var(--el-color-primary);
  font-size: 14px;
}

.avatar-dialog {
  display: flex;
  justify-content: center;
  padding: 6px 0;
}

.main {
  min-height: calc(100vh - 140px);
  padding-bottom: 40px;
}

.footer {
  padding: 20px 0;
  color: var(--ink-3);
  font-size: 12px;
  text-align: center;
  border-top: 1px solid var(--line);
  background: #fff;
}
</style>
