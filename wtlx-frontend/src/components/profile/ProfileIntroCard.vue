<template>
  <div class="wt-card wt-card--interactive profile-intro">
    <span v-if="introduce" class="profile-intro__text">
      {{ introduce }}
      <el-icon class="profile-intro__icon"><EditPen /></el-icon>
    </span>
    <span
      v-else
      class="profile-intro__text profile-intro__text--placeholder"
      @click="emit('edit')"
    >
      添加个人简介，让大家更好的认识你
      <el-icon class="profile-intro__icon"><EditPen /></el-icon>
    </span>
  </div>
</template>

<script setup>
import { EditPen } from '@element-plus/icons-vue'

// 个人简介卡：有内容时仅展示，无内容时点击整块去填写
// （与原逻辑一致：@click 只挂在空态文案上）
defineProps({
  introduce: { type: String, default: '' }
})
const emit = defineEmits(['edit'])
</script>

<style scoped>
.profile-intro {
  margin: var(--wt-space-4) var(--wt-gutter) 0;
  padding: 14px var(--wt-space-4);
}
.profile-intro__text {
  display: flex;
  align-items: flex-start; /* 多行简介时图标不再垂直居中偏移 */
  gap: var(--wt-space-2);
  color: var(--wt-text-2);
  font-size: var(--wt-fs-md);
  line-height: 1.6;
  word-break: break-word;
}
.profile-intro__text--placeholder {
  cursor: pointer;
}
.profile-intro__icon {
  flex: 0 0 auto;
  margin-top: 3px;
  color: var(--wt-color-brand);
  font-size: var(--wt-fs-md);
}

@media (min-width: 768px) {
  .profile-intro {
    padding: 16px var(--wt-space-6);
  }
}
</style>
