<template>
  <div class="wt-card wt-empty">
    <slot name="icon">
      <el-icon class="wt-empty__icon"><Box /></el-icon>
    </slot>
    <p class="wt-empty__text">{{ text }}</p>
  </div>
</template>

<script setup>
import { Box } from '@element-plus/icons-vue'

// 通用空状态（列表为空时展示，图标通过 icon 插槽传入）
defineProps({
  text: { type: String, default: '暂无数据' }
})
</script>

<style scoped>
/* 外观完全复用公共类 .wt-card / .wt-empty，本组件无需额外样式 */
</style>
