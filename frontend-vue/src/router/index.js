import { createRouter, createWebHistory } from 'vue-router'

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
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 影票` : '影票'
  if (to.meta.requiresAuth && !localStorage.getItem('mt_token')) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
