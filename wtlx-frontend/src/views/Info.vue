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
/* ==========================================================================
   个人主页（我的）— 布局与样式统一
   说明：template / script 结构与交互逻辑未改动，本文件仅重写样式层。
   1) 设计令牌集中在 .info-page：间距 / 圆角 / 配色 / 字阶 / 阴影统一取值
   2) --pp-gutter 一处定义，头部渐变区、简介卡、内容区共用同一左右边距
   3) 修复：头部遗留 70px 占位、退出登录绝对定位错位、游记图 padding-bottom
      百分比基准错误、简介卡与内容区左右边距不一致、卡片圆角不统一等
   ========================================================================== */
.info-page {
  /* ---- 间距节奏 ---- */
  --pp-gap-xs: 4px;
  --pp-gap-sm: 8px;
  --pp-gap-md: 12px;
  --pp-gap-lg: 16px;
  --pp-gap-xl: 24px;

  /* ---- 圆角 ---- */
  --pp-radius-sm: 8px;
  --pp-radius-md: 12px;
  --pp-radius-pill: 999px;

  /* ---- 配色 ---- */
  --pp-brand: #409eff;
  --pp-brand-strong: #2f7fd8;
  --pp-brand-tint: #8ecae6;
  --pp-bg: #f5f7fa;
  --pp-surface: #ffffff;
  --pp-surface-muted: #f7f9fc;
  --pp-text-1: #303133;
  --pp-text-2: #606266;
  --pp-text-3: #909399;
  --pp-text-4: #c0c4cc;
  --pp-border: #ebeef5;
  --pp-border-strong: #e4e7ed;
  --pp-warning: #e6a23c;
  --pp-success: #67c23a;
  --pp-danger: #f56c6c;

  /* ---- 阴影 ---- */
  --pp-shadow-card: 0 2px 12px rgba(31, 45, 61, 0.06);
  --pp-shadow-float: 0 8px 24px rgba(31, 45, 61, 0.12);

  /* ---- 字体与字阶（统一字体层级，避免各块各自为政）---- */
  --pp-font: -apple-system, BlinkMacSystemFont, "PingFang SC", "Hiragino Sans GB",
    "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif;
  --pp-fs-xl: 20px;
  --pp-fs-lg: 16px;
  --pp-fs-md: 14px;
  --pp-fs-sm: 13px;
  --pp-fs-xs: 12px;

  /* ---- 版心：移动端 16px 边距，桌面端居中定宽 ---- */
  --pp-page-max: 720px;
  --pp-gutter: max(var(--pp-gap-lg), calc((100% - var(--pp-page-max)) / 2));

  background: var(--pp-bg);
  min-height: 100vh;
  min-height: 100dvh;
  padding-bottom: calc(66px + env(safe-area-inset-bottom, 0px));
  font-family: var(--pp-font);
  -webkit-tap-highlight-color: transparent;
}

/* ---------------------- 头部：头像 / 昵称 / 操作 ---------------------- */
.basic {
  display: flex;
  align-items: center;
  gap: var(--pp-gap-lg);
  padding: calc(var(--pp-gap-xl) + env(safe-area-inset-top, 0px)) var(--pp-gutter)
    var(--pp-gap-xl);
  /* 渐变收尾色改为页面底色，消除原先 #fff 与 #f5f7fa 之间的色差断层 */
  background-image: radial-gradient(
      120% 90% at 12% -10%,
      rgba(255, 255, 255, 0.26) 0%,
      rgba(255, 255, 255, 0) 55%
    ),
    linear-gradient(
      180deg,
      #3183dd 0%,
      var(--pp-brand) 38%,
      #86bdf7 70%,
      #d6e8fa 88%,
      var(--pp-bg) 100%
    );
  position: relative;
}
.basic-icon {
  flex: 0 0 auto;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  border: 3px solid #fff;
  background: #fff;
  box-shadow: 0 4px 14px rgba(23, 62, 102, 0.18);
}
.basic-icon img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.basic-info {
  flex: 1 1 auto;
  min-width: 0; /* 长昵称不再把右侧按钮挤出屏幕 */
  color: #fff;
}
.basic-info .name {
  margin: 0 0 10px;
  font-size: var(--pp-fs-xl);
  font-weight: 600;
  line-height: 1.3;
  letter-spacing: 0.2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 编辑资料 / 退出登录：同一套胶囊按钮，尺寸与间距对齐 */
.edit-btn,
.logout-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 30px;
  padding: 0 14px;
  border: 1px solid rgba(255, 255, 255, 0.34);
  border-radius: var(--pp-radius-pill);
  background: rgba(255, 255, 255, 0.2);
  -webkit-backdrop-filter: blur(6px);
  backdrop-filter: blur(6px);
  color: #fff;
  font-size: var(--pp-fs-xs);
  font-weight: 500;
  line-height: 1;
  cursor: pointer;
  transition: background-color 0.2s ease, border-color 0.2s ease,
    transform 0.2s ease;
}
.edit-btn:hover,
.logout-btn:hover {
  background: rgba(255, 255, 255, 0.3);
  border-color: rgba(255, 255, 255, 0.55);
}
.edit-btn:active,
.logout-btn:active {
  transform: scale(0.96);
}
/* 退出登录回归文档流，不再绝对定位（原先 top:50px 在窄屏会压住昵称） */
.logout-btn {
  flex: 0 0 auto;
}

/* ---------------------- 个人简介卡 ---------------------- */
.introduce {
  margin: var(--pp-gap-lg) var(--pp-gutter) 0;
  padding: 14px var(--pp-gap-lg);
  background: var(--pp-surface);
  border-radius: var(--pp-radius-md);
  box-shadow: var(--pp-shadow-card);
  cursor: pointer;
  transition: box-shadow 0.25s ease, transform 0.25s ease;
}
.introduce:hover {
  box-shadow: var(--pp-shadow-float);
}
.introduce:active {
  transform: scale(0.995);
}
.introduce span {
  display: flex;
  align-items: flex-start; /* 多行简介时编辑图标不再垂直居中偏移 */
  gap: var(--pp-gap-sm);
  color: var(--pp-text-2);
  font-size: var(--pp-fs-md);
  line-height: 1.6;
  word-break: break-word;
}
.edit-icon {
  flex: 0 0 auto;
  margin: 3px 0 0;
  color: var(--pp-brand);
  font-size: var(--pp-fs-md);
}

/* ---------------------- 内容区与标签页 ---------------------- */
.content {
  padding: 0 var(--pp-gutter);
  margin-top: var(--pp-gap-lg);
}
.content :deep(.el-tabs) {
  --el-color-primary: var(--pp-brand);
}
.content :deep(.el-tabs__header) {
  margin: 0 0 var(--pp-gap-lg);
}
.content :deep(.el-tabs__nav-wrap) {
  padding: var(--pp-gap-xs);
  background: var(--pp-surface);
  border-radius: var(--pp-radius-md);
  box-shadow: var(--pp-shadow-card);
}
/* 去掉 Element 默认 2px 底线，避免白底卡片下出现多余横线 */
.content :deep(.el-tabs__nav-wrap::after) {
  display: none;
}
.content :deep(.el-tabs__item) {
  height: 40px;
  padding: 0 20px;
  line-height: 40px;
  font-size: var(--pp-fs-md);
  font-weight: 500;
  color: var(--pp-text-2);
  transition: color 0.2s ease;
}
.content :deep(.el-tabs__item.is-active) {
  color: var(--pp-brand);
  font-weight: 600;
}
/* 覆盖 main.css 全局橙色 active-bar（#F63 → #f5b9a2），与页面主色统一 */
.content :deep(.el-tabs__active-bar) {
  height: 3px;
  border-radius: 3px;
  background-color: transparent;
  background-image: linear-gradient(
    90deg,
    var(--pp-brand),
    var(--pp-brand-tint)
  );
}
.content :deep(.el-tabs__content) {
  overflow: visible;
}
/* 内层卡片型标签（门票 / 酒店）：重置上面的白底卡片样式 */
.content :deep(.el-tabs--card > .el-tabs__header) {
  margin-bottom: 0;
  border-bottom: none;
}
.content :deep(.el-tabs--card > .el-tabs__header > .el-tabs__nav-wrap) {
  padding: 0;
  background: transparent;
  box-shadow: none;
}
.content :deep(.el-tabs--card > .el-tabs__header .el-tabs__nav) {
  border: none;
}
.content :deep(.el-tabs--card > .el-tabs__header .el-tabs__item) {
  height: 32px;
  padding: 0 14px;
  margin-right: var(--pp-gap-sm);
  border: 1px solid var(--pp-border-strong);
  border-radius: var(--pp-radius-pill);
  line-height: 30px;
  font-size: var(--pp-fs-sm);
  font-weight: 500;
  color: var(--pp-text-2);
  transition: background-color 0.2s ease, border-color 0.2s ease,
    color 0.2s ease;
}
.content :deep(.el-tabs--card > .el-tabs__header .el-tabs__item.is-active) {
  background-color: var(--pp-brand);
  border-color: var(--pp-brand);
  color: #fff;
}

/* ---------------------- 订单卡片 ---------------------- */
.order-item {
  background: var(--pp-surface);
  border-radius: var(--pp-radius-md);
  margin-bottom: var(--pp-gap-md);
  box-shadow: var(--pp-shadow-card);
  overflow: hidden;
  transition: box-shadow 0.25s ease;
}
.order-item:last-child {
  margin-bottom: 0;
}
.order-item:hover {
  box-shadow: var(--pp-shadow-float);
}
.order-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--pp-gap-sm);
  padding: var(--pp-gap-md) var(--pp-gap-lg);
  border-bottom: 1px solid var(--pp-border);
}
.order-id {
  flex: 1 1 auto;
  min-width: 0;
  font-size: var(--pp-fs-sm);
  color: var(--pp-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.order-status {
  flex: 0 0 auto;
  padding: 2px var(--pp-gap-sm);
  border-radius: var(--pp-radius-sm);
  font-size: var(--pp-fs-xs);
  font-weight: 600;
  line-height: 18px;
}
.status-pending {
  color: var(--pp-warning);
  background: rgba(230, 162, 60, 0.14);
}
.status-completed {
  color: var(--pp-success);
  background: rgba(103, 194, 58, 0.14);
}
.status-cancelled {
  color: var(--pp-danger);
  background: rgba(245, 108, 108, 0.14);
}
.order-content {
  padding: var(--pp-gap-md) var(--pp-gap-lg);
}
.order-info {
  min-width: 0;
}
.order-title {
  margin: 0 0 var(--pp-gap-xs);
  font-size: var(--pp-fs-lg);
  font-weight: 600;
  line-height: 1.45;
  color: var(--pp-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.order-subtitle {
  margin: 0 0 var(--pp-gap-sm);
  font-size: var(--pp-fs-sm);
  color: var(--pp-text-2);
}
.order-detail {
  display: flex;
  flex-wrap: wrap;
  gap: var(--pp-gap-sm) var(--pp-gap-lg);
}
.detail-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0; /* 间距统一交给父级 gap，不再各自 margin 叠加 */
  font-size: var(--pp-fs-sm);
  line-height: 1.6;
  color: var(--pp-text-3);
}
.detail-item .el-icon {
  flex: 0 0 auto;
  font-size: var(--pp-fs-md);
  color: var(--pp-text-4);
}
.detail-item > span {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}
.detail-item span span {
  display: inline-block;
  margin: 0;
  padding: 2px var(--pp-gap-sm);
  border-radius: 6px;
  background: var(--pp-surface-muted);
  font-size: var(--pp-fs-xs);
  line-height: 1.5;
  color: var(--pp-text-2);
}
.order-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--pp-gap-sm);
  padding: var(--pp-gap-md) var(--pp-gap-lg);
  border-top: 1px solid var(--pp-border);
  background: var(--pp-surface-muted);
}
.order-footer .el-button {
  margin-left: 0; /* 间距由 gap 统一，避免与 el-button 默认 margin 叠加 */
}
.empty-orders {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--pp-gap-md);
  padding: 56px var(--pp-gap-lg);
  background: var(--pp-surface);
  border-radius: var(--pp-radius-md);
  box-shadow: var(--pp-shadow-card);
  color: var(--pp-text-3);
}
.empty-orders p {
  margin: 0; /* 去掉 p 默认外边距造成的间距不可控 */
  font-size: var(--pp-fs-md);
}

/* ---------------------- 游记卡片 ---------------------- */
.blog-item {
  display: flex;
  align-items: stretch;
  background: var(--pp-surface);
  border-radius: var(--pp-radius-md);
  overflow: hidden;
  margin-bottom: var(--pp-gap-md);
  box-shadow: var(--pp-shadow-card);
  cursor: pointer;
  transition: box-shadow 0.25s ease, transform 0.25s ease;
}
.blog-item:last-child {
  margin-bottom: 0;
}
.blog-item:hover {
  box-shadow: var(--pp-shadow-float);
  transform: translateY(-2px);
}
.blog-item:active {
  transform: translateY(0);
}
.blog-img {
  flex: 0 0 40%;
  align-self: flex-start;
  /* 原用 padding-bottom:56.25% —— 百分比基准是卡片整宽而非图片列宽，
     导致图片列高达 190px+ 撑坏卡片；改为 aspect-ratio 精准控比 */
  aspect-ratio: 4 / 3;
  position: relative;
  overflow: hidden;
  background: #eef1f5;
}
.blog-img img {
  position: absolute;
  top: 0;
  left: 0;
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.blog-info {
  flex: 1 1 auto;
  min-width: 0;
  padding: var(--pp-gap-md) var(--pp-gap-lg);
}
.blog-title {
  /* 去掉写死 height:42px，改由 line-clamp 控制，长标题不再被裁切 */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin: 0 0 var(--pp-gap-sm);
  font-size: var(--pp-fs-md);
  font-weight: 500;
  line-height: 1.5;
  color: var(--pp-text-1);
  word-break: break-word;
}
.blog-liked,
.blog-comments {
  display: inline-flex;
  align-items: center;
  gap: var(--pp-gap-xs);
  margin-right: var(--pp-gap-md);
  font-size: var(--pp-fs-xs);
  line-height: 1;
  color: var(--pp-text-3);
  vertical-align: middle;
}
/* 点赞图 16px 与评论图标 12px 尺寸不一致 → 统一 14px */
.blog-liked img {
  display: block;
  width: 14px;
  height: 14px;
  object-fit: contain;
}
.blog-comments .el-icon {
  margin: 0;
  font-size: var(--pp-fs-md);
}

/* ---------------------- 响应式 ---------------------- */
@media (min-width: 768px) {
  .info-page {
    --pp-page-max: 760px;
  }
  .basic {
    padding-top: calc(32px + env(safe-area-inset-top, 0px));
    padding-bottom: 28px;
  }
  .basic-icon {
    width: 96px;
    height: 96px;
    border-width: 4px;
  }
  .basic-info .name {
    font-size: 24px;
  }
  .edit-btn,
  .logout-btn {
    height: 34px;
    padding: 0 18px;
    font-size: var(--pp-fs-sm);
  }
  .introduce {
    padding: 16px var(--pp-gap-xl);
  }
  .blog-img {
    flex: 0 0 240px;
  }
  .blog-info {
    padding: var(--pp-gap-lg);
  }
  .order-detail {
    gap: var(--pp-gap-md) var(--pp-gap-xl);
  }
  /* 桌面端游记列表两列平铺（:has 不支持时优雅退化为单列） */
  .content :deep(.el-tab-pane:has(> .blog-item)) {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--pp-gap-lg);
    align-content: start;
  }
  .blog-item {
    margin-bottom: 0;
  }
}

@media (max-width: 360px) {
  .basic {
    gap: var(--pp-gap-md);
  }
  .basic-icon {
    width: 68px;
    height: 68px;
  }
  .basic-info .name {
    font-size: 18px;
  }
  .edit-btn,
  .logout-btn {
    padding: 0 10px;
  }
  .blog-img {
    flex: 0 0 36%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .blog-item,
  .order-item,
  .introduce,
  .edit-btn,
  .logout-btn {
    transition: none;
  }
}
</style>
