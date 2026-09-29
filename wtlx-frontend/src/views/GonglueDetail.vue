<template>
  <div class="page-container">
    <HeaderBar title="攻略详情" :show-back="true" />

    <img v-if="image" :src="image" alt="攻略图片" class="article-image">

    <div class="article-content" v-html="guideData"></div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import HeaderBar from '../components/HeaderBar.vue'
import request from '../api/request'
import { getUrlParam } from '../utils/index'

const guideData = ref('')
const image = ref('')

function fetchGuideData() {
  const guideId = getUrlParam('id')
  request.get('/guides', {
    params: {
      id: guideId
    }
  })
    .then(res => {
      const data = res.data || {}
      guideData.value = data.content
      image.value = data.images
    })
    .catch(error => {
      console.error('获取攻略数据失败:', error)
    })
}

onMounted(() => {
  fetchGuideData()
})
</script>

<style scoped>
.page-container {
  background: #f8f8f8;
  min-height: 100vh;
  padding: 0 15px 20px;
}
.article-image {
  max-width: 100%;
  height: auto;
  margin: 20px 0;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}
.article-content {
  line-height: 1.8;
  font-size: 15px;
  color: #333;
}
</style>
