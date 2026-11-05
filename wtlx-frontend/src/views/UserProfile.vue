<template>
  <div class="other-info-page">
    <HeaderBar title="用户主页" :show-back="true" />
    <div class="basic">
      <div class="basic-icon">
        <img :src="user.icon || '/imgs/icons/default-icon.jpg'" alt="">
      </div>
      <div class="basic-info">
        <div class="name">{{ user.nickName }}</div>
      </div>
      <div class="logout-btn" @click="follow" style="text-align: center">
        {{ followed ? '取消关注' : '关注' }}
      </div>
    </div>
    <div class="content">
      <el-tabs v-model="activeName" @tab-click="handleClick">
        <el-tab-pane label="游记" name="1">
          <div v-for="b in blogs" :key="b.id" class="blog-item">
            <div class="blog-img"><img :src="b.images.split(',')[0]" alt=""></div>
            <div class="blog-info">
              <div class="blog-title" v-html="b.title"></div>
              <div class="blog-liked"><img src="/imgs/thumbup.png" alt=""> {{ b.liked }}</div>
              <div class="blog-comments"><el-icon><ChatDotRound /></el-icon> {{ b.comments }}</div>
            </div>
          </div>
        </el-tab-pane>
        <el-tab-pane label="共同关注" name="2">
          <div v-if="commonFollows && commonFollows.length > 0">
            <div class="follow-info" v-for="u in commonFollows" :key="u.id">
              <div class="follow-info-icon" @click="toOtherInfo(u.id)">
                <img :src="u.icon || '/imgs/icons/default-icon.jpg'" alt="">
              </div>
              <div class="follow-info-name">
                <div class="name">{{ u.nickName }}</div>
              </div>
              <div class="follow-info-btn" @click="toOtherInfo(u.id)">
                去主页看看
              </div>
            </div>
          </div>
          <div v-else class="empty-state" style="text-align: center; padding: 40px 0;">
            <el-icon style="font-size: 48px; color: #909399; margin-bottom: 16px;"><User /></el-icon>
            <p style="color: #909399;">暂无共同关注</p>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
    <FootBar :active-btn="0" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import HeaderBar from '../components/HeaderBar.vue'
import FootBar from '../components/FootBar.vue'
import request from '../api/request'
import { getUrlParam } from '../utils/index'

const router = useRouter()

const user = ref({})
const loginUser = ref({})
const activeName = ref('1')
const info = ref({})
const blogs = ref([])
const followed = ref(false)
const commonFollows = ref([])

onMounted(() => {
  queryUser()
  queryLoginUser()
})

function queryBlogs() {
  request.get('/blog/of/user', { params: { id: user.value.id, current: 1 } })
    .then(res => { blogs.value = res.data })
    .catch(err => ElMessage.error(err))
}

function queryLoginUser() {
  request.get('/user/me')
    .then(res => { loginUser.value = res.data })
    .catch(err => console.log(err))
}

function queryUser() {
  const id = getUrlParam('id')
  request.get('/user/' + id)
    .then(res => {
      user.value = res.data
      queryUserInfo()
      queryBlogs()
      isFollowed()
    })
    .catch(err => console.log(err))
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

function isFollowed() {
  request.get('/follow/or/not/' + user.value.id)
    .then(res => { followed.value = res.data })
    .catch(err => ElMessage.error(err))
}

function queryCommonFollow() {
  request.get('/follow/common/' + user.value.id)
    .then(res => { commonFollows.value = res.data })
    .catch(err => ElMessage.error(err))
}

function follow() {
  request.put('/follow/' + user.value.id + '/' + !followed.value)
    .then(() => {
      ElMessage.success(followed.value ? '已取消关注' : '已关注')
      followed.value = !followed.value
    })
    .catch(err => ElMessage.error(err))
}

function handleClick(tab) {
  if (tab.name === '2') {
    queryCommonFollow()
  }
}

function toOtherInfo(id) {
  router.push('/user-profile?id=' + id)
}
</script>

<style scoped>
/* 样式合并自原 other-info.html <style> 内联块 + css/info.css；body/#app 全局重置不迁移 */
.other-info-page {
  background: #f5f7fa;
  min-height: 100vh;
}
.basic {
  padding: 70px 16px 20px;
  display: flex;
  align-items: center;
  background: linear-gradient(to bottom, #409EFF, #fff);
  position: relative;
}
.basic-icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  border: 3px solid #fff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  flex-shrink: 0;
}
.basic-icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.basic-info {
  flex: 1;
  margin-left: 16px;
  color: #fff;
}
.basic-info .name {
  font-size: 20px;
  font-weight: 500;
  margin-bottom: 4px;
}
.logout-btn {
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.2);
  transition: all 0.3s;
  flex-shrink: 0;
}
.content {
  padding: 16px;
  margin-top: 16px;
}
:deep(.el-tabs__nav) {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}
:deep(.el-tabs__item) {
  font-size: 14px;
  padding: 0 20px;
  height: 40px;
  line-height: 40px;
}
.blog-item {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  display: flex;
}
.blog-img {
  width: 40%;
  padding-bottom: 56.25%;
  position: relative;
  overflow: hidden;
  flex-shrink: 0;
}
.blog-img img {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.blog-info {
  flex: 1;
  padding: 12px 16px;
}
.blog-title {
  font-size: 14px;
  font-weight: normal;
  color: #606266;
  margin-bottom: 8px;
  line-height: 1.5;
  height: 42px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-all;
}
.blog-liked,
.blog-comments {
  display: inline-flex;
  align-items: center;
  color: #909399;
  font-size: 12px;
  margin-right: 12px;
}
.blog-liked img {
  width: 16px;
  height: 16px;
  margin-right: 4px;
}
.blog-comments .el-icon {
  margin-right: 4px;
  font-size: 12px;
}
.follow-info {
  display: flex;
  align-items: center;
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  margin-bottom: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}
.follow-info-icon {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  overflow: hidden;
  margin-right: 16px;
  border: 1px solid #ebeef5;
  cursor: pointer;
  flex-shrink: 0;
}
.follow-info-icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.follow-info-name {
  flex: 1;
}
.follow-info-name .name {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
}
.follow-info-btn {
  padding: 6px 12px;
  background: #409EFF;
  color: #fff;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
}
:deep(.el-tabs__content) {
  padding: 20px 0;
}
</style>
