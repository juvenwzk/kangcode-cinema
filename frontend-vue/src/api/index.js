import http from './http'

/** 认证 */
export const authApi = {
  login: (data) => http.post('/auth/login', data),
  register: (data) => http.post('/auth/register', data),
  me: () => http.get('/user/me'),
  logout: () => http.post('/user/logout')
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
