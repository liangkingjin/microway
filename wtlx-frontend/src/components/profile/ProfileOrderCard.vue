<template>
  <div class="wt-card profile-order-card">
    <div class="profile-order-card__header">
      <span class="profile-order-card__no wt-ellipsis">{{ orderNoText }}</span>
      <span class="profile-order-card__status" :class="statusClass">{{ statusText }}</span>
    </div>

    <div class="profile-order-card__body">
      <div class="profile-order-card__title wt-ellipsis">{{ title }}</div>
      <div v-if="subtitle" class="profile-order-card__subtitle">{{ subtitle }}</div>

      <div class="profile-order-card__details">
        <div
          v-for="(item, index) in detailItems"
          :key="index"
          class="profile-order-card__detail"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span class="profile-order-card__detail-text">
            <template v-if="item.label">{{ item.label }}：</template>
            <template v-if="item.chips">
              <span
                v-for="(chip, i) in item.chips"
                :key="i"
                class="wt-chip"
              >{{ chip.name }}×{{ chip.quantity }}</span>
            </template>
            <template v-else>{{ item.value }}</template>
          </span>
        </div>
      </div>
    </div>

    <div class="profile-order-card__footer">
      <el-button size="small" type="primary" plain @click="emit('view', order)">
        查看详情
      </el-button>
      <el-button
        v-if="canCancel"
        size="small"
        type="danger"
        plain
        @click="emit('cancel', order)"
      >
        取消订单
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Calendar, Tickets, User, Wallet } from '@element-plus/icons-vue'
import { ORDER_TYPE, ORDER_STATUS } from '../../constants'

/**
 * 订单卡片：门票 / 酒店共用
 * 差异（字段、票种 chips、价格文案）全部收敛到 detailItems 计算属性里，
 * 模板保持一份，不再按类型写两套结构。
 */
const props = defineProps({
  order: { type: Object, required: true },
  type: { type: String, default: ORDER_TYPE.TICKET },
  /** 副标题（酒店订单的业务类型），为空则不渲染 */
  subtitle: { type: String, default: '' }
})
const emit = defineEmits(['view', 'cancel'])

const isTicket = computed(() => props.type === ORDER_TYPE.TICKET)

const STATUS_TEXT = {
  [ORDER_STATUS.PENDING]: '待支付',
  [ORDER_STATUS.COMPLETED]: '已完成',
  [ORDER_STATUS.CANCELLED]: '已取消'
}
const STATUS_CLASS = {
  [ORDER_STATUS.PENDING]: 'is-pending',
  [ORDER_STATUS.COMPLETED]: 'is-completed',
  [ORDER_STATUS.CANCELLED]: 'is-cancelled'
}

/** 门票取 orderStatus，酒店取 status */
const status = computed(() =>
  isTicket.value ? props.order.orderStatus : props.order.status
)
/** 酒店订单的状态文案由后端直接下发 statusText */
const statusText = computed(() =>
  isTicket.value
    ? STATUS_TEXT[status.value] || '未知状态'
    : props.order.statusText
)
const statusClass = computed(() => STATUS_CLASS[status.value] || '')
const canCancel = computed(() => status.value === ORDER_STATUS.PENDING)

const orderNoText = computed(() =>
  `订单号: ${isTicket.value ? props.order.orderNo : props.order.id}`
)
const title = computed(() =>
  isTicket.value ? props.order.scenicName : props.order.hotelName
)

/** 票种字符串是 JSON，解析失败降级为空数组 */
const ticketItems = computed(() => {
  try {
    return JSON.parse(props.order.tickets) || []
  } catch (e) {
    return []
  }
})

const detailItems = computed(() => {
  if (isTicket.value) {
    return [
      { icon: Calendar, label: '游玩日期', value: props.order.visitDate },
      { icon: Tickets, label: '票种', chips: ticketItems.value },
      { icon: User, label: '总票数', value: props.order.totalQuantity },
      {
        icon: Wallet,
        label: '总价',
        value: '¥' + (props.order.totalPrice || 0).toFixed(2)
      }
    ]
  }
  return [
    { icon: Calendar, label: '预定时间', value: props.order.createTime },
    { icon: Wallet, label: '', value: '¥' + (props.order.price || 0).toFixed(2) }
  ]
})
</script>

<style scoped>
.profile-order-card {
  overflow: hidden;
}
.profile-order-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wt-space-2);
  padding: var(--wt-space-3) var(--wt-space-4);
  border-bottom: 1px solid var(--wt-border);
}
.profile-order-card__no {
  flex: 1 1 auto;
  font-size: var(--wt-fs-sm);
  color: var(--wt-text-3);
}
.profile-order-card__status {
  flex: 0 0 auto;
  padding: 2px var(--wt-space-2);
  border-radius: var(--wt-radius-sm);
  font-size: var(--wt-fs-xs);
  font-weight: 600;
  line-height: 18px;
}
.profile-order-card__status.is-pending {
  color: var(--wt-color-warning);
  background: rgba(230, 162, 60, 0.14);
}
.profile-order-card__status.is-completed {
  color: var(--wt-color-success);
  background: rgba(103, 194, 58, 0.14);
}
.profile-order-card__status.is-cancelled {
  color: var(--wt-color-danger);
  background: rgba(245, 108, 108, 0.14);
}
.profile-order-card__body {
  padding: var(--wt-space-3) var(--wt-space-4);
}
.profile-order-card__title {
  margin: 0 0 var(--wt-space-1);
  font-size: var(--wt-fs-lg);
  font-weight: 600;
  line-height: 1.45;
  color: var(--wt-text-1);
}
.profile-order-card__subtitle {
  margin: 0 0 var(--wt-space-2);
  font-size: var(--wt-fs-sm);
  color: var(--wt-text-2);
}
.profile-order-card__details {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wt-space-2) var(--wt-space-4);
}
.profile-order-card__detail {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--wt-fs-sm);
  line-height: 1.6;
  color: var(--wt-text-3);
}
.profile-order-card__detail .el-icon {
  flex: 0 0 auto;
  font-size: var(--wt-fs-md);
  color: var(--wt-text-4);
}
.profile-order-card__detail-text {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}
.profile-order-card__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--wt-space-2);
  padding: var(--wt-space-3) var(--wt-space-4);
  border-top: 1px solid var(--wt-border);
  background: var(--wt-bg-muted);
}
/* el-button 自带 margin-left，间距统一交给容器 gap */
.profile-order-card__footer .el-button {
  margin-left: 0;
}

@media (min-width: 768px) {
  .profile-order-card__details {
    gap: var(--wt-space-3) var(--wt-space-6);
  }
}
</style>
