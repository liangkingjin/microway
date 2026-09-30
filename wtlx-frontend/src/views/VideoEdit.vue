<template>
  <div class="video-edit-page">
    <div class="header">
      <div class="header-cancel-btn" @click="goBack">取消</div>
      <div class="header-title">&nbsp;&nbsp;发布视频<el-icon style="margin-left:4px;"><VideoCamera /></el-icon></div>
      <div class="header-commit">
        <div class="header-commit-btn" @click="submitVideo">发布</div>
      </div>
    </div>
    <div class="upload-box">
      <input type="file" accept="video/*" @change="videoSelected" name="file" ref="videoInput" style="display: none">
      <div class="upload-btn" @click="openVideoDialog" v-if="!videoUrl">
        <el-icon><VideoCamera /></el-icon>
        <div style="font-size: 12px;line-height: 12px">{{ uploading ? '上传中...' : '上传视频' }}</div>
      </div>
      <div class="video-preview" v-if="videoUrl" style="position: relative;">
        <video :src="videoUrl" controls style="width: 100%;max-height:200px;"></video>
        <el-icon class="video-close" @click="deleteVideo"><Close /></el-icon>
      </div>
    </div>
    <div class="blog-content">
      <textarea v-model="params.description" placeholder="请填写视频描述"></textarea>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request, { uploadRequest } from '../api/request'

const router = useRouter()

const videoUrl = ref('')
const videoFileName = ref('')
const params = reactive({ description: '' })
const videoInput = ref(null)
const uploading = ref(false)
const submitting = ref(false)

function openVideoDialog() {
  if (videoInput.value) videoInput.value.click()
}

function videoSelected(e) {
  const file = videoInput.value.files[0]
  if (!file) return
  const formData = new FormData()
  formData.append('file', file)
  const config = {
    headers: { 'Content-Type': 'multipart/form-data;boundary=' + new Date().getTime() }
  }
  uploading.value = true
  uploadRequest.post('/upload/video', formData, config)
    .then(res => {
      videoFileName.value = res.data
      videoUrl.value = '/video' + res.data
    })
    .catch(err => ElMessage.error(err))
    .finally(() => { uploading.value = false })
  e.target.value = ''
}

function deleteVideo() {
  if (!videoFileName.value) return
  request.get('/upload/video/delete?name=' + encodeURIComponent(videoFileName.value))
    .then(() => {
      videoUrl.value = ''
      videoFileName.value = ''
      if (videoInput.value) videoInput.value.value = ''
    })
    .catch(err => ElMessage.error(err))
}

function submitVideo() {
  if (!videoFileName.value) {
    ElMessage.warning('请先上传视频')
    return
  }
  if (submitting.value) return
  submitting.value = true
  const data = {
    description: params.description,
    videoUrl: videoUrl.value,
    coverUrl: ''
  }
  request.post('/videos', data)
    .then(() => {
      ElMessage.success('发布成功')
      setTimeout(() => router.push('/video'), 800)
    })
    .catch(err => ElMessage.error(err))
    .finally(() => { submitting.value = false })
}

function goBack() {
  router.back()
}
</script>

<style scoped>
/* 样式迁移自原 video-edit.html <style> 内联块 + css/blog-edit.css */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 50px;
  padding: 0 15px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  position: relative;
  z-index: 1000;
}
.header-cancel-btn {
  color: #666;
  font-size: 16px;
  font-weight: bold;
  cursor: pointer;
}
.header-title {
  flex: 1;
  text-align: center;
  font-size: 18px;
  font-weight: 600;
  color: #333;
}
.header-commit-btn {
  background: #45a2f3;
  color: #fff;
  padding: 6px 20px;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
}
.upload-box {
  padding: 15px;
  display: flex;
  align-items: flex-start;
}
.upload-btn {
  width: 70px;
  height: 125px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 1px dashed #3a8ee6;
  border-radius: 5px;
  font-size: 30px;
  color: #82848a;
  margin-right: 10px;
  cursor: pointer;
}
.video-preview {
  flex: 1;
}
.video-close {
  position: absolute;
  top: 5px;
  right: 5px;
  cursor: pointer;
  color: #fff;
  background: rgba(0,0,0,0.5);
  border-radius: 50%;
  padding: 2px;
  z-index: 2;
}
.blog-content {
  padding: 5px 15px;
}
.blog-content textarea {
  width: 100%;
  box-sizing: border-box;
  height: 200px;
  border: 0;
  outline: none;
}
textarea::placeholder {
  color: #cccccc;
}
</style>
