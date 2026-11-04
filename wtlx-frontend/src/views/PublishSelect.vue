<template>
  <div class="page-container">
    <HeaderBar title="选择发布类型" />

    <div class="publish-select-container">
      <div class="publish-header">
        <div class="publish-title">选择您要发布的内容类型</div>
        <div class="publish-subtitle">分享您的旅行故事</div>
      </div>

      <div class="publish-options">
        <div class="publish-option" @click="toBlogEdit">
          <div class="option-icon">
            <el-icon><Notebook /></el-icon>
          </div>
          <div class="option-content">
            <div class="option-title">发布游记</div>
            <div class="option-desc">分享您的旅行故事、照片和体验，帮助其他旅行者发现精彩目的地</div>
          </div>
          <div class="option-arrow">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import HeaderBar from '../components/HeaderBar.vue'

const router = useRouter()

function toBlogEdit() {
  router.push('/blog-edit')
}
</script>

<style scoped>
.page-container {
  min-height: 100vh;
}
.publish-select-container {
  padding: 20px;
  background: #f8f8f8;
  min-height: calc(100vh - 56px);
}
.publish-header {
  text-align: center;
  margin-bottom: 30px;
}
.publish-title {
  font-size: 22px;
  font-weight: 600;
  color: #333;
  margin-bottom: 10px;
}
.publish-subtitle {
  font-size: 14px;
  color: #999;
}
.publish-options {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 0 15px;
}
.publish-option {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  transition: transform 0.3s, box-shadow 0.3s;
  cursor: pointer;
}
.publish-option:hover {
  transform: translateY(-3px);
  box-shadow: 0 5px 15px rgba(0, 0, 0, 0.1);
}
.option-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20px;
}
.option-icon .el-icon {
  font-size: 30px;
  color: #409EFF;
}
.option-content {
  flex: 1;
}
.option-title {
  font-size: 18px;
  font-weight: 500;
  color: #333;
  margin-bottom: 8px;
}
.option-desc {
  font-size: 14px;
  color: #666;
  line-height: 1.5;
}
.option-arrow {
  color: #999;
  font-size: 20px;
  margin-left: 10px;
  display: flex;
  align-items: center;
}
</style>
