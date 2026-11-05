<template>
  <div class="header">
    <div class="header-back-btn" v-if="showBack" @click="onBack">
      <el-icon><ArrowLeft /></el-icon>
    </div>
    <div class="header-title">
      <slot>{{ title }}</slot>
    </div>
    <div class="header-right" v-if="$slots.right">
      <slot name="right" />
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

// 公共头部（原各页内嵌 div.header + .header-back-btn + .header-title 结构的统一样式）
// 与原 header.css 全局样式配套；自定义头部页（如首页搜索头、AI 页）保留内联结构，不使用本组件
const props = defineProps({
  title: { type: String, default: '' },
  showBack: { type: Boolean, default: true }
})

const router = useRouter()
function onBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/home')
  }
}
</script>

<style scoped>
.header-right {
  margin-left: auto;
}
</style>
