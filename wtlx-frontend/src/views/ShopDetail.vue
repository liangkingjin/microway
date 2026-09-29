<template>
  <div class="page-container">
    <HeaderBar :title="''" />

    <div class="breadcrumb">
      <span @click="goBack">&lt; 返回列表</span>
      <span class="divider">|</span>
      <span class="type-tag">{{ shop.typeName }}</span>
    </div>

    <div class="shop-images-carousel">
      <el-carousel
        :interval="4000"
        indicator-position="outside"
        height="200px"
        :autoplay="true"
        trigger="click"
        arrow="hover"
        class="shop-carousel">
        <el-carousel-item v-for="(s, i) in shop.images || []" :key="i" class="carousel-item">
          <img :src="s" alt="" class="carousel-image">
          <div class="image-indicator">{{ i + 1 }}/{{ (shop.images || []).length }}</div>
        </el-carousel-item>
      </el-carousel>
    </div>

    <div class="shop-info-box">
      <div class="shop-header">
        <div class="shop-title">{{ shop.name }}</div>
        <div class="shop-tag">
          <el-tag type="warning" size="small">品牌好店</el-tag>
          <el-tag type="success" size="small">24H营业</el-tag>
        </div>
      </div>

      <div class="shop-rate-box">
        <div class="rate-right"></div>
      </div>

      <div class="shop-location">
        <div class="location-left">
          <el-icon style="color: #6b66f7;"><MapLocation /></el-icon>
          <div class="address-box">
            <span class="address-text">{{ shop.address }}</span>
          </div>
        </div>
        <div class="location-right" @click="callPhone">
          <el-icon style="color: #00a680;"><Phone /></el-icon>
        </div>
      </div>
    </div>

    <div class="shop-open-time">
      <div class="time-header">
        <el-icon style="color: #00a680;"><Clock /></el-icon>
        <span>营业时间</span>
      </div>
      <div class="time-content">
        <div class="time-text">{{ shop.openHours }}</div>
        <div class="time-tip">（具体时间以商户为准）</div>
      </div>
    </div>
    <div class="shop-divider"></div>
    <div class="shop-divider"></div>

    <div class="hotel-rooms" v-if="shop.typeId === 9">
      <div class="hotel-room-box" v-for="(room, index) in hotelRooms" :key="index">
        <div class="hotel-room-title" @click="viewRoomDetail(room)">
          <div>
            <span class="hotel-room-type">{{ room.roomType }}</span>
            {{ room.title }}
          </div>
          <el-icon style="color: #999;"><ArrowRight /></el-icon>
        </div>

        <div class="hotel-room-info">
          {{ room.size }} {{ room.bedInfo }} {{ room.capacity }}
        </div>

        <div class="hotel-room-tags">
          <div class="hotel-room-tag">{{ room.mealInfo }}</div>
          <div class="hotel-room-tag">{{ room.cancelInfo }}</div>
        </div>

        <div v-if="room.promotions && room.promotions.length > 0" style="display: flex; flex-wrap: wrap; padding: 0 12px 4px;">
          <div v-for="(promo, i) in room.promotions" :key="i" style="font-size: 12px; color: #999; margin-right: 8px;">
            <span v-if="promo.type === 'payment'">{{ promo.text }}</span>
            <span v-if="promo.type === 'confirm'">{{ promo.text }}</span>
            <span v-if="promo.type === 'gift'">{{ promo.text }}</span>
          </div>
        </div>

        <div class="hotel-room-price">
          <div class="price-info">
            <div class="original-price" v-if="room.originalPrice">¥{{ room.originalPrice }}</div>
            <div class="current-price">
              ¥{{ room.currentPrice }}
              <span class="discount-tag" v-if="room.discount">已优惠{{ room.discount }}</span>
            </div>
          </div>
          <button class="book-btn" @click="bookRoom(room)">订</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import HeaderBar from '../components/HeaderBar.vue'
import request from '../api/request'
import { getUrlParam } from '../utils/index'

const router = useRouter()

const shop = ref({})
const vouchers = ref([])
const hotelRooms = ref([])

onMounted(() => {
  const shopId = getUrlParam('id')
  queryShopById(shopId)
  queryVoucher(shopId)
})

function goBack() {
  router.back()
}

function callPhone() {
  const a = document.createElement('a')
  a.href = 'tel:15072819530'
  a.click()
}

function queryShopById(shopId) {
  request.get('/shop/' + shopId)
    .then(res => {
      const data = res.data
      data.images = data.images ? data.images.split(',') : []
      shop.value = data
      if (data.typeId === 9) {
        queryHotelRooms(shopId)
      }
    })
    .catch(err => {
      ElMessage.error(typeof err === 'string' ? err : '加载店铺信息失败')
    })
}

function queryVoucher(shopId) {
  request.get('/voucher/list/' + shopId)
    .then(res => {
      vouchers.value = res.data || []
    })
    .catch(err => {
      console.error('加载优惠券失败:', err)
    })
}

function queryHotelRooms(shopId) {
  request.get('/tb-hotel-room/' + shopId)
    .then(res => {
      const data = res.data
      if (Array.isArray(data)) {
        hotelRooms.value = data.map(room => {
          if (room.promotions && typeof room.promotions === 'string') {
            try {
              room.promotions = JSON.parse(room.promotions)
            } catch (e) {
              console.error('解析促销信息失败:', e)
              room.promotions = []
            }
          }
          if (room.images && typeof room.images === 'string') {
            const imageUrls = room.images.split(',')
            room.imagesList = imageUrls.map(url => {
              if (url.startsWith('http://') || url.startsWith('https://')) return url
              if (url.startsWith('/')) return url
              return url
            })
          }
          return room
        })
      } else {
        hotelRooms.value = []
      }
    })
    .catch(err => {
      ElMessage.error(typeof err === 'string' ? err : '加载房型失败')
    })
}

function viewRoomDetail(room) {
  const roomData = { ...room }
  if (room.imagesList && room.imagesList.length > 0) {
    roomData.processedImages = room.imagesList
  }
  localStorage.setItem('currentRoom', JSON.stringify(roomData))
  localStorage.setItem('hotelInfo', JSON.stringify({
    id: shop.value.id,
    name: shop.value.name,
    address: shop.value.address,
    images: shop.value.images
  }))
  router.push('/hotel-room-detail?shopId=' + shop.value.id + '&roomId=' + room.id)
}

function bookRoom(room) {
  if ((room.stock ?? 1) < 1) {
    ElMessage.error('房间已售罄，请选择其他房型')
    return
  }
  ElMessageBox.confirm('确认预订该房间?', '预订确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info',
    center: true
  }).then(() => {
    submitBooking(room)
  }).catch(() => {})
}

function submitBooking(room) {
  const loading = ElLoading.service({
    lock: true,
    text: '预订中...',
    background: 'rgba(0, 0, 0, 0.7)'
  })

  const bookingData = {
    shopId: shop.value.id,
    roomId: room.id,
    price: room.currentPrice,
    checkInDate: new Date().toISOString().split('T')[0],
    checkOutDate: new Date(Date.now() + 86400000).toISOString().split('T')[0]
  }

  request.post('/tb-hotel-order', bookingData)
    .then(() => {
      ElMessage({
        message: '预订成功！',
        type: 'success',
        duration: 3000
      })
      setTimeout(() => {
        goBack()
      }, 2000)
    })
    .catch(error => {
      ElMessage.error(typeof error === 'string' ? error : '预订失败')
      console.error('预订失败:', error)
    })
    .finally(() => {
      loading.close()
    })
}
</script>

<style scoped>
.page-container {
  background: #f5f5f5;
  min-height: 100vh;
}
.breadcrumb {
  padding: 12px 15px;
  background: #f8f9fa;
  font-size: 13px;
  color: #666;
}
.breadcrumb .divider {
  margin: 0 8px;
  color: #ddd;
}
.type-tag {
  background: #e8f4ff;
  color: #6b66f7;
  padding: 2px 6px;
  border-radius: 3px;
}
.shop-images-carousel {
  margin: 0 15px 15px;
}
.shop-carousel {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
.carousel-item {
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  background: #f5f5f5;
}
.carousel-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.image-indicator {
  position: absolute;
  bottom: 10px;
  right: 10px;
  background: rgba(0, 0, 0, 0.6);
  color: white;
  padding: 2px 8px;
  font-size: 12px;
  border-radius: 12px;
}
.shop-info-box {
  padding: 0 10px;
}
.shop-header {
  padding: 0 15px;
  margin-bottom: 12px;
}
.shop-title {
  font-size: 22px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
}
.shop-tag .el-tag {
  margin-right: 8px;
}
.shop-rate-box {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 15px;
  margin-bottom: 15px;
}
.shop-location {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 15px;
  background: #f8f9fa;
  border-radius: 8px;
  margin: 0 15px 15px;
}
.location-left {
  display: flex;
  align-items: center;
  flex: 1;
}
.location-left .el-icon {
  font-size: 20px;
  margin-right: 8px;
}
.address-box {
  display: flex;
  flex-direction: column;
}
.address-text {
  font-size: 14px;
  color: #333;
}
.location-right {
  cursor: pointer;
}
.location-right .el-icon {
  font-size: 24px;
}
.shop-open-time {
  padding: 12px 15px;
  background: white;
}
.time-header {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}
.time-header .el-icon {
  font-size: 16px;
  margin-right: 6px;
}
.time-content {
  display: flex;
  align-items: baseline;
}
.time-text {
  font-size: 14px;
  color: #333;
  margin-right: 8px;
}
.time-tip {
  font-size: 12px;
  color: #999;
}
.shop-divider {
  height: 10px;
  background-color: #f3f1f1;
}
.hotel-rooms {
  background-color: #fff;
  padding: 10px;
}
.hotel-room-box {
  background-color: #fff;
  border-radius: 8px;
  margin-bottom: 10px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}
.hotel-room-type {
  display: inline-block;
  background-color: #6b66f7;
  color: white;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 12px;
  margin-right: 5px;
}
.hotel-room-title {
  font-size: 15px;
  font-weight: bold;
  padding: 8px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
}
.hotel-room-info {
  padding: 0 12px 6px;
  font-size: 13px;
  color: #333;
}
.hotel-room-tags {
  display: flex;
  flex-wrap: wrap;
  padding: 0 12px 6px;
}
.hotel-room-tag {
  color: #666;
  font-size: 12px;
  margin-right: 10px;
}
.hotel-room-price {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 12px;
  border-top: 1px solid #f5f5f5;
}
.price-info {
  display: flex;
  flex-direction: column;
}
.original-price {
  font-size: 12px;
  color: #999;
  text-decoration: line-through;
}
.current-price {
  color: #ff6633;
  font-size: 20px;
  font-weight: bold;
}
.discount-tag {
  font-size: 12px;
  color: #ff6633;
  margin-left: 5px;
}
.book-btn {
  background-color: #ff6633;
  color: white;
  border: none;
  border-radius: 4px;
  padding: 6px 16px;
  font-size: 16px;
  font-weight: bold;
}
:deep(.el-carousel__indicators) {
  bottom: 15px;
}
:deep(.el-carousel__indicator--horizontal) {
  padding: 12px 4px;
}
:deep(.el-carousel__button) {
  width: 8px;
  height: 8px;
  background-color: rgba(255, 255, 255, 0.7);
  border-radius: 50%;
}
:deep(.el-carousel__indicator.is-active .el-carousel__button) {
  background-color: #fff;
}
</style>
