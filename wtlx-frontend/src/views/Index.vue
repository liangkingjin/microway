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
      <div class="status-item" :class="{ active: activeTab === 'discover' }" @click="switchTab('discover')">发现</div>
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
          <span>热门推荐</span>
        </div>
        <div class="section-more" @click="toMore('top')">
          查看更多
          <el-icon><ArrowRight /></el-icon>
        </div>
      </div>

      <div class="section-content">
        <div class="top-list-container">
          <div class="recommendation-card" v-for="(item, index) in recommendations.slice(0, 6)" :key="index" @click="goToDetail(item)">
            <div class="top-tag">
              <div class="top-number">TOP {{ padNumber(index + 1) }}</div>
            </div>
            <img :src="item.coverUrl" :alt="item.title" :title="item.title" class="card-image">
            <div class="location-name">{{ item.title }}</div>
          </div>
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
          <div class="recommendation-card" v-for="(blog, index) in blogs.slice(0, 6)" :key="blog.id" @click="toBlogDetail(blog)">
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

    <!-- 发现页（视频流） -->
    <div v-if="activeTab === 'discover'" class="discover-container">
      <div class="video-container">
        <div class="video-wrapper" @touchstart="touchStart" @touchend="touchEnd">
          <!-- 只渲染当前及前后各一个视频：未渲染的 <video> 不会发起请求，避免一次性拉取全部视频。
           v-if 必须放在 template 上，Vue3 不允许 v-if 与 v-for 同元素且 v-if 拿不到循环变量 -->
          <template v-for="(video, index) in videos" :key="video.videoId">
            <div class="video-item" v-if="Math.abs(index - currentIndex) <= 1" v-show="currentIndex === index">
              <video
                class="video-player"
                :src="video.videoUrl"
                :ref="videoRef(index)"
                :title="video.description"
                loop
                muted
                playsinline
                preload="auto"
                @click="togglePlay(index)"
                @loadedmetadata="videoLoaded(index)"
                @error="videoError(index)"
              ></video>
              <div class="video-overlay">
                <div class="video-info">
                  <div class="video-title">{{ video.authorName }}</div>
                  <div class="video-desc">{{ video.description }}</div>
                </div>
              </div>
              <div class="video-actions">
                <div class="action-item">
                  <div class="action-btn avatar" @click="goToOtherInfo(video.id)">
                    <img :src="video.authorAvatar" :alt="video.authorName" :title="video.authorName">
                  </div>
                </div>
                <div class="action-item">
                  <div class="action-btn like-btn" @click="toggleLike(video)">
                    <el-icon :style="{ color: video.isLiked ? '#ff2442' : '#fff' }"><Thumb /></el-icon>
                  </div>
                  <div class="action-count">{{ video.likesCount || 0 }}</div>
                </div>
                <div class="action-item">
                  <div class="action-btn" @click="showComments(video)">
                    <el-icon><ChatDotRound /></el-icon>
                  </div>
                  <div class="action-count">{{ video.commentsCount || 0 }}</div>
                </div>
              </div>
            </div>
          </template>
          <div v-if="loading" class="loading-text">加载中...</div>
        </div>
      </div>
    </div>

    <FootBar :active-btn="1" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElLoading } from 'element-plus'
import FootBar from '../components/FootBar.vue'
import request from '../api/request'
import { safeAutoPlay, toggleVideo, onVideoError } from '../utils/video'

const router = useRouter()

// 原 created() 中一次性加载
onMounted(() => {
  queryTypes()
  loadRecommendations()
  loadVideos()
  loadBlogs()
})

const isReachBottom = ref(false)
const types = ref([])
const blogs = ref([])
const current = ref(1)
const recommendations = ref([])
const loading = ref(false)
const activeTab = ref('recommend')
const videos = ref([])
const currentIndex = ref(0)
const startY = ref(0)
const followedBlogs = ref([])

// 视频 DOM 引用（Vue2 :ref="'video'+index" → Vue3 函数式 ref）
const videoEls = reactive({})
function videoRef(index) {
  return (el) => {
    if (el) videoEls[index] = el
    else delete videoEls[index]
  }
}

function toPublishSelect() {
  router.push('/publish-select')
}

function switchTab(tab) {
  activeTab.value = tab
  if (tab === 'follow') {
    loadFollowedBlogs()
  } else if (tab === 'discover' && videos.value.length > 0) {
    // 视频已挂载后自动播放当前视频
    setTimeout(() => playVideo(currentIndex.value), 50)
  } else {
    pauseCurrentVideo()
  }
}

function loadVideos() {
  request.get('/videos')
    .then(res => {
      const data = res.data
      if (data) {
        videos.value = data.map(video => ({
          // 注意：VideosVO.id 返回的是发布者 userId，视频主键是 videoId，
          // key 必须用 videoId，否则同一作者的多个视频 key 重复会导致渲染/ref 错乱
          id: video.id,
          videoId: video.videoId,
          authorName: video.nickName,
          authorAvatar: video.icon,
          videoUrl: video.videoUrl,
          description: video.description,
          isLiked: video.isLiked,
          likesCount: video.likeCount,
          commentsCount: video.commentCount
        }))
      }
    })
    .catch(err => {
      console.error('加载视频失败:', err)
      ElMessage.error(typeof err === 'string' ? err : '加载视频失败')
    })
}

function videoLoaded(index) {
  if (activeTab.value === 'discover' && index === currentIndex.value) {
    playVideo(index)
  }
}

function videoError(index) {
  console.error('视频加载失败:', videos.value[index] && videos.value[index].videoUrl)
  onVideoError('视频加载失败，请检查视频地址或网络')
}

function playVideo(index) {
  pauseAllVideos()
  safeAutoPlay(videoEls[index])
}

function pauseAllVideos() {
  videos.value.forEach((_, i) => {
    const video = videoEls[i]
    if (video) video.pause()
  })
}

function pauseCurrentVideo() {
  const video = videoEls[currentIndex.value]
  if (video) {
    video.pause()
  }
}

function togglePlay(index) {
  toggleVideo(videoEls[index])
}

function touchStart(e) {
  startY.value = e.touches[0].clientY
}

function touchEnd(e) {
  const endY = e.changedTouches[0].clientY
  const diff = endY - startY.value
  if (diff < -50 && currentIndex.value < videos.value.length - 1) {
    currentIndex.value++
    setTimeout(() => playVideo(currentIndex.value), 50)
  } else if (diff > 50 && currentIndex.value > 0) {
    currentIndex.value--
    setTimeout(() => playVideo(currentIndex.value), 50)
  }
}

function toggleLike(video) {
  request.get(`/video-likes/${video.videoId}`)
    .then(() => {
      video.isLiked = !video.isLiked
      video.likesCount = video.isLiked ? (video.likesCount || 0) + 1 : (video.likesCount || 1) - 1
    })
    .catch(err => {
      console.error(err)
      ElMessage.error('操作失败')
    })
}

function showComments() {
  ElMessage({
    message: '评论功能开发中...',
    type: 'info'
  })
}

function goToOtherInfo(userId) {
  router.push('/other-info?id=' + userId)
}

function padNumber(num) {
  return num.toString().padStart(2, '0')
}

function formatNumber(num) {
  if (num >= 10000) {
    return (num / 10000).toFixed(1) + '万'
  }
  return num.toString()
}

function goToDetail(item) {
  router.push('/top-detail?id=' + item.id)
}

function loadRecommendations() {
  loading.value = true
  const loadingInstance = ElLoading.service({
    lock: true,
    text: '加载中...',
    background: 'rgba(255, 255, 255, 0.7)'
  })
  request.get('/place-recommendations')
    .then(res => {
      if (res.success) {
        recommendations.value = res.data.map(item => ({
          id: item.id,
          title: item.title,
          coverUrl: item.coverUrl || './imgs/default.png'
        }))
      } else {
        ElMessage.error('加载失败')
      }
    })
    .catch(error => {
      console.error('加载推荐失败:', error)
      ElMessage.error('网络错误，请稍后重试')
    })
    .finally(() => {
      loading.value = false
      loadingInstance.close()
    })
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

function queryHotBlogsScroll() {
  request.get('/blog/hot?current=' + current.value)
    .then(res => {
      const data = res.data
      data.forEach(b => b.img = b.images.split(',')[0])
      blogs.value = blogs.value.concat(data)
    })
    .catch(err => {
      ElMessage.error(err)
    })
}

function addLike(b) {
  request.put('/blog/like/' + b.id)
    .then(() => {
      queryBlogById(b)
    })
    .catch(err => {
      ElMessage.error(err)
    })
}

function queryBlogById(b) {
  request.get('/blog/' + b.id)
    .then(res => {
      const data = res.data
      b.liked = data.liked
      b.isLike = data.isLike
    })
    .catch(() => {
      console.error
      b.liked++
    })
}

function onScroll(e) {
  const scrollTop = e.target.scrollTop
  const offsetHeight = e.target.offsetHeight
  const scrollHeight = e.target.scrollHeight
  if (scrollTop + offsetHeight > scrollHeight && !isReachBottom.value) {
    isReachBottom.value = true
    current.value++
    queryHotBlogsScroll()
  } else {
    isReachBottom.value = false
  }
}

function toShopList(id, name) {
  if (id === 4) router.push('/ai')
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

function toBlogEdit() {
  router.push('/blog-edit')
}

function toMore(type) {
  if (type === 'top') {
    router.push('/top')
  } else if (type === 'blog') {
    router.push('/watch')
  }
}

function loadBlogs() {
  request.get('/blog/hot?current=1')
    .then(res => {
      blogs.value = res.data.map(blog => {
        blog.img = blog.images.split(',')[0]
        return blog
      })
    })
    .catch(err => {
      console.error('加载博客失败:', err)
      ElMessage.error('加载博客失败')
    })
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
  pauseAllVideos()
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
.discover-container {
  position: fixed;
  top: 50px;
  left: 0;
  right: 0;
  bottom: 60px;
  background: #000;
  z-index: 100;
}
.video-container {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}
.video-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}
.video-item {
  position: absolute;
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
}
.video-player {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.video-overlay {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 15px;
  background: linear-gradient(transparent, rgba(0,0,0,0.7));
  color: #fff;
}
.video-title {
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 8px;
}
.video-desc {
  font-size: 14px;
  opacity: 0.9;
}
.video-actions {
  position: absolute;
  right: 15px;
  bottom: 80px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  align-items: center;
}
.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  color: #fff;
}
.action-btn {
  width: 45px;
  height: 45px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.action-btn.avatar {
  border: 2px solid #fff;
  border-radius: 50%;
  overflow: hidden;
}
.action-btn.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.action-btn i {
  font-size: 28px;
  color: #fff;
  text-shadow: 0 1px 1px rgba(0,0,0,0.2);
}
.action-count {
  font-size: 12px;
  margin-top: 3px;
  color: #fff;
}
.loading-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #fff;
  z-index: 2;
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
