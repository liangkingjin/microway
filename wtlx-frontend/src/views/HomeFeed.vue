<template>
  <div class="page-container">
    <!-- 顶部：品牌 + 内容 tab + 发布（去掉写死的假天气） -->
    <div class="home-header">
      <div class="hh-brand">微途</div>
      <div class="hh-tabs">
        <div class="hh-tab" :class="{ active: activeTab === 'recommend' }" @click="switchTab('recommend')">推荐</div>
        <div class="hh-tab" :class="{ active: activeTab === 'follow' }" @click="switchTab('follow')">关注</div>
      </div>
      <div class="hh-publish" @click="toPublishSelect">
        <el-icon><EditPen /></el-icon><span>发布</span>
      </div>
    </div>

    <!-- 推荐页 -->
    <div v-if="activeTab === 'recommend'">
      <!-- 今日精选：热门游记首条做视觉锚点，替代 4 张自动轮播 banner -->
      <div v-if="featuredBlog" class="featured" @click="toBlogDetail(featuredBlog)">
        <div class="featured__cover">
          <img :src="featuredBlog.img" :alt="featuredBlog.title" class="featured__img">
          <span class="featured__badge">今日精选</span>
        </div>
        <div class="featured__body">
          <div class="featured__title">{{ featuredBlog.title }}</div>
          <div class="featured__meta">
            <img :src="featuredBlog.icon || '/imgs/icons/default-icon.jpg'" alt="" class="featured__avatar">
            <span>{{ featuredBlog.name }}</span>
          </div>
        </div>
      </div>

      <!-- 核心入口：固定 4 个，不再从 /shop-type/list 拉 10 宫格 -->
      <div class="quick-entries">
        <div class="quick-entry" v-for="entry in QUICK_ENTRIES" :key="entry.path" @click="router.push(entry.path)">
          <div class="quick-entry__icon"><img :src="entry.icon" :alt="entry.label"></div>
          <div class="quick-entry__label">{{ entry.label }}</div>
        </div>
      </div>

      <div class="section-header">
        <div class="section-title">
          <i class="section-icon"></i>
          <span>为你推荐</span>
        </div>
        <div class="section-more" @click="toMore('blog')">
          查看更多
          <el-icon><ArrowRight /></el-icon>
        </div>
      </div>

      <div class="section-content">
        <div class="top-list-container">
          <div class="recommendation-card" v-for="blog in feedBlogs" :key="blog.id" @click="toBlogDetail(blog)">
            <img :src="blog.img" :alt="blog.title" :title="blog.title" class="card-image">
            <div class="blog-info">
              <div class="blog-title">{{ blog.title }}</div>
              <div class="blog-author">
                <img :src="blog.icon || '/imgs/icons/default-icon.jpg'" :alt="blog.name" :title="blog.name" class="author-avatar">
                <span>{{ blog.name }}</span>
              </div>
            </div>
          </div>
        </div>
        <!-- 加载/到底提示 -->
        <div class="blog-load-status">
          <span v-if="blogLoading">加载中...</span>
          <span v-else-if="blogNoMore" class="no-more">— 到底啦，没有更多攻略了 —</span>
        </div>
      </div>
    </div>

    <!-- 关注页 -->
    <div v-if="activeTab === 'follow'" class="follow-container">
      <div class="empty-state" v-if="!followedBlogs || followedBlogs.length === 0">
        <el-icon style="font-size: 48px; color: #ccc;"><User /></el-icon>
        <p>您还没有关注任何人</p>
        <el-button type="primary" size="small" @click="switchTab('recommend')">去发现更多内容</el-button>
      </div>
      <div v-else class="followed-blogs">
        <div class="blog-card" v-for="blog in followedBlogs" :key="blog.id" @click="toBlogDetail(blog)">
          <div class="blog-header">
            <img :src="blog.userIcon || '/imgs/icons/default-icon.jpg'" :alt="blog.userName" class="user-avatar">
            <span class="user-name">{{ blog.userName }}</span>
          </div>
          <div class="blog-content">
            <img :src="blog.coverImage" :alt="blog.title" class="blog-image">
            <h3 class="blog-title">{{ blog.title }}</h3>
            <p class="blog-description">{{ blog.description }}</p>
          </div>
          <div class="blog-footer">
            <span class="blog-time">{{ blog.createTime }}</span>
            <div class="blog-stats">
              <span class="like-count"><el-icon><Pointer /></el-icon> {{ blog.likeCount }}</span>
              <span class="comment-count"><el-icon><ChatDotRound /></el-icon> {{ blog.commentCount }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <FootBar :active-btn="1" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import FootBar from '../components/FootBar.vue'
import request from '../api/request'
import { toPlainSummary } from '../utils/blogContent'

const router = useRouter()

// 首页只保留 4 个核心入口：内容消费 → AI 规划 → 计划沉淀 → 内容社区
const QUICK_ENTRIES = [
  { label: 'AI 规划', icon: '/imgs/types/人工智能机器人.png', path: '/ai-assistant' },
  { label: '找攻略', icon: '/imgs/types/找攻略.png', path: '/guides' },
  { label: '我的计划', icon: '/imgs/types/旅行清单.png', path: '/travel-plan-list' },
  { label: '看游记', icon: '/imgs/types/看游记.png', path: '/blogs' }
]

// 原 created() 中一次性加载
onMounted(() => {
  loadBlogsCursor()
  window.addEventListener('scroll', onWindowScroll)
})

const types = ref([])
const blogs = ref([])
// 首条热门游记作为「今日精选」大图，其余进入双列信息流，避免重复展示
const featuredBlog = computed(() => blogs.value[0] || null)
const feedBlogs = computed(() => blogs.value.slice(1))
// 达人攻略分页：cursor 由后端下发，前端只透传，不需要理解批次边界
const feedCursor = ref(null)
const blogHasMore = ref(true)
const blogLoading = ref(false)
const blogNoMore = ref(false)
const activeTab = ref('recommend')
const followedBlogs = ref([])

function toPublishSelect() {
  router.push('/publish-select')
}

function switchTab(tab) {
  activeTab.value = tab
  if (tab === 'follow') {
    loadFollowedBlogs()
  }
}

function toBlogDetail(b) {
  router.push('/blog-detail?id=' + b.id)
}

function toMore(type) {
  if (type === 'top') {
    router.push('/popular-destinations')
  } else if (type === 'blog') {
    router.push('/blogs')
  }
}

function loadBlogsCursor() {
  if (blogLoading.value || !blogHasMore.value) return
  blogLoading.value = true
  request.get('/blog/hot/cursor', {
    params: {
      cursor: feedCursor.value,
      pageSize: 10
    }
  })
    .then(res => {
      const data = res.data
      const list = data.list || []
      list.forEach(b => b.img = (b.images || '').split(',')[0])
      blogs.value = blogs.value.concat(list)
      feedCursor.value = data.cursor
      blogHasMore.value = data.hasMore
      if (!data.hasMore) blogNoMore.value = true
    })
    .catch(err => {
      console.error('加载攻略失败:', err)
      ElMessage.error('加载攻略失败')
    })
    .finally(() => {
      blogLoading.value = false
    })
}

// 窗口滚动触底：距底部不足 300px 时加载下一页
function onWindowScroll() {
  if (activeTab.value !== 'recommend') return
  const scrollTop = document.documentElement.scrollTop || document.body.scrollTop
  const clientHeight = document.documentElement.clientHeight
  const scrollHeight = document.documentElement.scrollHeight
  if (scrollTop + clientHeight >= scrollHeight - 300) {
    loadBlogsCursor()
  }
}

function loadFollowedBlogs() {
  request.get('/blog/of/follow', {
    params: {
      offset: 0,
      lastId: new Date().getTime()
    }
  })
    .then(res => {
      const data = res.data
      if (!data || !data.list) {
        followedBlogs.value = []
        return
      }
      followedBlogs.value = data.list.map(blog => ({
        id: blog.id,
        userIcon: blog.icon,
        userName: blog.name,
        coverImage: blog.images.split(',')[0],
        title: blog.title,
        // 卡片摘要只取纯文本：历史数据正文带 <br>，直接插值会把标签当文字露出来
        description: toPlainSummary(blog.content),
        createTime: formatTime(blog.createTime),
        likeCount: blog.liked,
        commentCount: blog.comments
      }))
    })
    .catch(err => {
      console.error('加载关注用户博客失败:', err)
      ElMessage.error('加载关注用户博客失败')
    })
}

function formatTime(timestamp) {
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now - date
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 2592000000) return Math.floor(diff / 86400000) + '天前'
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onWindowScroll)
})
</script>

<style scoped>
/* 以下样式合并自原 index.html <style> 内联块 + css/index.css，原样保留 */
.page-container {
  background: #f8f8f8;
  min-height: 100vh;
  padding-bottom: 60px;
}
.home-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  position: sticky;
  top: 0;
  z-index: 1000;
}
.hh-brand {
  font-size: 17px;
  font-weight: 600;
  color: #2c3e50;
  letter-spacing: 1px;
}
.hh-tabs {
  flex: 1;
  display: flex;
  justify-content: center;
  gap: 20px;
}
.hh-tab {
  position: relative;
  font-size: 15px;
  color: #7a8290;
  padding: 4px 0;
  cursor: pointer;
  transition: color 0.2s;
}
.hh-tab.active {
  color: #0f6e56;
  font-weight: 600;
}
.hh-tab.active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: -3px;
  width: 20px;
  height: 2px;
  border-radius: 1px;
  background: #1d9e75;
  transform: translateX(-50%);
}
.hh-publish {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: 20px;
  background: #1d9e75;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}
.featured {
  margin: 12px 12px 0;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
}
.featured__cover {
  position: relative;
  aspect-ratio: 16 / 9;
  background: #eef1f5;
}
.featured__img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.featured__badge {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 3px 10px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.92);
  color: #0f6e56;
  font-size: 12px;
  font-weight: 600;
}
.featured__body { padding: 12px 14px 14px; }
.featured__title {
  font-size: 16px;
  font-weight: 600;
  color: #2c3e50;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.featured__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  color: #8a9099;
  font-size: 12px;
}
.featured__avatar {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  object-fit: cover;
}
.quick-entries {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-top: 10px;
  padding: 14px 12px;
  background: #fff;
}
.quick-entry { text-align: center; cursor: pointer; }
.quick-entry__icon {
  width: 46px;
  height: 46px;
  margin: 0 auto 6px;
  border-radius: 50%;
  background: #eaf7f2;
  color: #0f6e56;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 21px;
  transition: transform 0.2s ease;
}
.quick-entry__icon img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  padding: 6px;
  box-sizing: border-box;
}
.quick-entry:hover .quick-entry__icon { transform: translateY(-2px); }
.quick-entry__label { font-size: 12px; color: #4a5058; }
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px;
  margin-top: 10px;
  background: #fff;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  color: #333;
  font-weight: 500;
}
.section-icon {
  width: 4px;
  height: 16px;
  background: #1d9e75;
  border-radius: 2px;
}
.section-more {
  color: #999;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}
.top-list-container {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  padding: 10px;
  padding-bottom: 70px;
}
.top-list-container .recommendation-card {
  margin: 5px;
  width: auto;
}
.top-list-container .card-image {
  height: 150px;
}
.top-list-container .top-tag {
  top: 10px;
  left: 10px;
  padding: 5px 10px;
}
.top-list-container .top-number {
  font-size: 18px;
}
.top-list-container .location-name {
  font-size: 18px;
  bottom: 20px;
}
.recommendation-card {
  margin: 15px;
  border-radius: 12px;
  overflow: hidden;
  background: #fff;
  position: relative;
  box-shadow: 0 2px 10px rgba(0,0,0,0.05);
}
.top-tag {
  position: absolute;
  top: 20px;
  left: 20px;
  background: #ffffff;
  padding: 8px 16px;
  border-radius: 8px;
  z-index: 1;
}
.top-number {
  font-size: 24px;
  font-weight: bold;
  color: #88f3ff;
}
.card-image {
  width: 100%;
  height: 200px;
  object-fit: cover;
  display: block;
}
.location-name {
  font-size: 24px;
  font-weight: bold;
  color: #fff;
  position: absolute;
  bottom: 60px;
  left: 20px;
  text-shadow: 0 2px 4px rgba(0,0,0,0.3);
}
.blog-info {
  padding: 10px;
}
.blog-title {
  font-size: 14px;
  color: #333;
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.blog-author {
  display: flex;
  align-items: center;
  gap: 6px;
}
.author-avatar {
  width: 20px;
  height: 20px;
  border-radius: 50%;
}
.blog-author span {
  font-size: 12px;
  color: #666;
}
.blog-load-status {
  text-align: center;
  padding: 14px 0 60px;
  font-size: 13px;
  color: #999;
}
.follow-container {
  padding: 12px;
  min-height: calc(100vh - 100px);
  background: #f8f8f8;
  padding-bottom: 60px;
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  text-align: center;
}
.empty-state p {
  color: #909399;
  margin-bottom: 16px;
}
.followed-blogs {
  padding: 8px;
}
.blog-card {
  background: #fff;
  border-radius: 12px;
  margin-bottom: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}
.blog-header {
  display: flex;
  align-items: center;
  padding: 12px;
  border-bottom: 1px solid #f5f5f5;
}
.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  margin-right: 12px;
}
.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}
.blog-content {
  padding: 12px;
}
.blog-image {
  width: 100%;
  height: 200px;
  object-fit: cover;
  border-radius: 8px;
  margin-bottom: 12px;
}
.blog-description {
  font-size: 14px;
  color: #666;
  line-height: 1.5;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.blog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border-top: 1px solid #f5f5f5;
}
.blog-time {
  font-size: 12px;
  color: #999;
}
.blog-stats {
  display: flex;
  align-items: center;
  gap: 16px;
}
.like-count,
.comment-count {
  display: flex;
  align-items: center;
  font-size: 12px;
  color: #999;
}
.like-count i,
.comment-count i {
  margin-right: 4px;
  font-size: 14px;
}
</style>
