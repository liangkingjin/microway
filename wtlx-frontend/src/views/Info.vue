<template>
  <div class="info-page">
    <div class="basic">
      <div class="basic-icon">
        <img :src="user.icon || '/imgs/icons/default-icon.jpg'" alt="">
      </div>
      <div class="basic-info">
        <div class="name">{{ user.nickName }}</div>
        <div class="edit-btn" @click="toEdit">编辑资料</div>
      </div>
      <div class="logout-btn" @click="logout">退出登录</div>
    </div>
    <div class="introduce">
      <span v-if="user.introduce">{{ user.introduce }} <el-icon class="edit-icon"><EditPen /></el-icon></span>
      <span v-else @click="toEditIntroduce">添加个人简介，让大家更好的认识你 <el-icon class="edit-icon"><EditPen /></el-icon></span>
    </div>
    <div class="content">
      <el-tabs v-model="activeName" @tab-click="handleClick">
        <el-tab-pane label="订单" name="1">
          <el-tabs v-model="orderActiveTab" @tab-click="handleOrderTabClick" type="card">
            <el-tab-pane label="门票订单" name="ticket">
              <div v-if="ticketOrders.length > 0">
                <div v-for="order in ticketOrders" :key="order.id" class="order-item">
                  <div class="order-header">
                    <span class="order-id">订单号: {{ order.orderNo }}</span>
                    <span class="order-status" :class="getStatusClass(order.orderStatus)">
                      {{ getStatusText(order.orderStatus) }}
                    </span>
                  </div>
                  <div class="order-content">
                    <div class="order-info">
                      <div class="order-title">{{ order.scenicName }}</div>
                      <div class="order-detail">
                        <div class="detail-item">
                          <el-icon><Calendar /></el-icon>
                          <span>游玩日期：{{ order.visitDate }}</span>
                        </div>
                        <div class="detail-item">
                          <el-icon><Tickets /></el-icon>
                          <span>票种：
                            <span v-for="(ticket, index) in parseTickets(order.tickets)" :key="index">
                              {{ ticket.name }}×{{ ticket.quantity }}
                            </span>
                          </span>
                        </div>
                        <div class="detail-item">
                          <el-icon><User /></el-icon>
                          <span>总票数：{{ order.totalQuantity }}</span>
                        </div>
                        <div class="detail-item">
                          <el-icon><Wallet /></el-icon>
                          <span>总价：¥{{ (order.totalPrice || 0).toFixed(2) }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                  <div class="order-footer">
                    <el-button size="small" type="primary" plain @click="viewOrderDetail(order, 'ticket')">查看详情</el-button>
                    <el-button size="small" type="danger" plain v-if="order.status === 0" @click="cancelOrder(order.id, 'ticket')">取消订单</el-button>
                  </div>
                </div>
              </div>
              <div v-else class="empty-orders">
                <el-icon style="font-size:48px;color:#c0c4cc;"><Tickets /></el-icon>
                <p>暂无门票订单</p>
              </div>
            </el-tab-pane>
            <el-tab-pane label="酒店订单" name="hotel">
              <div v-if="hotelOrders.length > 0">
                <div v-for="order in hotelOrders" :key="order.id" class="order-item">
                  <div class="order-header">
                    <span class="order-id">订单号: {{ order.id }}</span>
                    <span class="order-status" :class="getStatusClass(order.status)">{{ order.statusText }}</span>
                  </div>
                  <div class="order-content">
                    <div class="order-info">
                      <div class="order-title">{{ order.hotelName }}</div>
                      <div class="order-subtitle">{{ orderType }}</div>
                      <div class="order-detail">
                        <div class="detail-item">
                          <el-icon><Calendar /></el-icon>
                          <span>预定时间：{{ order.createTime }}</span>
                        </div>
                        <div class="detail-item">
                          <el-icon><Wallet /></el-icon>
                          <span>¥{{ (order.price || 0).toFixed(2) }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                  <div class="order-footer">
                    <el-button size="small" type="primary" plain @click="viewOrderDetail(order, 'hotel')">查看详情</el-button>
                    <el-button size="small" type="danger" plain v-if="order.status === 0" @click="cancelOrder(order.id, 'hotel')">取消订单</el-button>
                  </div>
                </div>
              </div>
              <div v-else class="empty-orders">
                <el-icon style="font-size:48px;color:#c0c4cc;"><House /></el-icon>
                <p>暂无酒店订单</p>
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-tab-pane>

        <el-tab-pane label="游记" name="2">
          <div v-for="b in blogs" :key="b.id" class="blog-item"
               @touchstart="startLongPress(b)"
               @touchend="cancelLongPress"
               @touchmove="cancelLongPress"
               @mousedown="startLongPress(b)"
               @mouseup="cancelLongPress"
               @mouseleave="cancelLongPress">
            <div class="blog-img"><img :src="b.images.split(',')[0]" alt=""></div>
            <div class="blog-info">
              <div class="blog-title">{{ b.title }}</div>
              <div class="blog-liked"><img src="/imgs/thumbup.png" alt=""> {{ b.liked }}</div>
              <div class="blog-comments"><el-icon><ChatDotRound /></el-icon> {{ b.comments }}</div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
    <FootBar :active-btn="4" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import FootBar from '../components/FootBar.vue'
import request from '../api/request'

const router = useRouter()

const user = ref('')
const activeName = ref('1')
const orderActiveTab = ref('ticket')
const info = ref({})
const blogs = ref([])
const ticketOrders = ref([])
const hotelOrders = ref([])
const params = reactive({ minTime: 0, offset: 0 })
const orderType = ref('')
let longPressTimer = null
const longPressDelay = 800

onMounted(() => {
  queryTicketOrders()
  queryHotelOrders()
  queryUser()
})

function queryBlogs() {
  request.get('/blog/of/me')
    .then(res => { blogs.value = res.data })
    .catch(err => ElMessage.error(err))
}

function toEditIntroduce() {
  router.push('/info-edit?edit=introduce')
}

function queryUser() {
  request.get('/user/me')
    .then(res => {
      const data = res.data
      user.value = {
        ...data,
        icon: data.icon ? '' + data.icon : '/imgs/icons/default-icon.jpg'
      }
      queryBlogs()
    })
    .catch(() => {
      router.push('/login')
    })
}

function queryUserInfo() {
  request.get('/user/info/' + user.value.id)
    .then(res => {
      if (!res.data) return
      info.value = res.data
      sessionStorage.setItem('userInfo', JSON.stringify(res.data))
    })
    .catch(err => ElMessage.error(err))
}

function toEdit() {
  router.push('/info-edit')
}

function logout() {
  request.post('/user/logout')
    .then(() => {
      sessionStorage.removeItem('token')
      router.push('/')
    })
    .catch(err => ElMessage.error(err))
}

function handleClick(tab) {
  if (tab.name === '4') {
    queryBlogsOfFollow(true)
  } else if (tab.name === '2') {
    loadOrders()
  }
}

function handleOrderTabClick(tab) {
  if (tab.name === 'ticket') {
    queryTicketOrders()
  } else if (tab.name === 'hotel') {
    queryHotelOrders()
  }
}

function loadOrders() {
  if (orderActiveTab.value === 'ticket') {
    queryTicketOrders()
  } else {
    queryHotelOrders()
  }
}

function queryTicketOrders() {
  request.get('/orders/of/user')
    .then(res => {
      ticketOrders.value = res.data || []
    })
    .catch(err => {
      console.error('获取门票订单失败', err)
      ElMessage.error('获取门票订单失败')
    })
}

function queryHotelOrders() {
  request.get('/tb-hotel-order/of/user', { params: { page: 1, size: 10 } })
    .then(res => {
      hotelOrders.value = res.data
    })
    .catch(err => {
      console.error('获取酒店订单失败', err)
      ElMessage.error('获取酒店订单失败')
    })
}

function getStatusText(status) {
  switch (status) {
    case 0: return '待支付'
    case 1: return '已完成'
    case 2: return '已取消'
    default: return '未知状态'
  }
}

function getStatusClass(status) {
  switch (status) {
    case 0: return 'status-pending'
    case 1: return 'status-completed'
    case 2: return 'status-cancelled'
    default: return ''
  }
}

function parseTickets(ticketString) {
  try {
    return JSON.parse(ticketString)
  } catch (e) {
    return []
  }
}

function viewOrderDetail(order, type) {
  if (type === 'ticket') {
    router.push('/ticket-order-detail?id=' + order.id)
  } else {
    router.push('/hotel-order-detail?id=' + order.id)
  }
}

function cancelOrder(orderId, type) {
  ElMessageBox.confirm('确定要取消该订单吗?', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    const url = type === 'ticket' ? `/ticket-order/cancel/${orderId}` : `/tb-hotel-order/cancel/${orderId}`
    request.post(url)
      .then(() => {
        ElMessage.success('订单已取消')
        if (type === 'ticket') {
          queryTicketOrders()
        } else {
          queryHotelOrders()
        }
      })
      .catch(err => {
        console.error('取消订单失败', err)
        ElMessage.error('取消订单失败：' + (err.message || err))
      })
  }).catch(() => {})
}

function startLongPress(blog) {
  longPressTimer = setTimeout(() => {
    showDeleteConfirm(blog)
  }, longPressDelay)
}

function cancelLongPress() {
  if (longPressTimer) {
    clearTimeout(longPressTimer)
    longPressTimer = null
  }
}

function showDeleteConfirm(blog) {
  ElMessageBox({
    title: '提示',
    message: '确定要删除这篇游记吗?',
    showCancelButton: true,
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
    beforeClose: (action, instance, done) => {
      done()
      if (action === 'confirm') {
        deleteBlog(blog.id)
      }
    }
  }).catch(() => {})
}

function deleteBlog(blogId) {
  request.delete(`/blog/${blogId}`)
    .then(() => {
      ElMessage.success('删除成功')
      blogs.value = blogs.value.filter(b => b.id !== blogId)
    })
    .catch(err => {
      console.error('删除博客失败', err)
      ElMessage.error('删除失败：' + (err.message || err))
    })
}

function queryBlogsOfFollow() {
  // 原关注列表逻辑保留接口，当前 tab 结构中未直接渲染
}
</script>

<style scoped>
/* 样式合并自原 info.html <style> 内联块 + css/info.css + 文件尾部独立 <style>；body/#app 全局重置不迁移 */
.info-page {
  background: #f5f7fa;
  min-height: 100vh;
  padding-bottom: 60px;
}
.basic {
  padding: 70px 16px 20px;
  display: flex;
  align-items: center;
  background: linear-gradient(to bottom, #409EFF, #fff);
  position: relative;
}
.basic-icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  border: 3px solid #fff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  flex-shrink: 0;
}
.basic-icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.basic-info {
  flex: 1;
  margin-left: 16px;
  color: #fff;
}
.basic-info .name {
  font-size: 20px;
  font-weight: 500;
  margin-bottom: 4px;
}
.edit-btn {
  margin-top: 8px;
  display: inline-block;
  padding: 4px 12px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 16px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.3s;
}
.logout-btn {
  position: absolute;
  top: 50px;
  right: 16px;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.2);
  transition: all 0.3s;
  z-index: 10;
}
.introduce {
  padding: 16px;
  background: #fff;
  margin: 16px;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  cursor: pointer;
}
.introduce span {
  color: #606266;
  font-size: 14px;
  display: flex;
  align-items: center;
}
.edit-icon {
  margin-left: 8px;
  color: #409EFF;
}
.content {
  padding: 0 16px;
  margin-top: 16px;
}
:deep(.el-tabs__nav) {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}
:deep(.el-tabs__item) {
  font-size: 14px;
  padding: 0 20px;
  height: 40px;
  line-height: 40px;
}
.blog-item {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  display: flex;
}
.blog-img {
  width: 40%;
  padding-bottom: 56.25%;
  position: relative;
  overflow: hidden;
  flex-shrink: 0;
}
.blog-img img {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.blog-info {
  flex: 1;
  padding: 12px 16px;
}
.blog-title {
  font-size: 14px;
  font-weight: normal;
  color: #606266;
  margin-bottom: 8px;
  line-height: 1.5;
  height: 42px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.blog-liked,
.blog-comments {
  display: inline-flex;
  align-items: center;
  color: #909399;
  font-size: 12px;
  margin-right: 12px;
}
.blog-liked img {
  width: 16px;
  height: 16px;
  margin-right: 4px;
}
.blog-comments .el-icon {
  margin-right: 4px;
  font-size: 12px;
}
.order-item {
  background: #fff;
  border-radius: 8px;
  margin-bottom: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}
.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
}
.order-id {
  font-size: 13px;
  color: #909399;
}
.order-status {
  font-size: 13px;
  font-weight: 500;
}
.status-pending {
  color: #E6A23C;
}
.status-completed {
  color: #67C23A;
}
.status-cancelled {
  color: #F56C6C;
}
.order-content {
  padding: 16px;
}
.order-info {
  flex: 1;
}
.order-title {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 8px;
}
.order-subtitle {
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
}
.order-detail {
  display: flex;
  flex-wrap: wrap;
}
.detail-item {
  display: flex;
  align-items: center;
  margin-right: 16px;
  margin-bottom: 8px;
  font-size: 13px;
  color: #909399;
}
.detail-item .el-icon {
  margin-right: 4px;
}
.detail-item span span {
  margin-right: 8px;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
}
.order-footer {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
}
.order-footer .el-button {
  margin-left: 8px;
}
.empty-orders {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
  color: #909399;
}
.empty-orders p {
  font-size: 14px;
}
:deep(.el-tabs--card > .el-tabs__header) {
  border-bottom: none;
  margin-bottom: 16px;
}
:deep(.el-tabs--card > .el-tabs__header .el-tabs__nav) {
  border: none;
}
:deep(.el-tabs--card > .el-tabs__header .el-tabs__item) {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  margin-right: 8px;
  height: 32px;
  line-height: 32px;
}
:deep(.el-tabs--card > .el-tabs__header .el-tabs__item.is-active) {
  background-color: #409EFF;
  border-color: #409EFF;
  color: #fff;
}
</style>
