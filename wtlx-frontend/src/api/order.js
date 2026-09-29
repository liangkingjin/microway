import request from './request'
import { ORDER_TYPE } from '../constants'

/**
 * 订单接口（门票 / 酒店）
 * 原 Info.vue 内联的 axios 调用统一收敛到本文件
 */

/** 我的门票订单（GET /orders/of/user） */
export function getTicketOrders() {
  return request.get('/orders/of/user')
}

/** 我的酒店订单（GET /tb-hotel-order/of/user） */
export function getHotelOrders(page = 1, size = 10) {
  return request.get('/tb-hotel-order/of/user', { params: { page, size } })
}

/** 取消订单（POST /ticket-order/cancel/:id 或 /tb-hotel-order/cancel/:id） */
export function cancelOrder(orderId, type = ORDER_TYPE.TICKET) {
  const url =
    type === ORDER_TYPE.TICKET
      ? `/ticket-order/cancel/${orderId}`
      : `/tb-hotel-order/cancel/${orderId}`
  return request.post(url)
}
