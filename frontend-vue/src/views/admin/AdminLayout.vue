<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../../api'
import { ROLE_KEY, TOKEN_KEY } from '../../api/http'

const route = useRoute()
const router = useRouter()

const username = ref(localStorage.getItem('mt_user') || '管理员')

const menus = [
  { path: '/admin/cinemas', label: '影院管理', icon: 'OfficeBuilding' },
  { path: '/admin/screenings', label: '场次管理', icon: 'Film' },
  { path: '/admin/stock', label: '票池管理', icon: 'Ticket' }
]

const active = computed(() => route.path)

async function handleLogout() {
  try {
    await authApi.logout()
  } catch (e) {
    /* 忽略：本地也会清掉 */
  }
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(ROLE_KEY)
  localStorage.removeItem('mt_user')
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <div class="admin">
    <aside class="side">
      <div class="brand">
        <span class="logo-mark">影</span>
        <div class="brand-text">
          <strong>影票</strong>
          <small>管理后台</small>
        </div>
      </div>

      <el-menu :default-active="active" router class="menu">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.label }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <div class="body">
      <header class="top">
        <div class="page-title">{{ route.meta.title }}</div>
        <div class="right">
          <router-link to="/cinemas" class="link">返回前台</router-link>
          <el-dropdown trigger="click">
            <span class="user-name">
              <el-avatar :size="26" class="avatar">{{ username.slice(0, 1) }}</el-avatar>
              {{ username }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/cinemas')">前台首页</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin {
  display: flex;
  min-height: 100vh;
  background: var(--page-bg);
}

.side {
  width: 210px;
  flex: 0 0 210px;
  background: #fff;
  border-right: 1px solid var(--line);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 64px;
  padding: 0 18px;
  border-bottom: 1px solid var(--line);
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

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.brand-text strong {
  font-size: 15px;
}

.brand-text small {
  color: var(--ink-3);
  font-size: 11px;
}

.menu {
  border-right: none;
  padding-top: 8px;
}

.body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.top {
  display: flex;
  align-items: center;
  height: 64px;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid var(--line);
}

.page-title {
  font-size: 16px;
  font-weight: 600;
}

.right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 18px;
}

.link {
  color: var(--el-color-primary);
  font-size: 13px;
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

.content {
  flex: 1;
  padding: 20px 24px 40px;
}
</style>
