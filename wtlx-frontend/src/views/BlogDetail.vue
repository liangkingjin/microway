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
      <div class="blog-text" v-html="blog.content"></div>
      <div class="zan-box">
        <div>
          <svg t="1646634642977" class="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" width="20" height="20">
            <path d="M160 944c0 8.8-7.2 16-16 16h-32c-26.5 0-48-21.5-48-48V528c0-26.5 21.5-48 48-48h32c8.8 0 16 7.2 16 16v448zM96 416c-53 0-96 43-96 96v416c0 53 43 96 96 96h96c17.7 0 32-14.3 32-32V448c0-17.7-14.3-32-32-32H96zM505.6 64c16.2 0 26.4 8.7 31 13.9 4.6 5.2 12.1 16.3 10.3 32.4l-23.5 203.4c-4.9 42.2 8.6 84.6 36.8 116.4 28.3 31.7 68.9 49.9 111.4 49.9h271.2c6.6 0 10.8 3.3 13.2 6.1s5 7.5 4 14l-48 303.4c-6.9 43.6-29.1 83.4-62.7 112C815.8 944.2 773 960 728.9 960h-317c-33.1 0-59.9-26.8-59.9-59.9v-455c0-6.1 1.7-12 5-17.1 69.5-109 106.4-234.2 107-364h41.6z m0-64h-44.9C427.2 0 400 27.2 400 60.7c0 127.1-39.1 251.2-112 355.3v484.1c0 68.4 55.5 123.9 123.9 123.9h317c122.7 0 227.2-89.3 246.3-210.5l47.9-303.4c7.8-49.4-30.4-94.1-80.4-94.1H671.6c-50.9 0-90.5-44.4-84.6-95l23.5-203.4C617.7 55 568.7 0 505.6 0z" :fill="blog.isLike ? '#ff6633' : '#82848a'"></path>
          </svg>
        </div>
        <div class="zan-list">
          <div class="user-icon-mini" v-for="u in likes" :key="u.id">
            <img :src="u.icon || '/imgs/icons/default-icon.jpg'" alt="">
          </div>
          <div style="margin-left:10px;text-align: center;line-height: 24px;">{{ blog.liked }}人点赞</div>
        </div>
      </div>
      <div class="blog-divider"></div>
      <div class="blog-comments">
        <div class="comments-head">
          <div>网友评价 <span>（{{ comments.length }}）</span></div>
          <div><el-icon><ArrowRight /></el-icon></div>
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
              <div class="comment-user">{{ comment.userName }} <span>Lv{{ comment.userLevel }}</span></div>
              <div style="display: flex;" v-if="comment.score">
                打分
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
          <div v-if="comments.length > 0"
            style="display: flex; justify-content: space-between;padding: 15px 0; border-top: 1px solid #f1f1f1; margin-top: 10px;">
            <div>查看全部{{ comments.length }}条评价</div>
            <div><el-icon><ArrowRight /></el-icon></div>
          </div>
          <div v-else class="no-comments">
            <el-icon style="font-size:48px;color:#8ecae6;opacity:.5;"><ChatDotRound /></el-icon>
            <p>暂无评论，快来发表第一条评论吧</p>
          </div>
        </div>
      </div>
      <div class="blog-divider"></div>
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
.page-scroll {
  height: calc(100vh - 56px);
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  background: #f8f8f8;
  padding-top: 44px;
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
  padding: 16px;
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
  padding: 20px 16px;
  font-size: 15px;
  line-height: 1.6;
  color: #333;
  background: #fff;
}
.blog-text img {
  width: 100%;
  height: auto;
  margin: 10px 0;
  display: block;
}
.zan-box {
  padding: 16px;
  background: #fff;
  margin-top: 10px;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.zan-list {
  display: flex;
  align-items: center;
  margin-top: 12px;
  flex-wrap: wrap;
}
.user-icon-mini {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  overflow: hidden;
  margin-right: 8px;
  margin-bottom: 8px;
  border: 2px solid #fff;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}
.user-icon-mini img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.blog-divider {
  height: 8px;
  background: #f8f8f8;
}
.blog-comments {
  background: #fff;
  margin-top: 10px;
  padding: 16px;
  border-radius: 12px;
}
.comments-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}
.comments-head span {
  color: #999;
  font-weight: normal;
  margin-left: 4px;
}
.comment-box {
  display: flex;
  margin-bottom: 20px;
  position: relative;
}
.comment-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  margin-right: 12px;
  flex-shrink: 0;
}
.comment-icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.comment-input-box {
  display: flex;
  margin: 15px 0;
  padding: 16px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
  animation: slideUp 0.3s ease;
}
.comment-input-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  margin-right: 12px;
  flex-shrink: 0;
}
.comment-input-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.comment-input-content {
  flex: 1;
}
.comment-input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.comment-info {
  flex: 1;
}
.comment-user {
  font-size: 15px;
  font-weight: 500;
  color: #333;
  margin-bottom: 4px;
}
.comment-user span {
  font-size: 12px;
  color: #8ecae6;
  background: rgba(142, 202, 230, 0.1);
  padding: 2px 6px;
  border-radius: 10px;
  margin-left: 8px;
}
.comment-content {
  font-size: 14px;
  line-height: 1.6;
  color: #666;
  margin: 8px 0;
}
.comment-images {
  display: flex;
  width: 100%;
  overflow-x: scroll;
  padding: 10px 0;
}
.comment-images img {
  height: 94px;
  width: 92px;
  border-radius: 5px;
  margin-right: 5px;
}
.comment-actions {
  display: flex;
  align-items: center;
  font-size: 12px;
  color: #999;
  margin-top: 8px;
}
.comment-time {
  margin-right: 16px;
}
.comment-reply-btn {
  color: #8ecae6;
  cursor: pointer;
}
.reply-list {
  background: #f8f8f8;
  padding: 12px;
  margin-top: 12px;
  border-radius: 8px;
}
.reply-item {
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(0,0,0,0.05);
}
.reply-item:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
}
.reply-user {
  font-size: 13px;
  margin-bottom: 4px;
  color: #666;
}
.reply-username,
.reply-to-username {
  color: #8ecae6;
  font-weight: 500;
}
.reply-content {
  font-size: 14px;
  line-height: 1.6;
  color: #333;
  word-break: break-all;
}
.reply-images {
  display: flex;
  flex-wrap: wrap;
  margin: 8px 0;
}
.reply-images img {
  width: 64px;
  height: 64px;
  object-fit: cover;
  margin-right: 8px;
  margin-bottom: 8px;
  border-radius: 8px;
}
.reply-actions {
  display: flex;
  align-items: center;
  font-size: 12px;
  color: #999;
  margin-top: 8px;
}
.reply-btn {
  color: #8ecae6;
  cursor: pointer;
}
.reply-input-box {
  margin-top: 12px;
  padding: 12px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid rgba(0,0,0,0.05);
}
.upload-images {
  display: flex;
  align-items: center;
  margin-top: 12px;
}
.upload-images .el-icon {
  font-size: 20px;
  color: #8ecae6;
  cursor: pointer;
  padding: 8px;
  border-radius: 50%;
  background: rgba(142, 202, 230, 0.1);
}
.preview-images {
  display: flex;
  flex-wrap: wrap;
  margin-left: 12px;
}
.preview-image-item {
  width: 48px;
  height: 48px;
  margin-right: 8px;
  margin-bottom: 8px;
  position: relative;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}
.preview-image-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.preview-close {
  position: absolute;
  top: 4px;
  right: 4px;
  background: rgba(0,0,0,0.5);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}
.no-comments {
  text-align: center;
  padding: 40px 0;
  color: #999;
}
.no-comments p {
  font-size: 14px;
}
.foot {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #fff;
  padding: 8px 16px;
  display: flex;
  align-items: center;
  box-shadow: 0 -1px 3px rgba(0,0,0,0.05);
  z-index: 100;
}
.foot-box {
  display: flex;
  align-items: center;
}
.foot-view {
  display: flex;
  align-items: center;
  padding: 8px 16px;
  border-radius: 20px;
  background: #f8f8f8;
  cursor: pointer;
}
.foot-view span {
  font-size: 14px;
  color: #666;
  margin-left: 6px;
}
.foot-view span.liked {
  color: #ff6633;
}
:deep(.el-textarea__inner) {
  border: none;
  background: #f8f8f8;
  border-radius: 8px;
  padding: 12px;
  font-size: 14px;
  resize: none;
}
:deep(.el-rate) {
  display: inline-flex;
  margin-left: 8px;
}
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes slideUp {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
</style>
