<template>
  <div class="page-container">
    <HeaderBar title="打卡发现" :show-back="true" />

    <div class="container" :style="backgroundStyle">
      <div class="check-in-info">
        <div class="check-in-title"></div>
        <div class="check-in-days">
          <span class="day-block">{{ padZero(consecutiveDays, 4) }}</span>
          <span class="day-label">天</span>
        </div>
      </div>

      <div class="current-city">
        <div class="city-name">{{ currentCity.name }}</div>
      </div>

      <div class="check-in-btn-container">
        <el-button
          type="primary"
          :disabled="hasCheckedIn"
          @click="handleCheckIn"
          :loading="checkingIn"
          class="check-in-btn"
        >
          {{ hasCheckedIn ? '今日已打卡' : '立即打卡' }}
        </el-button>
        <div class="city-en">
          1天后解锁<span class="unlock-city-name">{{ nextUnlockCity.name }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import HeaderBar from '../components/HeaderBar.vue'
import request from '../api/request'

const consecutiveDays = ref(0)
const hasCheckedIn = ref(false)
const checkingIn = ref(false)
const currentCity = ref({ name: '' })
const nextUnlockCity = ref({ name: '' })
const backgroundImage = ref('')

const backgroundStyle = computed(() => ({
  'background-image': `url(${backgroundImage.value})`,
  'background-size': 'cover',
  'background-position': 'center',
  'background-repeat': 'no-repeat'
}))

function padZero(num, length) {
  return num.toString().padStart(length, '0')
}

function handleCheckIn() {
  if (hasCheckedIn.value) {
    ElMessage({
      message: '今日已打卡，无需重复打卡',
      type: 'warning'
    })
    return
  }

  checkingIn.value = true
  request.get('/user-check-ins/check-in')
    .then(() => {
      checkingIn.value = false
      hasCheckedIn.value = true
      fetchCheckInData()
      ElMessage({
        message: '打卡成功！连续打卡天数+1',
        type: 'success'
      })
    })
    .catch(error => {
      checkingIn.value = false
      console.error('打卡失败:', error)
      ElMessage({
        message: '打卡失败，请重试',
        type: 'error'
      })
    })
}

function fetchCheckInData() {
  request.get('/user-check-ins/check-in-data')
    .then(res => {
      const data = res.data || {}
      consecutiveDays.value = data.consecutiveDays
      hasCheckedIn.value = data.hasCheckedIn
      backgroundImage.value = data.cityImage
      currentCity.value.name = data.cityName
      nextUnlockCity.value.name = data.nextCityName
    })
    .catch(error => {
      console.error('获取打卡数据失败:', error)
      ElMessage({
        message: '获取打卡数据失败，请重试',
        type: 'error'
      })
    })
}

onMounted(() => {
  fetchCheckInData()
})
</script>

<style scoped>
.page-container {
  background: #f8f8f8;
  min-height: 100vh;
}
.container {
  max-width: 800px;
  height: 90vh;
  display: flex;
  flex-direction: column;
}
.check-in-info {
  text-align: center;
  margin-top: 30px;
}
.check-in-title {
  font-size: 16px;
  color: #333;
  margin-bottom: 10px;
}
.check-in-days {
  display: flex;
  justify-content: center;
  align-items: center;
}
.day-block {
  font-size: 48px;
  font-weight: bold;
  background-color: white;
  padding: 5px 10px;
  margin: 0 5px;
  border-radius: 5px;
  min-width: 40px;
  text-align: center;
}
.day-label {
  font-size: 16px;
  color: #333;
  margin-left: 10px;
}
.current-city {
  text-align: center;
}
.city-name {
  font-size: 24px;
  font-weight: bold;
  color: #333;
  margin-bottom: 5px;
}
.city-en {
  font-size: 16px;
  color: #3d2e2e;
}
.unlock-city-name {
  color: #ff6600;
  font-weight: bold;
}
.check-in-btn-container {
  text-align: center;
  margin-top: 100%;
  margin-bottom: 40px;
}
.check-in-btn {
  width: 200px;
  padding: 12px 0;
  font-size: 16px;
  background-color: #409EFF;
  border: none;
  border-radius: 5px;
  color: white;
  cursor: pointer;
  outline: none;
}
.check-in-btn:disabled {
  background-color: #909399;
  cursor: not-allowed;
}
</style>
