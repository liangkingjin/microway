import { createRouter, createWebHashHistory } from 'vue-router'

// 微途旅行 SPA 路由表（hash 模式，nginx 静态托管零配置）
// 命名规范：路由路径与视图文件名统一为 PascalCase 视图名（views/<Name>.vue）
// 原多页文件名 → 路由路径映射见 MIGRATION_SPEC.md 附录路由表
const routes = [
  { path: '/', redirect: '/index' },
  { path: '/index', name: 'Index', component: () => import('../views/Index.vue'), meta: { title: '微途旅行' } },
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue'), meta: { title: '登录', requiresAuth: false } },
  { path: '/login2', name: 'Login2', component: () => import('../views/Login2.vue'), meta: { title: '密码登录', requiresAuth: false } },
  { path: '/ai', name: 'AI', component: () => import('../views/AI.vue'), meta: { title: 'AI 智能助手' } },
  { path: '/blog-detail', name: 'BlogDetail', component: () => import('../views/BlogDetail.vue'), meta: { title: '游记详情' } },
  { path: '/blog-edit', name: 'BlogEdit', component: () => import('../views/BlogEdit.vue'), meta: { title: '发布游记' } },
  { path: '/gonglue-detail', name: 'GonglueDetail', component: () => import('../views/GonglueDetail.vue'), meta: { title: '攻略详情' } },
  { path: '/hotel-order-detail', name: 'HotelOrderDetail', component: () => import('../views/HotelOrderDetail.vue'), meta: { title: '酒店订单详情' } },
  { path: '/hotel-room-detail', name: 'HotelRoomDetail', component: () => import('../views/HotelRoomDetail.vue'), meta: { title: '酒店房型详情' } },
  { path: '/info', name: 'Info', component: () => import('../views/Info.vue'), meta: { title: '我的' } },
  { path: '/info-edit', name: 'InfoEdit', component: () => import('../views/InfoEdit.vue'), meta: { title: '编辑资料' } },
  { path: '/other-info', name: 'OtherInfo', component: () => import('../views/OtherInfo.vue'), meta: { title: '他人主页' } },
  { path: '/jingdian', name: 'Jingdian', component: () => import('../views/Jingdian.vue'), meta: { title: '景点' } },
  { path: '/map', name: 'Map', component: () => import('../views/Map.vue'), meta: { title: '行程地图' } },
  { path: '/page1', name: 'Page1', component: () => import('../views/Page1.vue'), meta: { title: '活动页1' } },
  { path: '/page2', name: 'Page2', component: () => import('../views/Page2.vue'), meta: { title: '活动页2' } },
  { path: '/page3', name: 'Page3', component: () => import('../views/Page3.vue'), meta: { title: '活动页3' } },
  { path: '/page4', name: 'Page4', component: () => import('../views/Page4.vue'), meta: { title: '活动页4' } },
  { path: '/privacy-policy', name: 'PrivacyPolicy', component: () => import('../views/PrivacyPolicy.vue'), meta: { title: '隐私政策', requiresAuth: false } },
  { path: '/publish-select', name: 'PublishSelect', component: () => import('../views/PublishSelect.vue'), meta: { title: '发布' } },
  { path: '/scenic-detail', name: 'ScenicDetail', component: () => import('../views/ScenicDetail.vue'), meta: { title: '景点详情' } },
  { path: '/shop-detail', name: 'ShopDetail', component: () => import('../views/ShopDetail.vue'), meta: { title: '店铺详情' } },
  { path: '/shop-list', name: 'ShopList', component: () => import('../views/ShopList.vue'), meta: { title: '店铺列表' } },
  { path: '/ticket-order-detail', name: 'TicketOrderDetail', component: () => import('../views/TicketOrderDetail.vue'), meta: { title: '门票订单详情' } },
  { path: '/top', name: 'Top', component: () => import('../views/Top.vue'), meta: { title: 'TOP 榜' } },
  { path: '/top-detail', name: 'TopDetail', component: () => import('../views/TopDetail.vue'), meta: { title: 'TOP 详情' } },
  { path: '/travel-plan', name: 'TravelPlan', component: () => import('../views/TravelPlan.vue'), meta: { title: '行程规划' } },
  { path: '/user-agreement', name: 'UserAgreement', component: () => import('../views/UserAgreement.vue'), meta: { title: '用户服务协议', requiresAuth: false } },
  { path: '/watch', name: 'Watch', component: () => import('../views/Watch.vue'), meta: { title: '视频详情' } },
  { path: '/zhaogonglue', name: 'ZhaoGonglue', component: () => import('../views/ZhaoGonglue.vue'), meta: { title: '找攻略' } },
  // 中文文件名页面 → 英文路由
  { path: '/local-tour', name: 'LocalTour', component: () => import('../views/LocalTour.vue'), meta: { title: '当地游' } },
  { path: '/travel-list', name: 'TravelList', component: () => import('../views/TravelList.vue'), meta: { title: '旅游清单' } },
  { path: '/list-detail', name: 'ListDetail', component: () => import('../views/ListDetail.vue'), meta: { title: '清单详情' } },
  { path: '/group-tour', name: 'GroupTour', component: () => import('../views/GroupTour.vue'), meta: { title: '组团游' } },
  // 404 兜底
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('../views/NotFound.vue'), meta: { title: '页面不存在' } }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 登录守卫：未登录且页面需要鉴权时跳转 /login
// （原多页架构：各页 mounted 无 token 跳 /login.html；现统一收敛到路由守卫）
router.beforeEach((to) => {
  // 【临时预览放行】仅 npm run dev 生效：跳过登录校验，便于业务后端未就绪时预览 UI。
  // 生产构建（npm run build）时 import.meta.env.DEV 为 false，守卫照常生效。预览结束删除本段即可还原。
  if (import.meta.env.DEV) {
    return true
  }
  const token = sessionStorage.getItem('token')
  if (to.meta.requiresAuth !== false && !token) {
    return { path: '/login', query: to.fullPath !== '/login' ? { redirect: to.fullPath } : {} }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? to.meta.title + ' - 微途旅行' : '微途旅行'
})

export default router
