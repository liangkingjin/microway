import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getTicketOrders, getHotelOrders, cancelOrder } from '../api/order'
import { ORDER_TYPE } from '../constants'

/**
 * 个人主页 · 订单区块状态与操作
 * 视图只负责渲染与交互编排，数据加载 / 取消 / 提示统一收在这里。
 */
export function useProfileOrders() {
  const ticketOrders = ref([])
  const hotelOrders = ref([])
  /** 当前内层页签：ticket | hotel */
  const activeTab = ref(ORDER_TYPE.TICKET)

  async function loadTicketOrders() {
    try {
      const res = await getTicketOrders()
      ticketOrders.value = res.data || []
    } catch (err) {
      console.error('获取门票订单失败', err)
      ElMessage.error('获取门票订单失败')
    }
  }

  async function loadHotelOrders() {
    try {
      const res = await getHotelOrders()
      hotelOrders.value = res.data || []
    } catch (err) {
      console.error('获取酒店订单失败', err)
      ElMessage.error('获取酒店订单失败')
    }
  }

  /** 按页签名加载 */
  function loadTab(tab) {
    return tab === ORDER_TYPE.HOTEL ? loadHotelOrders() : loadTicketOrders()
  }

  /** 重新加载当前页签 */
  function refresh() {
    return loadTab(activeTab.value)
  }

  /** 首屏同时拉取两类订单 */
  function loadAll() {
    return Promise.all([loadTicketOrders(), loadHotelOrders()])
  }

  /** 取消订单：接口 + 刷新 + 提示（确认弹窗属于交互层，留在视图里） */
  async function removeOrder(orderId, type) {
    try {
      await cancelOrder(orderId, type)
      ElMessage.success('订单已取消')
      return loadTab(type)
    } catch (err) {
      console.error('取消订单失败', err)
      ElMessage.error('取消订单失败：' + (err.message || err))
    }
  }

  return {
    ticketOrders,
    hotelOrders,
    activeTab,
    loadTicketOrders,
    loadHotelOrders,
    loadTab,
    refresh,
    loadAll,
    removeOrder
  }
}
