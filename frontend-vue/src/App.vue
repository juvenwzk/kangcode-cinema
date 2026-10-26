<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from './api'
import { TOKEN_KEY } from './api/http'

const router = useRouter()
const route = useRoute()
const user = ref(null)

const navs = [
  { label: '首页', to: '/' },
  { label: '影院', to: '/cinemas' },
  { label: '我的订单', to: '/orders' }
]

async function loadUser() {
  if (!localStorage.getItem(TOKEN_KEY)) {
    user.value = null
    return
  }
  try {
    user.value = await authApi.me()
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
  user.value = null
  ElMessage.success('已退出登录')
  router.push('/cinemas')
}

onMounted(loadUser)

defineExpose({ loadUser })
</script>

<template>
  <div class="app">
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
                <el-avatar :size="26" class="avatar">{{ (user.nickname || user.username || 'U').slice(0, 1) }}</el-avatar>
                {{ user.nickname || user.username }}
                <el-icon><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="router.push('/orders')">我的订单</el-dropdown-item>
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
