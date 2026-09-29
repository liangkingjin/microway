/**
 * 全局常量（原散落在各页面模板 / 脚本中的硬编码值统一收口）
 */

/** 未上传头像时的默认头像 */
export const DEFAULT_AVATAR = '/imgs/icons/default-icon.jpg'

/** 订单类型：门票 / 酒店 */
export const ORDER_TYPE = {
  TICKET: 'ticket',
  HOTEL: 'hotel'
}

/** 订单状态码 → 文案 / 样式类 */
export const ORDER_STATUS = {
  PENDING: 0, // 待支付
  COMPLETED: 1, // 已完成
  CANCELLED: 2 // 已取消
}
