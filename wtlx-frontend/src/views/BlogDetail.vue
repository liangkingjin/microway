<template>
  <div class="page-container">
    <HeaderBar title="博客详情" :show-back="true" />
    <div class="page-scroll">
      <div class="blog-info-box" ref="swiperBox"
           @touchstart="moveStart"
           @touchmove="moving"
           @touchend="moveEnd">
        <div class="swiper-item" v-for="(img, i) in blog.images" :key="i">
          <img :src="img" alt="" style="width: 100%" height="100%">
        </div>
      </div>
      <div class="basic">
        <div class="basic-icon" @click="toOtherInfo">
          <img :src="blog.icon || '/imgs/icons/default-icon.jpg'" alt="">
        </div>
        <div class="basic-info">
          <div class="name">{{ blog.name }}</div>
          <span class="time">{{ formatTime(new Date(blog.createTime)) }}</span>
        </div>
        <div style="width: 20%">
          <div class="logout-btn" @click="follow" v-show="!user || user.id !== blog.userId">
            {{ followed ? '取消关注' : '关注' }}
          </div>
        </div>
      </div>
      <!-- 正文既要兼容历史 HTML 数据，也要还原新游记里的换行与连续空格，
           统一走 toDisplayHtml（纯文本转义后 \n→<br>、空格→&nbsp;） -->
      <div class="blog-text" v-html="toDisplayHtml(blog.content)"></div>
      <!-- 点赞卡：桌面端由 margin-inline 走版心居中，移动端退化为 16px 边距 -->
      <div class="zan-box wt-card">
        <div class="zan-count" :class="{ 'is-liked': blog.isLike }" @click="addLike">
          <svg t="1646634642977" class="icon zan-icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" width="20" height="20">
            <path d="M160 944c0 8.8-7.2 16-16 16h-32c-26.5 0-48-21.5-48-48V528c0-26.5 21.5-48 48-48h32c8.8 0 16 7.2 16 16v448zM96 416c-53 0-96 43-96 96v416c0 53 43 96 96 96h96c17.7 0 32-14.3 32-32V448c0-17.7-14.3-32-32-32H96zM505.6 64c16.2 0 26.4 8.7 31 13.9 4.6 5.2 12.1 16.3 10.3 32.4l-23.5 203.4c-4.9 42.2 8.6 84.6 36.8 116.4 28.3 31.7 68.9 49.9 111.4 49.9h271.2c6.6 0 10.8 3.3 13.2 6.1s5 7.5 4 14l-48 303.4c-6.9 43.6-29.1 83.4-62.7 112C815.8 944.2 773 960 728.9 960h-317c-33.1 0-59.9-26.8-59.9-59.9v-455c0-6.1 1.7-12 5-17.1 69.5-109 106.4-234.2 107-364h41.6z m0-64h-44.9C427.2 0 400 27.2 400 60.7c0 127.1-39.1 251.2-112 355.3v484.1c0 68.4 55.5 123.9 123.9 123.9h317c122.7 0 227.2-89.3 246.3-210.5l47.9-303.4c7.8-49.4-30.4-94.1-80.4-94.1H671.6c-50.9 0-90.5-44.4-84.6-95l23.5-203.4C617.7 55 568.7 0 505.6 0z" :fill="blog.isLike ? '#ff6633' : '#82848a'"></path>
          </svg>
          <span class="zan-count__num">{{ blog.liked }}</span>
          <span class="zan-count__unit">人点赞</span>
        </div>
        <div class="zan-avatars" v-if="likes.length">
          <div class="zan-avatar" v-for="u in likes.slice(0, 6)" :key="u.id">
            <img :src="u.icon || '/imgs/icons/default-icon.jpg'" alt="">
          </div>
          <span class="zan-more" v-if="likes.length > 6">+{{ likes.length - 6 }}</span>
        </div>
      </div>
      <div class="blog-comments wt-card">
        <div class="comments-head">
          <span class="comments-title">网友评价</span>
          <span class="comments-count">{{ comments.length }}</span>
        </div>
        <!-- 评论输入框 -->
        <div class="comment-input-box">
          <div class="comment-input-avatar">
            <img :src="user.icon || '/imgs/icons/default-icon.jpg'" alt="">
          </div>
          <div class="comment-input-content">
            <el-input
              type="textarea"
              v-model="commentContent"
              :rows="2"
              placeholder="说点什么吧..."
              resize="none">
            </el-input>
            <div class="comment-input-actions">
              <div class="upload-images">
                <el-icon @click="uploadCommentImage"><Picture /></el-icon>
                <input type="file" ref="commentImageInput" style="display:none" @change="handleCommentImageChange">
                <input type="file" ref="replyImageInput" style="display:none" @change="handleReplyImageChange">
                <div class="preview-images" v-if="commentImages.length > 0">
                  <div class="preview-image-item" v-for="(img, index) in commentImages" :key="index">
                    <img :src="img" alt="">
                    <el-icon class="preview-close" @click="removeCommentImage(index)"><Close /></el-icon>
                  </div>
                </div>
              </div>
              <el-button type="primary" size="small" @click="submitComment" :disabled="!commentContent.trim()">发表评论</el-button>
            </div>
          </div>
        </div>
        <div class="comment-list">
          <!-- 评论项 -->
          <div class="comment-box" v-for="comment in comments" :key="comment.id">
            <div class="comment-icon">
              <img :src="comment.userIcon || '/imgs/icons/default-icon.jpg'" alt="">
            </div>
            <div class="comment-info">
              <div class="comment-user">
                <span>{{ comment.userName }}</span>
                <span class="comment-user__level">Lv{{ comment.userLevel }}</span>
              </div>
              <div class="comment-score" v-if="comment.score">
                <span class="comment-score__label">打分</span>
                <el-rate disabled v-model="comment.score"></el-rate>
              </div>
              <div class="comment-content">{{ comment.content }}</div>
              <div class="comment-images" v-if="comment.images && comment.images.length > 0">
                <img v-for="(img, imgIndex) in comment.images" :key="imgIndex" :src="img" alt="">
              </div>
              <div class="comment-actions">
                <span class="comment-time">{{ formatCommentTime(comment.createTime) }}</span>
                <span class="comment-reply-btn" @click="showReplyInput(comment)">回复</span>
              </div>
              <!-- 回复列表 -->
              <div class="reply-list" v-if="comment.replies && comment.replies.length > 0">
                <div class="reply-item" v-for="reply in comment.replies" :key="reply.id">
                  <div class="reply-user">
                    <span class="reply-username">{{ reply.userName }}</span>
                    <span v-if="reply.replyToUserName">回复 <span class="reply-to-username">{{ reply.replyToUserName }}</span>：</span>
                    <span v-else>：</span>
                  </div>
                  <div class="reply-content">{{ reply.content }}</div>
                  <div class="reply-images" v-if="reply.images && reply.images.length > 0">
                    <img v-for="(img, imgIndex) in reply.images" :key="imgIndex" :src="img" alt="">
                  </div>
                  <div class="reply-actions">
                    <span class="reply-time">{{ formatCommentTime(reply.createTime) }}</span>
                    <span class="reply-btn" @click="showReplyInput(comment, reply)">回复</span>
                  </div>
                </div>
              </div>
              <!-- 回复输入框 -->
              <div class="reply-input-box" v-if="comment.showReplyInput">
                <el-input
                  type="textarea"
                  v-model="comment.replyContent"
                  :rows="1"
                  :placeholder="comment.replyPlaceholder || '回复评论...'"
                  resize="none">
                </el-input>
                <div class="reply-input-actions">
                  <div class="upload-images" v-if="!comment.replyPlaceholder">
                    <el-icon @click="uploadReplyImage(comment)"><Picture /></el-icon>
                    <div class="preview-images" v-if="comment.replyImages && comment.replyImages.length > 0">
                      <div class="preview-image-item" v-for="(img, index) in comment.replyImages" :key="index">
                        <img :src="img" alt="">
                        <el-icon class="preview-close" @click="removeReplyImage(comment, index)"><Close /></el-icon>
                      </div>
                    </div>
                  </div>
                  <el-button type="primary" size="small" @click="submitReply(comment)" :disabled="!comment.replyContent || !comment.replyContent.trim()">回复</el-button>
                </div>
              </div>
            </div>
          </div>
          <div v-if="comments.length > 0" class="comment-more">
            <span>查看全部 {{ comments.length }} 条评价</span>
            <el-icon><ArrowRight /></el-icon>
          </div>
          <div v-else class="wt-empty">
            <el-icon class="wt-empty__icon"><ChatDotRound /></el-icon>
            <p class="wt-empty__text">暂无评论，快来发表第一条评论吧</p>
          </div>
        </div>
      </div>
    </div>
    <div class="foot">
      <div class="foot-box">
        <div class="foot-view" @click="addLike()">
          <svg t="1646634642977" class="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" width="26" height="26">
            <path d="M160 944c0 8.8-7.2 16-16 16h-32c-26.5 0-48-21.5-48-48V528c0-26.5 21.5-48 48-48h32c8.8 0 16 7.2 16 16v448zM96 416c-53 0-96 43-96 96v416c0 53 43 96 96 96h96c17.7 0 32-14.3 32-32V448c0-17.7-14.3-32-32-32H96zM505.6 64c16.2 0 26.4 8.7 31 13.9 4.6 5.2 12.1 16.3 10.3 32.4l-23.5 203.4c-4.9 42.2 8.6 84.6 36.8 116.4 28.3 31.7 68.9 49.9 111.4 49.9h271.2c6.6 0 10.8 3.3 13.2 6.1s5 7.5 4 14l-48 303.4c-6.9 43.6-29.1 83.4-62.7 112C815.8 944.2 773 960 728.9 960h-317c-33.1 0-59.9-26.8-59.9-59.9v-455c0-6.1 1.7-12 5-17.1 69.5-109 106.4-234.2 107-364h41.6z m0-64h-44.9C427.2 0 400 27.2 400 60.7c0 127.1-39.1 251.2-112 355.3v484.1c0 68.4 55.5 123.9 123.9 123.9h317c122.7 0 227.2-89.3 246.3-210.5l47.9-303.4c7.8-49.4-30.4-94.1-80.4-94.1H671.6c-50.9 0-90.5-44.4-84.6-95l23.5-203.4C617.7 55 568.7 0 505.6 0z" :fill="blog.isLike ? '#ff6633' : '#82848a'"></path>
          </svg>
          <span :class="{ liked: blog.isLike }">{{ blog.liked }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import HeaderBar from '../components/HeaderBar.vue'
import request from '../api/request'
import { getUrlParam } from '../utils/index'
import { toDisplayHtml } from '../utils/blogContent'

const router = useRouter()

const blog = ref({})
const likes = ref([])
const user = ref({})
const followed = ref(false)
const comments = ref([])
const commentContent = ref('')
const commentImages = ref([])

const swiperBox = ref(null)
const commentImageInput = ref(null)
// 共享一个隐藏 file input 用于回复图片上传（原 Vue2 动态 :ref 方案的等价实现）
const replyImageInput = ref(null)
let _replyImageTarget = null

// 自定义图片轮播状态
let _width = 0
const duration = 300
let container = null
let items = []
const active = ref(0)
const start = reactive({ x: 0, y: 0 })
const move = reactive({ x: 0, y: 0 })
const sensitivity = 60
const resistance = 0.3
let isMoving = false

function each(ary, callback) {
  for (let i = 0, l = ary.length; i < l; i++) {
    if (callback(ary[i], i) === false) break
  }
}

onMounted(() => {
  const id = getUrlParam('id')
  queryBlogById(id)
  queryComments(id) // 加载评论数据
})

// 获取评论数据
function queryComments(blogId) {
  request.get('/tb-blog-comments/list', { params: { blogId } })
    .then(res => {
      comments.value = buildCommentTree(res.data || [])
    })
    .catch(err => ElMessage.error(err))
}

// 构建评论树
function buildCommentTree(list) {
  const map = {}
  const roots = []
  list.forEach(item => {
    item.replies = []
    if (!item.userIcon) {
      item.userIcon = '/imgs/icons/default-icon.jpg'
    }
    if (typeof item.images === 'string' && item.images.trim() !== '') {
      item.images = item.images.split(',').map(s => s.trim()).filter(Boolean)
    } else {
      item.images = []
    }
    item.showReplyInput = false
    item.replyContent = ''
    item.replyPlaceholder = ''
    item.replyToUserId = null
    item.replyImages = []
    map[item.id] = item
  })
  list.forEach(item => {
    if (item.parentId && item.parentId !== 0) {
      if (map[item.parentId]) {
        map[item.parentId].replies.push(item)
        item.replyToUserName = map[item.parentId].userName
      }
    } else {
      roots.push(item)
    }
  })
  return roots
}

// 提交评论
function submitComment() {
  if (!commentContent.value.trim()) {
    ElMessage.warning('评论内容不能为空')
    return
  }
  const blogId = blog.value.id
  const userId = user.value.id
  request.post('/tb-blog-comments', {
    blogId,
    userId,
    content: commentContent.value,
    images: commentImages.value.join(','),
    parentId: 0,
    answerId: 0
  }).then(() => {
    commentContent.value = ''
    commentImages.value = []
    ElMessage.success('评论发表成功')
    queryComments(blogId)
  }).catch(err => ElMessage.error(err))
}

// 提交回复
function submitReply(comment) {
  if (!comment.replyContent || !comment.replyContent.trim()) {
    ElMessage.warning('回复内容不能为空')
    return
  }
  const blogId = blog.value.id
  const userId = user.value.id
  request.post('/tb-blog-comments', {
    blogId,
    userId,
    content: comment.replyContent,
    images: (comment.replyImages || []).join(','),
    parentId: comment.id,
    answerId: comment.answerId || comment.id,
    replyToUserId: comment.replyToUserId
  }).then(() => {
    comment.replyContent = ''
    comment.replyImages = []
    comment.showReplyInput = false
    ElMessage.success('回复发表成功')
    queryComments(blogId)
  }).catch(err => ElMessage.error(err))
}

function showReplyInput(comment, reply) {
  function closeAllReplyBox(list) {
    list.forEach(c => {
      c.showReplyInput = false
      c.replyContent = ''
      c.replyPlaceholder = ''
      c.replyToUserId = null
      c.replyImages = []
      if (c.replies && c.replies.length > 0) {
        closeAllReplyBox(c.replies)
      }
    })
  }
  closeAllReplyBox(comments.value)

  if (reply) {
    comment.showReplyInput = true
    comment.replyPlaceholder = `回复 ${reply.userName}：`
    comment.replyToUserId = reply.userId
    comment.answerId = reply.id
  } else {
    comment.showReplyInput = true
    comment.replyPlaceholder = ''
    comment.replyToUserId = comment.userId
    comment.answerId = comment.id
  }
}

// 格式化评论时间
function formatCommentTime(timeStr) {
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now - date
  if (diff < 60 * 1000) return '刚刚'
  if (diff < 60 * 60 * 1000) return Math.floor(diff / (60 * 1000)) + '分钟前'
  if (diff < 24 * 60 * 60 * 1000) return Math.floor(diff / (60 * 60 * 1000)) + '小时前'
  if (diff < 30 * 24 * 60 * 60 * 1000) return Math.floor(diff / (24 * 60 * 60 * 1000)) + '天前'
  return `${date.getFullYear()}-${(date.getMonth() + 1).toString().padStart(2, '0')}-${date.getDate().toString().padStart(2, '0')}`
}

// 上传评论图片
function uploadCommentImage() {
  if (commentImageInput.value) commentImageInput.value.click()
}
function handleCommentImageChange(e) {
  const file = e.target.files[0]
  if (!file) return
  const formData = new FormData()
  formData.append('file', file)
  const config = {
    headers: { 'Content-Type': 'multipart/form-data;boundary=' + new Date().getTime() }
  }
  request.post('/upload/blog', formData, config)
    .then(res => commentImages.value.push('/imgs' + res.data))
    .catch(err => ElMessage.error(err))
  e.target.value = ''
}
function removeCommentImage(index) {
  commentImages.value.splice(index, 1)
}

// 上传回复图片
function uploadReplyImage(comment) {
  _replyImageTarget = comment
  if (replyImageInput.value) replyImageInput.value.click()
}
function handleReplyImageChange(e) {
  const file = e.target.files[0]
  if (!file) return
  const c = _replyImageTarget
  if (!c) return
  const formData = new FormData()
  formData.append('file', file)
  const config = {
    headers: { 'Content-Type': 'multipart/form-data;boundary=' + new Date().getTime() }
  }
  request.post('/upload/blog', formData, config)
    .then(res => {
      if (!c.replyImages) c.replyImages = []
      c.replyImages.push('/imgs' + res.data)
    })
    .catch(err => ElMessage.error(err))
  e.target.value = ''
}
function removeReplyImage(c, index) {
  c.replyImages.splice(index, 1)
}

function init() {
  container = swiperBox.value
  if (!container) return
  items = container.querySelectorAll('.swiper-item')
  if (items.length > 0) {
    const firstImg = items[0].querySelector('img')
    firstImg.onload = () => {
      container.style.height = firstImg.offsetHeight + 'px'
      items.forEach(item => {
        item.style.height = firstImg.offsetHeight + 'px'
      })
    }
  }
  updateItemWidth()
  setTransform()
  setTransition('none')
}

function toOtherInfo() {
  if (blog.value.userId === user.value.id) {
    router.push('/info')
  } else {
    router.push('/other-info?id=' + blog.value.userId)
  }
}

function queryBlogById(id) {
  request.get('/blog/' + id)
    .then(res => {
      const data = res.data
      data.images = data.images.split(',')
      blog.value = data
      nextTick(init)
      queryLikeList(id)
      queryLoginUser()
    })
    .catch(err => ElMessage.error(err))
}

function queryLikeList(id) {
  request.get('/blog/likes/' + id)
    .then(res => { likes.value = res.data })
    .catch(err => ElMessage.error(err))
}

function addLike() {
  const wasLiked = blog.value.isLike
  blog.value.isLike = !wasLiked
  blog.value.liked += wasLiked ? -1 : 1

  request.put('/blog/like/' + blog.value.id)
    .then(() => {
      request.get('/blog/' + blog.value.id)
        .then(res => {
          const data = res.data
          data.images = data.images.split(',')
          blog.value = data
          queryLikeList(blog.value.id)
        })
        .catch(err => ElMessage.error(err))
    })
    .catch(err => {
      blog.value.isLike = wasLiked
      blog.value.liked += wasLiked ? 1 : -1
      ElMessage.error(err)
    })
}

function isFollowed() {
  request.get('/follow/or/not/' + blog.value.userId)
    .then(res => { followed.value = res.data })
    .catch(err => ElMessage.error(err))
}

function follow() {
  request.put('/follow/' + blog.value.userId + '/' + !followed.value)
    .then(() => {
      ElMessage.success(followed.value ? '已取消关注' : '已关注')
      followed.value = !followed.value
    })
    .catch(err => ElMessage.error(err))
}

function formatTime(b) {
  return b.getFullYear() + '年' + (b.getMonth() + 1) + '月' + b.getDate() + '日 '
}

function queryLoginUser() {
  request.get('/user/me')
    .then(res => {
      user.value = res.data
      if (user.value.id !== blog.value.userId) {
        isFollowed()
      }
    })
    .catch(err => console.log(err))
}

function updateItemWidth() {
  _width = container.offsetWidth || document.documentElement.offsetWidth
}

function setTransform(offset) {
  offset = offset || 0
  each(items, (item, i) => {
    const distance = (i - active.value) * _width + offset
    const transform = `translate3d(${distance}px, 0, 0)`
    item.style.webkitTransform = transform
    item.style.transform = transform
  })
}

function setTransition(dur) {
  dur = dur || duration
  dur = typeof dur === 'number' ? (dur + 'ms') : dur
  each(items, (item) => {
    item.style.webkitTransition = dur
    item.style.transition = dur
  })
}

function moveStart(e) {
  start.x = e.changedTouches[0].pageX
  start.y = e.changedTouches[0].pageY
  setTransition('none')
}

function moving(e) {
  e.preventDefault()
  e.stopPropagation()
  let distanceX = e.changedTouches[0].pageX - start.x
  const distanceY = e.changedTouches[0].pageY - start.y

  if (Math.abs(distanceX) > Math.abs(distanceY)) {
    isMoving = true
    move.x = start.x + distanceX
    move.y = start.y + distanceY

    if ((active.value === 0 && distanceX > 0) || (active.value === (items.length - 1) && distanceX < 0)) {
      distanceX = distanceX * resistance
    }
    setTransform(distanceX)
  }
}

function moveEnd(e) {
  if (isMoving) {
    e.preventDefault()
    e.stopPropagation()
    const distance = move.x - start.x
    if (Math.abs(distance) > sensitivity) {
      if (distance < 0) {
        next()
      } else {
        prev()
      }
    } else {
      back()
    }
    reset()
    isMoving = false
  }
}

function next() {
  go(active.value + 1)
}

function prev() {
  go(active.value - 1)
}

function reset() {
  start.x = 0
  start.y = 0
  move.x = 0
  move.y = 0
}

function back() {
  setTransition()
  setTransform()
}

function go(index) {
  active.value = index
  if (active.value < 0) {
    active.value = 0
  } else if (active.value > items.length - 1) {
    active.value = items.length - 1
  }
  setTransition()
  setTransform()
}
</script>

<style scoped>
/* 样式合并自原 blog-detail.html <style> 内联块 + css/blog-detail.css，body/#app 全局重置不迁移 */
.page-container {
  /* 本页强调色统一走令牌；soft 是同色的低透明度底（令牌里没有半透明变体），
     用于标签底、图标底这类"淡色块"，避免各处再写散落的 rgba 字面量 */
  --bd-accent: var(--wt-color-brand-tint);
  --bd-accent-soft: rgba(142, 202, 230, 0.12);
}
.page-scroll {
  height: calc(100vh - 56px);
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  background: #f8f8f8;
}
.page-scroll::-webkit-scrollbar {
  width: 0;
  background: transparent;
}
.blog-info-box {
  position: relative;
  width: 100%;
  height: auto;
  background: #f8f8f8;
  overflow: hidden;
}
.swiper-item {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: auto;
  display: flex;
  align-items: center;
}
.swiper-item img {
  width: 100%;
  height: auto;
  display: block;
}
.basic {
  display: flex;
  align-items: center;
  padding: var(--wt-space-4) var(--wt-gutter);
  background: #fff;
  position: relative;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
}
.basic-icon {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  overflow: hidden;
  margin-right: 12px;
  border: 2px solid #fff;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
  flex-shrink: 0;
  cursor: pointer;
}
.basic-icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.basic-info {
  flex: 1;
  min-width: 0;
}
.basic-info .name {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.basic-info .time {
  font-size: 13px;
  color: #999;
}
.logout-btn {
  border-radius: 20px;
  background: rgba(142, 202, 230, 0.1);
  color: #8ecae6;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
  white-space: nowrap;
  flex-shrink: 0;
  margin-left: 12px;
  text-align: center;
}
.logout-btn:active {
  transform: scale(0.95);
  background: rgba(142, 202, 230, 0.2);
}
.blog-text {
  padding: var(--wt-space-5) var(--wt-gutter);
  font-size: 15px;
  line-height: 1.7;
  color: #333;
  background: #fff;
  /* &nbsp; 与超长英文串不换行会横向溢出，强制在词内断行 */
  word-break: break-word;
  overflow-wrap: anywhere;
}
.blog-text img {
  width: 100%;
  height: auto;
  margin: 10px 0;
  display: block;
}
/* ==========================================================================
   点赞区
   ------------------------------------------------------------------------
   左"图标 + 计数"为一个可点整体，右为头像叠压组，两端对齐且各自成组；
   计数用 tabular-nums 等宽数字，加一减一时数字不会左右抖动。
   ========================================================================== */
.zan-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wt-space-3);
  margin: var(--wt-space-4) var(--wt-gutter) 0;
  padding: var(--wt-space-4);
}
.zan-count {
  display: inline-flex;
  align-items: center;
  gap: var(--wt-space-2);
  padding: var(--wt-space-2) var(--wt-space-3);
  border-radius: var(--wt-radius-pill);
  background: var(--wt-bg-muted);
  color: var(--wt-text-2);
  line-height: 1;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease, transform 0.2s ease;
}
.zan-count:hover {
  background: var(--bd-accent-soft);
}
.zan-count:active {
  transform: scale(0.97);
}
.zan-count.is-liked {
  color: #ff6633;
  background: rgba(255, 102, 51, 0.1);
}
.zan-icon {
  display: block;
  flex: none;
}
.zan-count__num {
  font-size: var(--wt-fs-lg);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.zan-count__unit {
  font-size: var(--wt-fs-sm);
}
/* 头像叠压成一组，超出部分用 +N 收口，避免点赞人多时把这一行撑爆 */
.zan-avatars {
  display: flex;
  align-items: center;
  flex: none;
}
.zan-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  overflow: hidden;
  border: 2px solid var(--wt-bg-surface);
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
}
.zan-avatar + .zan-avatar,
.zan-more {
  margin-left: -8px;
}
.zan-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.zan-more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2px solid var(--wt-bg-surface);
  background: var(--wt-bg-muted);
  color: var(--wt-text-3);
  font-size: var(--wt-fs-xs);
}
/* ==========================================================================
   评论区
   ------------------------------------------------------------------------
   与点赞卡同走 margin-inline 版心；卡片间距统一 --wt-space-4，
   底部预留固定操作栏的高度，避免最后一条评论被遮挡。
   ========================================================================== */
.blog-comments {
  margin: var(--wt-space-4) var(--wt-gutter) var(--wt-page-bottom);
  padding: var(--wt-space-4);
}
.comments-head {
  display: flex;
  align-items: baseline;
  gap: var(--wt-space-2);
  margin-bottom: var(--wt-space-4);
}
.comments-title {
  font-size: var(--wt-fs-lg);
  font-weight: 600;
  color: var(--wt-text-1);
}
.comments-count {
  font-size: var(--wt-fs-sm);
  color: var(--wt-text-3);
}
/* ---- 评论项 ---- */
/* 头像与内容用 gap 对齐；分条交给分隔线，不再靠各处不等的 margin 区分 */
.comment-list {
  margin-top: var(--wt-space-4);
}
.comment-box {
  display: flex;
  align-items: flex-start;
  gap: var(--wt-space-3);
}
.comment-box + .comment-box {
  margin-top: var(--wt-space-4);
  padding-top: var(--wt-space-4);
  border-top: 1px solid var(--wt-border);
}
.comment-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  flex: none;
}
.comment-icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
/* ---- 发表评论 ---- */
/* 淡色底 + 描边与白卡拉开层级：原先是"白底叠白底"，只剩阴影可辨 */
.comment-input-box {
  display: flex;
  align-items: flex-start;
  gap: var(--wt-space-3);
  padding: var(--wt-space-3);
  border: 1px solid var(--wt-border);
  border-radius: var(--wt-radius-sm);
  background: var(--wt-bg-muted);
  animation: slideUp 0.3s ease;
}
.comment-input-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  flex: none;
}
.comment-input-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.comment-input-content {
  flex: 1;
  min-width: 0;
}
.comment-input-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wt-space-3);
  margin-top: var(--wt-space-2);
}
.comment-info {
  flex: 1;
  min-width: 0;
}
.comment-user {
  display: flex;
  align-items: center;
  gap: var(--wt-space-2);
  font-size: var(--wt-fs-md);
  font-weight: 600;
  color: var(--wt-text-1);
  line-height: 1.4;
}
.comment-user__level {
  padding: 1px var(--wt-space-2);
  border-radius: var(--wt-radius-pill);
  font-size: var(--wt-fs-xs);
  font-weight: 500;
  color: var(--bd-accent);
  background: var(--bd-accent-soft);
}
.comment-score {
  display: flex;
  align-items: center;
  gap: var(--wt-space-2);
  margin-top: var(--wt-space-2);
  font-size: var(--wt-fs-sm);
  color: var(--wt-text-3);
}
.comment-content {
  margin-top: var(--wt-space-2);
  font-size: var(--wt-fs-md);
  line-height: 1.7;
  color: var(--wt-text-2);
  word-break: break-word;
}
.comment-images {
  display: flex;
  gap: var(--wt-space-2);
  margin-top: var(--wt-space-2);
  overflow-x: auto;
  padding-bottom: var(--wt-space-1);
}
.comment-images img {
  flex: none;
  width: 88px;
  height: 88px;
  border-radius: var(--wt-radius-sm);
  object-fit: cover;
}
.comment-actions {
  display: flex;
  align-items: center;
  gap: var(--wt-space-4);
  margin-top: var(--wt-space-2);
  font-size: var(--wt-fs-xs);
  color: var(--wt-text-3);
}
.comment-reply-btn,
.reply-btn {
  color: var(--bd-accent);
  font-weight: 500;
  cursor: pointer;
}
.comment-reply-btn:hover,
.reply-btn:hover {
  color: var(--wt-color-brand);
}
/* ---- 回复 ---- */
/* 整体缩进 + 淡色底，与主评论形成明确的父子层次；回复之间用分隔线分条 */
.reply-list {
  margin-top: var(--wt-space-3);
  padding: var(--wt-space-3);
  border-radius: var(--wt-radius-sm);
  background: var(--wt-bg-muted);
}
.reply-item + .reply-item {
  margin-top: var(--wt-space-3);
  padding-top: var(--wt-space-3);
  border-top: 1px solid var(--wt-border);
}
.reply-user {
  font-size: var(--wt-fs-sm);
  line-height: 1.5;
  color: var(--wt-text-2);
}
.reply-username,
.reply-to-username {
  color: var(--bd-accent);
  font-weight: 600;
}
.reply-content {
  margin-top: var(--wt-space-1);
  font-size: var(--wt-fs-md);
  line-height: 1.7;
  color: var(--wt-text-1);
  word-break: break-word;
}
.reply-images {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wt-space-2);
  margin-top: var(--wt-space-2);
}
.reply-images img {
  width: 64px;
  height: 64px;
  border-radius: var(--wt-radius-sm);
  object-fit: cover;
}
.reply-actions {
  display: flex;
  align-items: center;
  gap: var(--wt-space-4);
  margin-top: var(--wt-space-2);
  font-size: var(--wt-fs-xs);
  color: var(--wt-text-3);
}
.reply-input-box {
  margin-top: var(--wt-space-3);
  padding: var(--wt-space-3);
  border: 1px solid var(--wt-border);
  border-radius: var(--wt-radius-sm);
  background: var(--wt-bg-surface);
}
/* 原模板有 .reply-input-actions 这个类名但一直没有样式，
   导致回复框里的"上传图标 / 回复按钮"是一段普通块级流，这里补上对齐规则 */
.reply-input-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wt-space-3);
  margin-top: var(--wt-space-2);
}
/* ---- 图片上传 ---- */
.upload-images {
  display: flex;
  align-items: center;
  gap: var(--wt-space-2);
  min-width: 0;
}
.upload-images .el-icon {
  flex: none;
  width: 32px;
  height: 32px;
  font-size: 18px;
  color: var(--bd-accent);
  cursor: pointer;
  border-radius: 50%;
  background: var(--wt-bg-surface);
  transition: background-color 0.2s ease;
}
.upload-images .el-icon:hover {
  background: var(--bd-accent-soft);
}
.preview-images {
  display: flex;
  align-items: center;
  gap: var(--wt-space-2);
  min-width: 0;
  overflow-x: auto;
}
.preview-image-item {
  position: relative;
  flex: none;
  width: 48px;
  height: 48px;
  border-radius: var(--wt-radius-sm);
  overflow: hidden;
}
.preview-image-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.preview-close {
  position: absolute;
  top: 2px;
  right: 2px;
  padding: 2px;
  border-radius: 50%;
  background: rgba(0,0,0,0.5);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}
/* ==========================================================================
   底部固定操作栏
   ------------------------------------------------------------------------
   注意：main.css 里有一套全局底部导航栏（同款 tabbar）也占用了
   .foot / .foot-box / .foot-view 这三个类名，那套是 display:flex + flex:1 +
   水平居中。这里必须显式覆盖，否则本页的点赞按钮会浮到屏幕正中、且高度被锁死 50px。
   ========================================================================== */
.foot {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  height: auto;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 100;
  padding: var(--wt-space-2) var(--wt-gutter);
  padding-bottom: calc(var(--wt-space-2) + env(safe-area-inset-bottom, 0px));
  background: var(--wt-bg-surface);
  box-shadow: 0 -1px 3px rgba(0,0,0,0.05);
}
.foot-box {
  /* 全局那套同时给了 flex:1 / width:20% / flex-direction:column / align-items:center，
     结果是按钮即便靠左了，也会在这个 20% 宽的盒子里被再居中一次，必须逐项覆盖 */
  flex: none;
  width: auto;
  flex-direction: row;
  display: flex;
  align-items: center;
  justify-content: flex-start;
}
.foot-view {
  display: inline-flex;
  align-items: center;
  gap: var(--wt-space-2);
  height: 36px;
  padding: 0 var(--wt-space-4);
  border-radius: var(--wt-radius-pill);
  background: var(--wt-bg-muted);
  font-size: var(--wt-fs-md);
  color: var(--wt-text-2);
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease, transform 0.2s ease;
}
.foot-view:hover {
  background: var(--bd-accent-soft);
}
.foot-view:active {
  transform: scale(0.97);
}
.foot-view span.liked {
  color: #ff6633;
}
/* ---- 评论卡底部的"查看全部" ---- */
.comment-more {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wt-space-2);
  margin-top: var(--wt-space-4);
  padding-top: var(--wt-space-3);
  border-top: 1px solid var(--wt-border);
  font-size: var(--wt-fs-sm);
  color: var(--wt-text-3);
  cursor: pointer;
}
:deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  background: var(--wt-bg-surface);
  border-radius: var(--wt-radius-sm);
  padding: var(--wt-space-3);
  font-size: var(--wt-fs-md);
  resize: none;
}
:deep(.el-rate) {
  display: inline-flex;
  height: auto;
  line-height: 1;
}
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes slideUp {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
/* ---- 窄屏：收紧内边距与图元尺寸 ---- */
@media (max-width: 480px) {
  .zan-box,
  .blog-comments {
    padding: var(--wt-space-3);
  }
  .comment-input-box {
    padding: var(--wt-space-2);
  }
  .comment-icon,
  .comment-input-avatar {
    width: 32px;
    height: 32px;
  }
  .comment-images img {
    width: 72px;
    height: 72px;
  }
  .reply-images img {
    width: 56px;
    height: 56px;
  }
}
</style>
