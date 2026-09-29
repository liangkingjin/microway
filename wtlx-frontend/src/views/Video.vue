<template>
  <div class="video-page">
    <div class="video-container">
      <div class="video-wrapper" @touchstart="touchStart" @touchend="touchEnd">
        <div class="video-item" v-for="(video, index) in videos" :key="video.id" v-show="currentIndex === index">
          <video
            class="video-player"
            :src="video.videoUrl"
            :ref="videoRef(index)"
            :title="video.title"
            loop
            @click="togglePlay(index)"
            @loadedmetadata="videoLoaded(index)"
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
                <img :src="video.authorAvatar || '/imgs/icons/default-icon.jpg'" :alt="video.authorName" :title="video.authorName">
              </div>
            </div>
            <div class="action-item">
              <div class="action-btn like-btn" @click="toggleLike(video)">
                <el-icon :style="{ color: video.isLiked ? '#ff2442' : '#fff' }"><Thumb /></el-icon>
              </div>
              <div class="action-count">{{ video.likesCount || 0 }}</div>
            </div>
            <div class="action-item">
              <div class="action-btn" @click="showComments">
                <el-icon><ChatDotRound /></el-icon>
              </div>
              <div class="action-count">{{ video.commentsCount || 0 }}</div>
            </div>
          </div>
        </div>
        <div v-if="loading" class="loading-text">加载中...</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../api/request'

const router = useRouter()

const videos = ref([])
const currentIndex = ref(0)
const loading = ref(false)
const touchStartY = ref(0)

// Vue2 :ref="'video'+index" 数组 ref → Vue3 函数式 ref
const videoEls = reactive({})
function videoRef(index) {
  return (el) => {
    if (el) videoEls[index] = el
    else delete videoEls[index]
  }
}

onMounted(() => {
  loadVideos()
})

function loadVideos() {
  if (loading.value) return
  loading.value = true

  request.get('/videos')
    .then(res => {
      const data = res.data
      if (data) {
        const newVideos = data.map(video => ({
          id: video.id,
          authorName: video.nickName,
          authorAvatar: video.icon,
          videoUrl: video.videoUrl,
          description: video.description,
          isLiked: video.isLiked,
          likesCount: video.likeCount,
          commentsCount: video.commentCount
        }))
        videos.value = [...videos.value, ...newVideos]
      }
    })
    .catch(err => {
      console.error('请求出错:', err)
      ElMessage.error('加载失败')
    })
    .finally(() => {
      loading.value = false
    })
}

function videoLoaded(index) {
  if (index === currentIndex.value) {
    const video = videoEls[index]
    if (video) video.play()
  }
}

function togglePlay(index) {
  const video = videoEls[index]
  if (!video) return
  if (video.paused) {
    video.play()
  } else {
    video.pause()
  }
}

function touchStart(e) {
  touchStartY.value = e.touches[0].clientY
}

function touchEnd(e) {
  const touchEndY = e.changedTouches[0].clientY
  const diff = touchEndY - touchStartY.value

  // 向上滑动
  if (diff < -50 && currentIndex.value < videos.value.length - 1) {
    const cur = videoEls[currentIndex.value]
    if (cur) cur.pause()
    currentIndex.value++
    nextTick(() => {
      const v = videoEls[currentIndex.value]
      if (v) v.play()
    })
  } else if (diff > 50 && currentIndex.value > 0) {
    const cur = videoEls[currentIndex.value]
    if (cur) cur.pause()
    currentIndex.value--
    nextTick(() => {
      const v = videoEls[currentIndex.value]
      if (v) v.play()
    })
  }
}

function toggleLike(video) {
  request.get(`/video-likes/${video.id}`)
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
  ElMessage({ message: '评论功能开发中...', type: 'info' })
}

function goToOtherInfo(id) {
  router.push('/other-info?id=' + id)
}
</script>

<style scoped>
/* 样式合并自原 video.html <style> 内联块；原 header / foot-bar 在源页中被注释掉，故不渲染头部与底部导航 */
.video-container {
  position: fixed;
  top: 44px; /* header高度（源页 header 已注释，保留原布局间距） */
  left: 0;
  right: 0;
  bottom: 8%; /* footer高度 */
  background: #000;
  z-index: 100;
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
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.7));
  color: #fff;
}
.video-info {
  margin-bottom: 0;
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
  font-size: 28px;
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
.action-count {
  font-size: 12px;
  margin-top: 3px;
  color: #fff;
}
.loading-text {
  color: #fff;
  text-align: center;
  padding: 20px;
}
</style>
