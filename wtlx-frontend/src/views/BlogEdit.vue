<template>
  <div class="blog-edit-page">
    <div class="header">
      <div class="header-cancel-btn" @click="goBack">取消</div>
      <div class="header-title">&nbsp;&nbsp;发游记</div>
      <div class="header-commit">
        <div class="header-commit-btn" @click="submitBlog">发布</div>
      </div>
    </div>
    <div class="upload-box">
      <input type="file" @change="fileSelected" name="file" ref="fileInput" style="display: none">
      <div class="upload-btn" @click="openFileDialog">
        <el-icon><Camera /></el-icon>
        <div style="font-size: 12px;line-height: 12px">上传照片</div>
      </div>
      <div class="pic-list">
        <div class="pic-box" v-for="(f, i) in fileList" :key="i">
          <img :src="f" alt="">
          <el-icon class="pic-close" @click="deletePic(i)"><Close /></el-icon>
        </div>
      </div>
    </div>
    <div class="blog-title">
      <input v-model="params.title" type="text" placeholder="填写标题更容易上首页哦~">
    </div>
    <div class="blog-content">
      <textarea v-model="params.content" placeholder="最近打卡了什么地方，有什么新奇体验呢？"></textarea>
    </div>
    <div class="divider"></div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../api/request'

const router = useRouter()

const fileList = ref([])
const params = reactive({})
const shops = ref([])
const shopName = ref('')
const selectedShop = ref({})
const fileInput = ref(null)

onMounted(() => {
  queryShops()
})

function queryShops() {
  request.get('/shop/of/name?name=' + shopName.value)
    .then(res => { shops.value = res.data })
    .catch(err => ElMessage.error(err))
}

function submitBlog() {
  if (!params.title || !params.title.trim()) {
    ElMessage.error('请填写游记标题')
    return
  }
  if (!params.content || !params.content.trim()) {
    ElMessage.error('请填写游记内容')
    return
  }

  const data = { ...params }
  data.images = fileList.value.join(',')
  data.shopId = selectedShop.value.id
  request.post('/blog', data)
    .then(() => router.push('/info'))
    .catch(err => ElMessage.error(err))
}

function openFileDialog() {
  if (fileInput.value) fileInput.value.click()
}

function fileSelected(e) {
  const file = fileInput.value.files[0]
  const formData = new FormData()
  formData.append('file', file)
  const config = {
    headers: { 'Content-Type': 'multipart/form-data;boundary=' + new Date().getTime() }
  }
  request.post('/upload/blog', formData, config)
    .then(res => fileList.value.push('/imgs' + res.data))
    .catch(err => ElMessage.error(err))
  e.target.value = ''
}

function deletePic(i) {
  request.get('/upload/blog/delete?name=' + fileList.value[i])
    .then(() => fileList.value.splice(i, 1))
    .catch(err => ElMessage.error(err))
}

function goBack() {
  router.back()
}
</script>

<style scoped>
/* 样式合并自原 blog-edit.html <style> 内联块 + css/blog-edit.css */
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
  padding: 20px 15px;
  background: linear-gradient(145deg, #fbfdff, #f5faff);
  border-radius: 12px;
  margin: 15px;
  box-shadow: 0 4px 12px rgba(69,162,243,0.08);
  display: flex;
  align-items: flex-start;
  overflow-x: auto;
}
.upload-btn {
  flex-shrink: 0;
  width: 70px;
  height: 125px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 2px dashed #cfe7ff;
  background: rgba(255,255,255,0.9);
  border-radius: 5px;
  font-size: 30px;
  color: #82848a;
  margin-right: 10px;
  cursor: pointer;
}
.upload-btn .el-icon {
  font-size: 28px;
  margin-bottom: 6px;
}
.pic-list {
  display: flex;
  overflow-x: auto;
}
.pic-box {
  position: relative;
  flex-shrink: 0;
  width: 100px;
  height: 120px;
  border-radius: 5px;
  margin-right: 8px;
  overflow: hidden;
  border: 1px solid #c0ccda;
}
.pic-box img {
  width: 100px;
  height: 120px;
  object-fit: cover;
}
.pic-close {
  position: absolute;
  top: 4px;
  right: 4px;
  background: rgba(0,0,0,0.5);
  color: #fff;
  padding: 4px;
  border-radius: 50%;
  cursor: pointer;
  z-index: 99;
}
.blog-title input {
  width: 100%;
  box-sizing: border-box;
  padding: 16px 20px;
  font-size: 18px;
  border: none;
  border-bottom: 1px solid #eee;
  outline: none;
}
.blog-content textarea {
  width: 100%;
  box-sizing: border-box;
  height: 300px;
  padding: 20px;
  border: none;
  resize: none;
  font-size: 15px;
  line-height: 1.6;
  outline: none;
}
input::placeholder {
  color: #cccccc;
}
textarea::placeholder {
  color: #cccccc;
}
.divider {
  background-color: #f4f4f5;
  height: 10px;
}
</style>
