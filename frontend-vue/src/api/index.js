import http from './http'

/** 认证 */
export const authApi = {
  login: (data) => http.post('/auth/login', data),
  register: (data) => http.post('/auth/register', data),
  me: () => http.get('/user/me'),
  logout: () => http.post('/user/logout')
}

/** 当前用户资料（头像等） */
export const userApi = {
  me: () => http.get('/user/me'),
  /** 头像先调 uploadApi.image 传图拿 URL，再调这里落库 */
  updateAvatar: (avatarUrl) => http.put('/user/avatar', { avatarUrl })
}

/**
 * 图片上传（阿里云 OSS）
 * 返回 { url, name, size }，url 可直接放进 img 的 src。
 * 注意 timeout 要放宽：5MB 图片在慢网上传可能超过默认的 10 秒。
 */
export const uploadApi = {
  image: (file, dir = 'common') => {
    const form = new FormData()
    form.append('file', file)
    form.append('dir', dir)
    return http.post('/upload/image', form, { timeout: 60000 })
  }
}

/** 影院 */
export const cinemaApi = {
  list: () => http.get('/cinema/list')
}

/** 场次 */
export const screeningApi = {
  page: (params) => http.get('/screening/page', { params }),
  detail: (id) => http.get(`/screening/${id}`),
  /** 某场次某天的票种 / 票价 / 余票 */
  seats: (id, showDate) => http.get(`/screening/${id}/seats`, { params: { showDate } }),
  /** 某场次已放票的日期列表 */
  dates: (id) => http.get(`/screening/${id}/dates`)
}

/** 购票 */
export const ticketApi = {
  remain: (params) => http.get('/ticket/remain', { params }),
  buy: (data) => http.post('/ticket/buy', data)
}

/** 订单 */
export const orderApi = {
  my: () => http.get('/order/my'),
  detail: (orderNo) => http.get(`/order/${orderNo}`),
  cancel: (orderNo) => http.post(`/order/${orderNo}/cancel`),
  refund: (orderNo) => http.post(`/order/${orderNo}/refund`)
}

/** 支付回调（模拟第三方） */
export const payApi = {
  callback: (data) => http.post('/pay/callback', data)
}

/** 管理端 */
export const adminApi = {
  /** 探测当前账号是否真的有管理权限 */
  ping: () => http.get('/admin/ping'),

  cinema: {
    /** 含停用影院的完整列表 */
    list: (keyword) => http.get('/admin/cinema/list', { params: { keyword } }),
    create: (data) => http.post('/admin/cinema', data),
    update: (id, data) => http.put(`/admin/cinema/${id}`, data),
    remove: (id) => http.delete(`/admin/cinema/${id}`),
    /** 某影院下的影厅（新增场次时选厅） */
    halls: (id) => http.get(`/admin/cinema/${id}/halls`)
  },

  screening: {
    page: (params) => http.get('/admin/screening/page', { params }),
    create: (data) => http.post('/admin/screening', data),
    update: (id, data) => http.put(`/admin/screening/${id}`, data),
    remove: (id) => http.delete(`/admin/screening/${id}`)
  },

  stock: {
    /** 放票：DB 库存 + Redis 余票 + 布隆登记 一次完成 */
    publish: (data) => http.post('/admin/stock/publish', data),
    list: (params) => http.get('/admin/stock/list', { params }),
    updatePrice: (id, price) => http.put(`/admin/stock/${id}/price`, { price })
  }
}
