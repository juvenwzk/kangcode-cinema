import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ROLE_KEY, TOKEN_KEY } from '../api/http'

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('../views/Home.vue'),
    meta: { title: '首页' }
  },
  {
    path: '/cinemas',
    name: 'cinemas',
    component: () => import('../views/Cinemas.vue'),
    meta: { title: '影院' }
  },
  {
    path: '/cinemas/:id',
    name: 'cinema-screenings',
    component: () => import('../views/Screenings.vue'),
    meta: { title: '场次' }
  },
  {
    path: '/orders',
    name: 'orders',
    component: () => import('../views/Orders.vue'),
    meta: { title: '我的订单', requiresAuth: true }
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录' }
  },
  // ---------- 管理端：独立布局，不套用户端头部 ----------
  {
    path: '/admin',
    component: () => import('../views/admin/AdminLayout.vue'),
    meta: { requiresAdmin: true },
    children: [
      // 访问 /admin 直接落到影院管理
      { path: '', redirect: '/admin/cinemas' },
      {
        path: 'cinemas',
        name: 'admin-cinemas',
        component: () => import('../views/admin/AdminCinemas.vue'),
        meta: { title: '影院管理' }
      },
      {
        path: 'screenings',
        name: 'admin-screenings',
        component: () => import('../views/admin/AdminScreenings.vue'),
        meta: { title: '场次管理' }
      },
      {
        path: 'stock',
        name: 'admin-stock',
        component: () => import('../views/admin/AdminStock.vue'),
        meta: { title: '票池管理' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 影票` : '影票'

  const token = localStorage.getItem(TOKEN_KEY)

  if (to.meta.requiresAuth && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 管理端：没登录先去登录；登录了但不是 ADMIN，直接拦回用户端。
  // 注意这只是体验层的拦截，真正的权限在后端 AdminInterceptor，改 localStorage 也拿不到数据。
  if (to.meta.requiresAdmin) {
    if (!token) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    if (localStorage.getItem(ROLE_KEY) !== 'ADMIN') {
      ElMessage.error('需要管理员权限')
      return { path: '/cinemas' }
    }
  }

  return true
})

export default router
