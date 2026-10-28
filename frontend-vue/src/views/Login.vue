<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api'
import { TOKEN_KEY, ROLE_KEY } from '../api/http'

const route = useRoute()
const router = useRouter()

const emit = defineEmits(['logined'])

const tab = ref('login')
const loading = ref(false)
const form = reactive({ username: '', password: '', nickname: '' })

function fill(type) {
  if (type === 'demo') {
    form.username = 'demo'
    form.password = '123456'
  } else {
    form.username = 'admin'
    form.password = 'admin123'
  }
}

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  loading.value = true
  try {
    const data =
      tab.value === 'login'
        ? await authApi.login({ username: form.username, password: form.password })
        : await authApi.register({
            username: form.username,
            password: form.password,
            nickname: form.nickname || form.username
          })

    localStorage.setItem(TOKEN_KEY, data.token)
    // 角色存本地，供路由守卫做第一道判断（真正的权限校验在后端 AdminInterceptor）
    localStorage.setItem(ROLE_KEY, data.role || 'USER')
    localStorage.setItem('mt_user', data.nickname || data.username || '')
    emit('logined')
    ElMessage.success(tab.value === 'login' ? '登录成功' : '注册成功，已自动登录')
    // 管理员默认进后台；但如果是从别的页面被踢到登录页的（带 redirect），优先回原页面
    const fallback = data.role === 'ADMIN' ? '/admin/cinemas' : '/cinemas'
    router.push(route.query.redirect || fallback)
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="container login-wrap">
    <div class="card login-card">
      <div class="brand">
        <span class="logo-mark">影</span>
        <span class="brand-text">影票</span>
      </div>

      <el-tabs v-model="tab" stretch>
        <el-tab-pane label="登录" name="login" />
        <el-tab-pane label="注册" name="register" />
      </el-tabs>

      <el-form label-position="top" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" size="large" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-form-item v-if="tab === 'register'" label="昵称（选填）">
          <el-input v-model="form.nickname" placeholder="不填就用用户名" size="large" />
        </el-form-item>
      </el-form>

      <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="submit">
        {{ tab === 'login' ? '登录' : '注册并登录' }}
      </el-button>

      <div class="quick">
        <span class="muted">演示账号：</span>
        <el-button link type="primary" @click="fill('demo')">demo / 123456</el-button>
        <el-button link type="primary" @click="fill('admin')">admin / admin123</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-wrap {
  display: flex;
  justify-content: center;
  padding-top: 48px;
}

.login-card {
  width: 380px;
  padding: 28px 30px 24px;
}

.brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 18px;
}

.logo-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: var(--el-color-primary);
  color: #fff;
  font-weight: 700;
}

.brand-text {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 1px;
}

.quick {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 14px;
}
</style>
