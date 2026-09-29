<template>
  <div class="order-page">
    <div class="detail-header">
      <el-button type="text" @click="goBack">
        <el-icon style="vertical-align: middle;"><ArrowLeft /></el-icon> 返回
      </el-button>
      <h2 class="detail-title">门票订单详情</h2>
    </div>

    <div class="detail-content">
      <el-form v-if="order" label-width="100px" label-position="left">
        <el-form-item label="订单编号：">
          <el-input :model-value="order.orderNo" readonly></el-input>
        </el-form-item>

        <el-form-item label="景点名称：">
          <el-input :model-value="order.scenicName" readonly></el-input>
        </el-form-item>

        <el-form-item label="游玩日期：">
          <el-input :model-value="order.visitDate" readonly></el-input>
        </el-form-item>

        <el-form-item label="票种信息：">
          <el-table :data="parseTickets(order.tickets)" border stripe class="ticket-table">
            <el-table-column prop="name" label="票种名称" width="200"></el-table-column>
            <el-table-column prop="type" label="类型" width="120">
              <template #default="scope">
                {{ { adult: '成人票', student: '学生票' }[scope.row.type] || scope.row.type }}
              </template>
            </el-table-column>
            <el-table-column prop="price" label="单价" width="120" align="right">
              <template #default="scope">¥{{ Number(scope.row.price || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="80" align="center"></el-table-column>
            <el-table-column label="小计" align="right">
              <template #default="scope">¥{{ (Number(scope.row.price || 0) * Number(scope.row.quantity || 0)).toFixed(2) }}</template>
            </el-table-column>
          </el-table>
        </el-form-item>

        <el-form-item label="总票数：">
          <el-input :model-value="order.totalQuantity" readonly></el-input>
        </el-form-item>

        <el-form-item label="订单总额：">
          <el-input :model-value="'¥' + Number(order.totalPrice || 0).toFixed(2)" readonly></el-input>
        </el-form-item>

        <el-form-item label="订单状态：">
          <el-tag :type="statusTagType" effect="plain">{{ statusText }}</el-tag>
        </el-form-item>

        <el-form-item label="下单时间：">
          <el-input :model-value="formatDateTime(order.orderTime)" readonly></el-input>
        </el-form-item>
      </el-form>

      <div v-else class="loading-container">
        <el-icon class="loading-icon" :size="30"><Loading /></el-icon>
        <div>正在加载订单详情...</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../api/request'
import { getUrlParam } from '../utils/index'

const router = useRouter()

const order = ref(null)
const orderId = ref(null)

onMounted(() => {
  orderId.value = getUrlParam('id')
  fetchOrderDetail()
})

async function fetchOrderDetail() {
  try {
    const res = await request.get(`/orders/${orderId.value}`)
    order.value = res.data
  } catch (error) {
    ElMessage.error('获取订单详情失败')
    console.error(error)
  }
}

function formatDateTime(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return `${date.getFullYear()}年${(date.getMonth() + 1).toString().padStart(2, '0')}月${date.getDate().toString().padStart(2, '0')}日 ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
}

function parseTickets(ticketString) {
  try {
    return JSON.parse(ticketString)
  } catch (e) {
    return []
  }
}

function goBack() {
  router.back()
}

const statusText = computed(() => {
  return {
    0: '待支付',
    1: '已完成',
    2: '已取消'
  }[order.value?.orderStatus] || '未知状态'
})

const statusTagType = computed(() => {
  return {
    0: 'warning',
    1: 'success',
    2: 'info'
  }[order.value?.orderStatus] || 'danger'
})
</script>

<style scoped>
.order-page {
  min-height: 100vh;
  background: #f5f5f5;
}
.detail-header {
  padding: 16px 24px;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  background: #fff;
}
.detail-title {
  margin: 0 0 0 20px;
  color: #303133;
  font-size: 18px;
}
.detail-content {
  padding: 24px;
  max-width: 800px;
  margin: 20px auto;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}
:deep(.el-form-item) {
  margin-bottom: 18px;
}
:deep(.el-form-item__label) {
  color: #606266;
  font-weight: 500;
}
:deep(.el-input.is-disabled .el-input__inner) {
  background-color: #f5f7fa;
  border-color: #e4e7ed;
  color: #606266;
  cursor: default;
}
.ticket-table {
  margin-top: 10px;
}
.ticket-table :deep(th) {
  background-color: #f8f9fa;
}
.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 0;
  color: #909399;
}
.loading-icon {
  margin-bottom: 16px;
}
</style>
