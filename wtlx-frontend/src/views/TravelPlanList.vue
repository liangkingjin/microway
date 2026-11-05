<template>
  <div class="travel-list-page">
    <HeaderBar title="我的旅行计划" :show-back="true" />

    <div class="container">
      <!-- 空状态 -->
      <div v-if="plans.length === 0" class="empty-state">
        <img src="/imgs/types/空空如也.png" alt="空行程" class="empty-image">
        <div class="empty-desc">还没有旅行计划，快来添加你的旅程吧！</div>
        <el-button type="primary" class="add-plan-btn" @click="showCreateDialog">
          <el-icon><Plus /></el-icon> 创建旅行计划
        </el-button>
      </div>

      <template v-else>
        <div
          v-for="(plan, index) in plans"
          :key="index"
          class="plan-card"
          @click="viewDetail(plan.id)"
          @touchstart="handlePressStart(plan.id, $event)"
          @touchend="handlePressEnd"
          @touchmove="handlePressEnd"
        >
          <div class="plan-content">
            <div class="plan-header">
              <el-icon class="location-icon"><Location /></el-icon>
              <div>
                <div class="plan-title">{{ plan.title }}</div>
                <div class="plan-date">{{ plan.startDate }} — {{ plan.endDate }}</div>
                <div class="plan-amount" v-if="plan.totalAmount">¥{{ formatAmount(plan.totalAmount) }}</div>
              </div>
            </div>
          </div>

          <div class="trip-badge">
            <div class="trip-badge-title">THE TRIP</div>
            <div class="trip-badge-title">MUST GO ON</div>
            <div class="trip-badge-divider"></div>
            <div class="trip-badge-content">
              时长:
              <div class="trip-duration">{{ plan.duration }}</div>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 悬浮添加按钮 -->
    <div class="add-button" @click="showCreateDialog">
      <el-icon><Plus /></el-icon>
    </div>

    <!-- 创建计划对话框 -->
    <el-dialog
      title="为这次旅行取一个名字吧"
      v-model="dialogVisible"
      width="90%"
      :show-close="false">
      <div class="dialog-content">
        <div class="input-container">
          <div class="input-label">
            旅行计划名称
            <span class="character-count">{{ planName.length }}/25</span>
          </div>
          <el-input
            v-model="planName"
            placeholder="我的旅行计划"
            maxlength="25"
            show-word-limit>
          </el-input>
        </div>

        <div class="date-picker-container">
          <div class="input-label">选择行程时间</div>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :disabled-date="disabledDate"
            style="width: 100%">
          </el-date-picker>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="createPlan" style="width: 100%">确定</el-button>
      </template>
    </el-dialog>

    <!-- 删除确认对话框 -->
    <el-dialog
      title="确认删除"
      v-model="deleteDialogVisible"
      width="90%">
      <span>确定要删除这个旅行计划吗？</span>
      <template #footer>
        <el-button @click="deleteDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="deletePlan">确定删除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import HeaderBar from '../components/HeaderBar.vue'
import request from '../api/request'

const router = useRouter()

const plans = ref([])
const dialogVisible = ref(false)
const planName = ref('')
const dateRange = ref('')
const deleteDialogVisible = ref(false)
const currentPlanId = ref(null)
let pressTimer = null

function disabledDate(time) {
  return time.getTime() < Date.now() - 8.64e7
}

// 页面加载时获取计划列表
request.get('/travel-plans')
  .then(res => {
    const data = res.data
    if (data) {
      plans.value = data.map(plan => ({
        id: plan.id,
        title: plan.title,
        startDate: formatDate(new Date(plan.startDate)),
        endDate: formatDate(new Date(plan.endDate)),
        duration: plan.duration,
        totalAmount: plan.totalAmount,
        status: plan.status
      }))
    }
  })
  .catch(error => {
    ElMessage.error('获取计划列表失败')
    console.error('获取计划列表失败:', error)
  })

// 长按开始
function handlePressStart(planId) {
  pressTimer = setTimeout(() => {
    currentPlanId.value = planId
    deleteDialogVisible.value = true
  }, 800) // 800ms 长按触发
}

// 长按取消
function handlePressEnd() {
  clearTimeout(pressTimer)
}

// 删除计划
async function deletePlan() {
  try {
    await request.delete(`/travel-plans/${currentPlanId.value}`)
    ElMessage.success('删除成功')
    deleteDialogVisible.value = false
    loadPlans()
  } catch (error) {
    ElMessage.error('删除失败')
    console.error('删除计划失败:', error)
  }
}

// 创建新计划
async function createPlan() {
  if (!planName.value.trim()) {
    ElMessage.warning('请输入旅行计划名称')
    return
  }
  if (!dateRange.value || !dateRange.value[0] || !dateRange.value[1]) {
    ElMessage.warning('请选择行程时间')
    return
  }

  const days = Math.ceil((dateRange.value[1] - dateRange.value[0]) / (1000 * 60 * 60 * 24)) + 1
  const nights = days - 1

  try {
    const newPlan = {
      title: planName.value,
      startDate: dateRange.value[0].toISOString().split('T')[0],
      endDate: dateRange.value[1].toISOString().split('T')[0],
      duration: `${days}天${nights}晚`,
      totalAmount: null,
      status: '待出发'
    }

    await request.post('/travel-plans', newPlan)
    ElMessage.success('创建成功')
    dialogVisible.value = false
    loadPlans()
  } catch (error) {
    ElMessage.error('创建失败')
    console.error('创建计划失败:', error)
  }
}

function loadPlans() {
  request.get('/travel-plans')
    .then(res => {
      const data = res.data
      if (data) {
        plans.value = data.map(plan => ({
          id: plan.id,
          title: plan.title,
          startDate: formatDate(new Date(plan.startDate)),
          endDate: formatDate(new Date(plan.endDate)),
          duration: plan.duration,
          totalAmount: plan.totalAmount,
          status: plan.status
        }))
      }
    })
    .catch(error => {
      ElMessage.error('获取计划列表失败')
      console.error('获取计划列表失败:', error)
    })
}

function viewDetail(planId) {
  const plan = plans.value.find(p => p.id === planId)
  if (plan) {
    localStorage.setItem('currentPlan', JSON.stringify(plan))
  }
  router.push('/travel-plan-detail?id=' + planId)
}

function showCreateDialog() {
  dialogVisible.value = true
  planName.value = ''
  dateRange.value = ''
}

function formatDate(date) {
  const d = new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}.${month}.${day}`
}

function formatAmount(amount) {
  if (!amount && amount !== 0) return '0.00'
  return amount.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}
</script>

<style scoped>
/* 样式合并自原 旅游清单.html <style> 内联块；body 全局背景重置不迁移 */
.travel-list-page {
  background-color: #e6f7ff;
  min-height: 100vh;
}
.container {
  padding: 12px;
}
.plan-card {
  background: white;
  border-radius: 16px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  position: relative;
  display: flex;
  justify-content: space-between;
  cursor: pointer;
  transition: all 0.3s ease;
}
.plan-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}
.plan-content {
  flex: 1;
  padding-right: 12px;
}
.plan-header {
  display: flex;
  align-items: flex-start;
  margin-bottom: 12px;
}
.location-icon {
  color: #40a9ff;
  font-size: 20px;
  margin-right: 8px;
  margin-top: 2px;
}
.plan-title {
  font-size: 18px;
  font-weight: 600;
  color: #262626;
  margin-bottom: 6px;
  line-height: 1.4;
}
.plan-date {
  color: #595959;
  font-size: 14px;
  margin-bottom: 8px;
  line-height: 1.4;
}
.plan-amount {
  color: #f56c6c;
  font-size: 14px;
  font-weight: 500;
  margin-top: 6px;
}
.trip-badge {
  background: #f0f7ff;
  padding: 12px;
  border-radius: 10px;
  width: 100px;
  text-align: center;
  margin-left: 12px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.trip-badge-title {
  font-size: 11px;
  color: #262626;
  font-weight: 600;
  line-height: 1.3;
  letter-spacing: 0.5px;
}
.trip-badge-divider {
  width: 100%;
  height: 1px;
  background: #d9d9d9;
  margin: 8px 0;
}
.trip-badge-content {
  font-size: 12px;
  color: #595959;
}
.trip-duration {
  font-size: 14px;
  font-weight: 600;
  color: #262626;
  margin-top: 4px;
}
.add-button {
  position: fixed;
  right: 16px;
  bottom: 16px;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background-color: #1890ff;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.15);
  cursor: pointer;
  z-index: 1000;
  transition: all 0.3s ease;
}
.add-button:hover {
  background-color: #40a9ff;
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(24, 144, 255, 0.2);
}
.dialog-content {
  padding: 16px;
}
.input-container {
  margin-bottom: 16px;
}
.input-label {
  display: block;
  margin-bottom: 8px;
  color: #262626;
  font-size: 14px;
  font-weight: 500;
}
.character-count {
  float: right;
  color: #8c8c8c;
  font-size: 12px;
  font-weight: normal;
}
.date-picker-container {
  margin-top: 16px;
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  text-align: center;
  background: white;
  border-radius: 16px;
  margin: 20px 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.empty-image {
  width: 180px;
  height: 180px;
  margin-bottom: 20px;
  object-fit: contain;
}
.empty-desc {
  font-size: 14px;
  color: #8c8c8c;
  margin-bottom: 24px;
  line-height: 1.5;
}
.add-plan-btn {
  padding: 10px 20px;
  font-size: 14px;
}
</style>
