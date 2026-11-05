import { createRouter, createWebHashHistory } from 'vue-router'

// 微途旅行 SPA 路由表（hash 模式，nginx 静态托管零配置）
// 命名规范：视图文件名 = 路由 name（PascalCase 语义化），path 为 kebab-case。
// 禁止拼音缩写（Gonglue/……）与无语义编号（Page1/……），详见 wtlx-frontend/README.md。
// 历史路径兼容表放在 routes 末尾，旧链接（含 query）自动重定向，勿删。
const routes = [
  { path: '/', redirect: '/home' },

  // 首页与内容分发
  { path: '/home', name: 'HomeFeed', component: () => import('../views/HomeFeed.vue'), meta: { title: '微途旅行' } },
  { path: '/popular-destinations', name: 'PopularDestinations', component: () => import('../views/PopularDestinations.vue'), meta: { title: '热门推荐' } },
  { path: '/destination-detail', name: 'DestinationDetail', component: () => import('../views/DestinationDetail.vue'), meta: { title: '目的地详情' } },
  { path: '/blogs', name: 'BlogFeed', component: () => import('../views/BlogFeed.vue'), meta: { title: '看游记' } },
  { path: '/blog-detail', name: 'BlogDetail', component: () => import('../views/BlogDetail.vue'), meta: { title: '游记详情' } },
  { path: '/blog-edit', name: 'BlogEdit', component: () => import('../views/BlogEdit.vue'), meta: { title: '发布游记' } },
  { path: '/guides', name: 'GuideList', component: () => import('../views/GuideList.vue'), meta: { title: '找攻略' } },
  { path: '/guide-detail', name: 'GuideDetail', component: () => import('../views/GuideDetail.vue'), meta: { title: '攻略详情' } },
  { path: '/check-in', name: 'CheckInBoard', component: () => import('../views/CheckInBoard.vue'), meta: { title: '打卡发现' } },

  // 精选攻略（静态内容页，按主题命名）
  { path: '/cherry-blossom-guide', name: 'CherryBlossomGuide', component: () => import('../views/CherryBlossomGuide.vue'), meta: { title: '赏樱攻略' } },
  { path: '/hainan-island-guide', name: 'HainanIslandGuide', component: () => import('../views/HainanIslandGuide.vue'), meta: { title: '海岛攻略' } },
  { path: '/photo-spots-guide', name: 'PhotoSpotsGuide', component: () => import('../views/PhotoSpotsGuide.vue'), meta: { title: '拍照打卡攻略' } },
  { path: '/spring-outing-guide', name: 'SpringOutingGuide', component: () => import('../views/SpringOutingGuide.vue'), meta: { title: '踏青攻略' } },

  // 行程规划与游玩
  { path: '/trip-itinerary', name: 'TripItinerary', component: () => import('../views/TripItinerary.vue'), meta: { title: '旅行规划' } },
  { path: '/travel-plan-list', name: 'TravelPlanList', component: () => import('../views/TravelPlanList.vue'), meta: { title: '我的旅行计划' } },
  { path: '/travel-plan-detail', name: 'TravelPlanDetail', component: () => import('../views/TravelPlanDetail.vue'), meta: { title: '计划详情' } },
  { path: '/spot-detail', name: 'SpotDetail', component: () => import('../views/SpotDetail.vue'), meta: { title: '景点详情' } },
  { path: '/map-navigation', name: 'MapNavigation', component: () => import('../views/MapNavigation.vue'), meta: { title: '地图导航' } },
  { path: '/local-tour', name: 'LocalTour', component: () => import('../views/LocalTour.vue'), meta: { title: '当地游' } },
  { path: '/group-tour', name: 'GroupTour', component: () => import('../views/GroupTour.vue'), meta: { title: '组团游' } },
  { path: '/scenic-detail', name: 'ScenicDetail', component: () => import('../views/ScenicDetail.vue'), meta: { title: '景点详情' } },

  // 店铺与订单
  { path: '/shop-list', name: 'ShopList', component: () => import('../views/ShopList.vue'), meta: { title: '店铺列表' } },
  { path: '/shop-detail', name: 'ShopDetail', component: () => import('../views/ShopDetail.vue'), meta: { title: '店铺详情' } },
  { path: '/hotel-room-detail', name: 'HotelRoomDetail', component: () => import('../views/HotelRoomDetail.vue'), meta: { title: '酒店房型详情' } },
  { path: '/hotel-order-detail', name: 'HotelOrderDetail', component: () => import('../views/HotelOrderDetail.vue'), meta: { title: '酒店订单详情' } },
  { path: '/ticket-order-detail', name: 'TicketOrderDetail', component: () => import('../views/TicketOrderDetail.vue'), meta: { title: '门票订单详情' } },

  // 用户、发布与 AI
  { path: '/login', name: 'Login', component: () => import('../views/Login.vue'), meta: { title: '登录', requiresAuth: false } },
  { path: '/password-login', name: 'PasswordLogin', component: () => import('../views/PasswordLogin.vue'), meta: { title: '密码登录', requiresAuth: false } },
  { path: '/profile', name: 'Profile', component: () => import('../views/Profile.vue'), meta: { title: '我的' } },
  { path: '/profile-edit', name: 'ProfileEdit', component: () => import('../views/ProfileEdit.vue'), meta: { title: '编辑资料' } },
  { path: '/user-profile', name: 'UserProfile', component: () => import('../views/UserProfile.vue'), meta: { title: '用户主页' } },
  { path: '/publish-select', name: 'PublishSelect', component: () => import('../views/PublishSelect.vue'), meta: { title: '发布' } },
  { path: '/ai-assistant', name: 'AiAssistant', component: () => import('../views/AiAssistant.vue'), meta: { title: 'AI 智能助手' } },

  // 协议页
  { path: '/privacy-policy', name: 'PrivacyPolicy', component: () => import('../views/PrivacyPolicy.vue'), meta: { title: '隐私政策', requiresAuth: false } },
  { path: '/user-agreement', name: 'UserAgreement', component: () => import('../views/UserAgreement.vue'), meta: { title: '用户服务协议', requiresAuth: false } },

  // 历史路径兼容：拼音 / 编号命名时期的链接（书签、AI 对话卡片、分享链接）继续可用。
  // 只做一次性重定向，query（如 ?id=23）与 hash 原样透传，不引入第二套页面。
  ...Object.entries({
    '/index': '/home',
    '/ai': '/ai-assistant',
    '/gonglue-detail': '/guide-detail',
    '/zhaogonglue': '/guides',
    '/jingdian': '/check-in',
    '/info': '/profile',
    '/info-edit': '/profile-edit',
    '/other-info': '/user-profile',
    '/login2': '/password-login',
    '/map': '/map-navigation',
    '/page1': '/cherry-blossom-guide',
    '/page2': '/hainan-island-guide',
    '/page3': '/photo-spots-guide',
    '/page4': '/spring-outing-guide',
    '/top': '/popular-destinations',
    '/top-detail': '/destination-detail',
    '/travel-plan': '/trip-itinerary',
    '/travel-list': '/travel-plan-list',
    '/list-detail': '/travel-plan-detail',
    '/watch': '/blogs'
  }).map(([from, to]) => ({
    path: from,
    redirect: (route) => ({ path: to, query: route.query, hash: route.hash })
  })),

  // 404 兜底（必须最后）
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('../views/NotFound.vue'), meta: { title: '页面不存在' } }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 登录守卫：未登录且页面需要鉴权时跳转 /login
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
