<template>
  <div class="page-container">
    <!-- 顶部状态栏 -->
    <div class="status-bar">
      <div class="weather-widget">
        <el-icon><Sunny /></el-icon>
        <span class="weather-text">26°C</span>
      </div>
      <div class="status-item" :class="{ active: activeTab === 'follow' }" @click="switchTab('follow')">关注</div>
      <div class="status-item" :class="{ active: activeTab === 'recommend' }" @click="switchTab('recommend')">推荐</div>
      <div class="publish-button" @click="toPublishSelect">
        <el-icon><EditPen /></el-icon> 发布
      </div>
    </div>

    <!-- 推荐页 -->
    <div v-if="activeTab === 'recommend'">
      <div class="swiper-container" style="margin: 8px 0;">
        <el-carousel :interval="3000" arrow="always" indicator-position="inside" height="160px" style="border-radius: 8px;">
          <el-carousel-item v-for="item in 4" :key="item" style="border-radius: 8px; overflow: hidden;">
            <router-link :to="'/page' + item">
              <img :src="'/imgs/banner/banner' + item + '.png'" :alt="'Banner ' + item" style="width: 100%; height: 100%; object-fit: cover;">
            </router-link>
          </el-carousel-item>
        </el-carousel>
      </div>

      <div class="type-list">
        <div class="type-box" v-for="t in types" :key="t.id" @click="toShopList(t.id, t.name)">
          <div class="type-view"><img :src="'/imgs/' + t.icon" :alt="t.name"></div>
          <div class="type-text">{{ t.name }}</div>
        </div>
      </div>

      <div class="section-header">
        <div class="section-title">
          <i class="section-icon"></i>
          <span>达人攻略</span>
        </div>
        <div class="section-more" @click="toMore('blog')">
          查看更多
          <el-icon><ArrowRight /></el-icon>
        </div>
      </div>

      <div class="section-content">
        <div class="top-list-container">
          <div class="recommendation-card" v-for="blog in blogs" :key="blog.id" @click="toBlogDetail(blog)">
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
              <span class="like-count"><el-icon><Thumb /></el-icon> {{ blog.likeCount }}</span>
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
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import FootBar from '../components/FootBar.vue'
import request from '../api/request'

const router = useRouter()

// 原 created() 中一次性加载
onMounted(() => {
  queryTypes()
  loadBlogsCursor()
  window.addEventListener('scroll', onWindowScroll)
})

const types = ref([])
const blogs = ref([])
// 达人攻略游标分页：lastId 为当前列表最后一条博客 id，空表示首页
const blogLastId = ref(null)
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

function queryTypes() {
  request.get('/shop-type/list')
    .then(res => {
      types.value = res.data
    })
    .catch(err => {
      ElMessage.error(err)
    })
}

function toShopList(id, name) {  if (id === 4) router.push('/ai')
  else if (id === 7) router.push('/group-tour')
  else if (id === 5) router.push('/local-tour')
  else if (id === 6) router.push('/jingdian')
  else if (id === 3) router.push('/zhaogonglue')
  else if (id === 10) router.push('/watch')
  else if (id === 8) router.push('/top')
  else if (id === 2) router.push('/travel-list')
  else router.push('/shop-list?type=' + id + '&name=' + name)
}

function toBlogDetail(b) {
  router.push('/blog-detail?id=' + b.id)
}

function toMore(type) {
  if (type === 'top') {
    router.push('/top')
  } else if (type === 'blog') {
    router.push('/watch')
  }
}

function loadBlogsCursor() {
  if (blogLoading.value || !blogHasMore.value) return
  blogLoading.value = true
  request.get('/blog/hot/cursor', {
    params: {
      lastId: blogLastId.value,
      pageSize: 10
    }
  })
    .then(res => {
      const data = res.data
      data.list.forEach(b => b.img = b.images.split(',')[0])
      blogs.value = blogs.value.concat(data.list)
      blogHasMore.value = data.hasMore
      if (data.list.length > 0) {
        blogLastId.value = data.list[data.list.length - 1].id
      }
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
        description: blog.content,
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
.status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  background-color: #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  position: sticky;
  top: 0;
  z-index: 1000;
}
.weather-widget {
  display: flex;
  align-items: center;
  font-size: 16px;
  color: #333;
}
.weather-widget i {
  font-size: 20px;
  color: #f9a825;
  margin-right: 4px;
}
.weather-text {
  font-size: 14px;
  font-weight: 500;
}
.status-item {
  font-size: 16px;
  color: #333;
  margin: 0 15px;
  cursor: pointer;
  transition: color 0.3s;
}
.status-item.active {
  font-weight: bold;
  color: #3cb7fe;
  text-shadow: 0 1px 3px rgba(0,0,0,0.2);
}
.publish-button {
  display: flex;
  align-items: center;
  background-color: #45a2f3;
  color: #fff;
  padding: 6px 12px;
  border-radius: 20px;
  cursor: pointer;
  transition: background-color 0.3s;
}
.publish-button i {
  margin-right: 5px;
}
.publish-button:hover {
  background-color: #17d1fb;
}
.swiper-container {
  padding: 0 12px;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px;
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
  background: #409EFF;
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
.type-list {
  height: 25%;
  display: flex;
  flex-wrap: wrap;
  justify-content: space-around;
  box-shadow: 1px 1px 2px rgba(0, 0, 0, 0.3);
  background: #fff;
}
.type-view {
  width: 47px;
  height: 47px;
  border-radius: 50%;
  overflow: hidden;
  margin-bottom: 8px;
  margin-left: 8px;
  background: linear-gradient(145deg, #ffffff, #f5f5f5);
  box-shadow: 0 4px 6px rgba(0,0,0,0.1);
  transition: all 0.3s ease;
  position: relative;
}
.type-view::after {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  border-radius: 50%;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1) inset;
}
.type-view img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transform: scale(0.9);
  transition: transform 0.3s ease;
}
.type-text {
  font-size: 12px;
  color: #333;
  text-align: center;
  transition: color 0.3s ease;
}
.type-box {
  width: 17%;
  text-align: center;
}
.type-box:hover .type-view {
  transform: translateY(-3px);
  box-shadow: 0 6px 12px rgba(0,0,0,0.15);
}
.type-box:hover .type-view img {
  transform: scale(1);
}
.type-box:hover .type-text {
  color: #ff6633;
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
:deep(.el-carousel__arrow) {
  background: rgba(255,255,255,0.8)!important;
  color: #666!important;
}
:deep(.el-carousel__indicators--inside) {
  bottom: 12px;
}
:deep(.el-carousel__indicator button) {
  background: rgba(255,255,255,0.4);
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
</style>
