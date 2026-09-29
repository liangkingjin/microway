<template>
  <div class="wt-page profile-page">
    <ProfileHeader :user="user" @edit="toEdit" @logout="handleLogout" />
    <ProfileIntroCard :introduce="user.introduce" @edit="toEditIntroduce" />

    <div class="profile-page__content wt-container">
      <el-tabs v-model="activeName" class="wt-tabs" @tab-click="handleTabClick">
        <!-- 订单 -->
        <el-tab-pane label="订单" name="1">
          <el-tabs
            v-model="orderActiveTab"
            class="wt-tabs wt-tabs--nested"
            type="card"
            @tab-click="handleOrderTabClick"
          >
            <el-tab-pane label="门票订单" name="ticket">
              <ProfileOrderCard
                v-for="order in ticketOrders"
                :key="order.id"
                :order="order"
                :type="ORDER_TYPE.TICKET"
                @view="viewOrderDetail($event, ORDER_TYPE.TICKET)"
                @cancel="confirmCancel($event, ORDER_TYPE.TICKET)"
              />
              <EmptyState v-if="!ticketOrders.length" text="暂无门票订单">
                <template #icon>
                  <el-icon class="wt-empty__icon"><Tickets /></el-icon>
                </template>
              </EmptyState>
            </el-tab-pane>

            <el-tab-pane label="酒店订单" name="hotel">
              <ProfileOrderCard
                v-for="order in hotelOrders"
                :key="order.id"
                :order="order"
                :type="ORDER_TYPE.HOTEL"
                @view="viewOrderDetail($event, ORDER_TYPE.HOTEL)"
                @cancel="confirmCancel($event, ORDER_TYPE.HOTEL)"
              />
              <EmptyState v-if="!hotelOrders.length" text="暂无酒店订单">
                <template #icon>
                  <el-icon class="wt-empty__icon"><House /></el-icon>
                </template>
              </EmptyState>
            </el-tab-pane>
          </el-tabs>
        </el-tab-pane>

        <!-- 游记 -->
        <el-tab-pane label="游记" name="2" class="profile-page__pane--grid">
          <ProfileBlogCard
            v-for="blog in blogs"
            :key="blog.id"
            :blog="blog"
            @long-press="showDeleteConfirm"
          />
        </el-tab-pane>
      </el-tabs>
    </div>

    <FootBar :active-btn="4" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Tickets, House } from '@element-plus/icons-vue'
import FootBar from '../components/FootBar.vue'
import EmptyState from '../components/common/EmptyState.vue'
import ProfileHeader from '../components/profile/ProfileHeader.vue'
import ProfileIntroCard from '../components/profile/ProfileIntroCard.vue'
import ProfileOrderCard from '../components/profile/ProfileOrderCard.vue'
import ProfileBlogCard from '../components/profile/ProfileBlogCard.vue'
import { getMe, logout as logoutApi } from '../api/user'
import { useProfileOrders } from '../composables/useProfileOrders'
import { useProfileBlogs } from '../composables/useProfileBlogs'
import { ORDER_TYPE, DEFAULT_AVATAR } from '../constants'

/**
 * 个人主页（我的）
 * 本文件只做三件事：拉取数据、编排子组件、处理路由与弹窗交互。
 * 区块 UI 见 components/profile/*，订单与游记的数据逻辑见 composables/*。
 */
const router = useRouter()

const user = ref({})
const activeName = ref('1')

const {
  ticketOrders,
  hotelOrders,
  activeTab: orderActiveTab,
  loadAll: loadAllOrders,
  loadTab: loadOrderTab,
  refresh: refreshOrders,
  removeOrder
} = useProfileOrders()

const { blogs, load: loadBlogs, remove: removeBlog } = useProfileBlogs()

onMounted(() => {
  loadAllOrders()
  loadUser()
})

async function loadUser() {
  try {
    const res = await getMe()
    user.value = {
      ...res.data,
      icon: res.data.icon ? '' + res.data.icon : DEFAULT_AVATAR
    }
    loadBlogs()
  } catch (e) {
    // 未登录：与原有行为一致，直接回登录页
    router.push('/login')
  }
}

function toEdit() {
  router.push('/info-edit')
}

function toEditIntroduce() {
  router.push('/info-edit?edit=introduce')
}

async function handleLogout() {
  try {
    await logoutApi()
    sessionStorage.removeItem('token')
    router.push('/')
  } catch (err) {
    ElMessage.error(err)
  }
}

function handleTabClick(tab) {
  // 切回订单页签时刷新当前子页签数据
  if (tab.name === '1') refreshOrders()
}

function handleOrderTabClick(tab) {
  loadOrderTab(tab.name)
}

function viewOrderDetail(order, type) {
  const path =
    type === ORDER_TYPE.TICKET ? '/ticket-order-detail' : '/hotel-order-detail'
  router.push(`${path}?id=${order.id}`)
}

function confirmCancel(order, type) {
  ElMessageBox.confirm('确定要取消该订单吗?', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => removeOrder(order.id, type))
    .catch(() => {})
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
      if (action === 'confirm') removeBlog(blog.id)
    }
  }).catch(() => {})
}
</script>

<style scoped>
.profile-page__content {
  margin-top: var(--wt-space-4);
}
/* 页签内容区本身作为列表容器（纵向排列），不再额外包一层 div */
.profile-page__content :deep(.el-tab-pane) {
  display: flex;
  flex-direction: column;
  gap: var(--wt-space-3);
}

@media (min-width: 768px) {
  /* 桌面端游记两列平铺 */
  .profile-page__content :deep(.el-tab-pane.profile-page__pane--grid) {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--wt-space-4);
    align-content: start;
  }
}
</style>
