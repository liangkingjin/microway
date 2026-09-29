<template>
  <div class="not-found">
    <h1>404</h1>
    <p>页面不存在</p>
    <el-button type="primary" @click="$router.push('/index')">返回首页</el-button>
  </div>
</template>

<script setup>
</script>

<style scoped>
.not-found {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: #f8f8f8;
}
.not-found h1 {
  font-size: 64px;
  color: #409EFF;
  margin: 0;
}
.not-found p {
  color: #666;
  font-size: 16px;
}
</style>
