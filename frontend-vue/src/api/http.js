import axios from 'axios'
import { ElMessage } from 'element-plus'

export const TOKEN_KEY = 'mt_token'

/**
 * 角色单独存一份在本地：
 * 路由守卫是同步执行的，拿不到请求结果，只能读本地缓存做第一道快速判断。
 * 真正的权限校验永远在后端（AdminInterceptor），前端这个只负责「别让用户白跑一趟」。
 */
export const ROLE_KEY = 'mt_role'

const http = axios.create({
  baseURL: '/api',
  timeout: 10000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * 后端约定：只有 401 用真实 HTTP 状态码，其余业务错误都是 HTTP 200 + body.code。
 * 所以这里必须拆 body，不能只看 status。
 */
http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (!body || typeof body.code === 'undefined') {
      return body
    }
    if (body.code !== 200) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(body)
    }
    return body.data
  },
  (err) => {
    const status = err.response?.status
    const message = err.response?.data?.message
    if (status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      ElMessage.warning(message || '请先登录')
    } else {
      ElMessage.error(message || '网络异常，请检查后端是否启动')
    }
    return Promise.reject(err)
  }
)

export default http
