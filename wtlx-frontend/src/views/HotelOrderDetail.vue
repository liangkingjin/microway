<template>
  <div class="order-page">
    <div class="detail-header">
      <el-button type="text" @click="goBack">
        <el-icon style="vertical-align: middle;"><ArrowLeft /></el-icon> 返回
      </el-button>
      <h2 class="detail-title">酒店订单详情</h2>
    </div>

    <div class="detail-content">
      <el-form v-if="order" label-width="100px" label-position="left">
        <el-form-item label="房型：">
          <el-input :model-value="order.roomType" readonly></el-input>
        </el-form-item>
        <el-form-item label="房间标题：">
          <el-input :model-value="order.title" readonly></el-input>
        </el-form-item>
        <el-form-item label="房间大小：">
          <el-input :model-value="order.size" readonly></el-input>
        </el-form-item>
        <el-form-item label="床型信息：">
          <el-input :model-value="order.bedInfo" readonly></el-input>
        </el-form-item>
        <el-form-item label="容纳人数：">
          <el-input :model-value="order.capacity" readonly></el-input>
        </el-form-item>
        <el-form-item label="餐食信息：">
          <el-input :model-value="order.mealInfo" readonly></el-input>
        </el-form-item>
        <el-form-item label="取消政策：">
          <el-input :model-value="order.cancelInfo" readonly></el-input>
        </el-form-item>
        <el-form-item label="楼层：">
          <el-input :model-value="order.floor" readonly></el-input>
        </el-form-item>
        <el-form-item label="设施：">
          <el-input :model-value="parsedFacilitiesText" readonly></el-input>
        </el-form-item>

        <el-form-item label="订单编号：">
          <el-input :model-value="order.id" readonly></el-input>
        </el-form-item>
        <el-form-item label="订单金额：">
          <el-input :model-value="'¥' + Number(order.price || 0).toFixed(2)" readonly></el-input>
        </el-form-item>
        <el-form-item label="订单状态：">
          <el-tag :type="statusTagType" effect="plain">{{ getStatusText(order.status) }}</el-tag>
        </el-form-item>
        <el-form-item label="创建时间：">
          <el-input :model-value="formatDate(order.createTime)" readonly></el-input>
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../api/request'
import { getUrlParam } from '../utils/index'

const router = useRouter()

const order = ref({ price: 0 })
const parsedFacilities = ref({})
const parsedFacilitiesText = ref('')
const statusTagType = ref('')

onMounted(() => {
  getOrderDetail()
})

function getOrderDetail() {
  const id = getUrlParam('id')
  request.get(`/tb-hotel-order/detail/${id}`)
    .then(res => {
      const data = res.data
      order.value = data
      try {
        parsedFacilities.value = JSON.parse(data.facilities || '{}')
      } catch (e) {
        parsedFacilities.value = {}
      }
      parsedFacilitiesText.value = Object.entries(parsedFacilities.value)
        .map(([key, value]) => `${key}: ${value ? '有' : '无'}`)
        .join(', ')
      statusTagType.value = getStatusClass(data.status)
    })
    .catch(err => {
      ElMessage.error('获取订单详情失败')
      console.error(err)
    })
}

function getStatusClass(status) {
  const map = {
    0: 'warning',
    1: 'primary',
    2: 'success',
    3: 'info',
    4: 'success'
  }
  return map[status] || 'info'
}

function cancelOrder() {
  ElMessageBox.confirm('确定要取消该订单吗?', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    request.post(`/tb-hotel-order/cancel/${order.value.id}`)
      .then(() => {
        ElMessage.success('订单已取消')
        getOrderDetail()
      })
      .catch(err => {
        ElMessage.error('取消订单失败')
        console.error(err)
      })
  }).catch(() => {})
}

function goBack() {
  router.back()
}

function getStatusText(status) {
  const map = {
    0: '待支付',
    1: '已支付待确认',
    2: '已确认',
    3: '已取消',
    4: '已完成'
  }
  return map[status] || '未知状态'
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return `${date.getFullYear()}-${(date.getMonth() + 1).toString().padStart(2, '0')}-${date.getDate().toString().padStart(2, '0')} ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
}
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
