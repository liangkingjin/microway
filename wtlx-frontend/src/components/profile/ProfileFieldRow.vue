<template>
  <div class="profile-field-row" @click="emit('click')">
    <span class="profile-field-row__label">{{ label }}</span>
    <span class="profile-field-row__value">
      <img v-if="avatar" class="profile-field-row__avatar" :src="avatar" alt="">
      <span v-else class="profile-field-row__text wt-ellipsis">
        {{ displayValue }}
      </span>
      <el-icon class="profile-field-row__arrow"><ArrowRight /></el-icon>
    </span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'

// 资料编辑页的一行：标签 + 取值（或头像）+ 右箭头
// 取值超长时省略号截断（不再靠模板内联 width:150px 硬撑）
const props = defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], default: '' },
  /** 传了 avatar 则渲染圆形头像而非文字 */
  avatar: { type: String, default: '' },
  /** 取值为空时的占位文案 */
  placeholder: { type: String, default: '' }
})
const emit = defineEmits(['click'])

const displayValue = computed(() =>
  props.value === 0 || props.value ? props.value : props.placeholder
)
</script>

<style scoped>
.profile-field-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--wt-space-3);
  min-height: 60px; /* 头像行与文字行高度一致 */
  padding: var(--wt-space-3) var(--wt-space-4);
  cursor: pointer;
  transition: background-color 0.2s ease;
}
.profile-field-row:hover {
  background-color: var(--wt-bg-muted);
}
.profile-field-row:active {
  background-color: #eff2f7;
}
.profile-field-row__label {
  flex: 0 0 auto;
  font-size: var(--wt-fs-lg);
  font-weight: 500;
  line-height: 1.5;
  color: var(--wt-text-1);
}
.profile-field-row__value {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--wt-space-2);
  flex: 0 1 auto;
  min-width: 0;
  font-size: var(--wt-fs-md);
  line-height: 1.5;
  color: var(--wt-text-2);
}
.profile-field-row__text {
  max-width: min(46vw, 320px);
  text-align: right;
}
.profile-field-row__avatar {
  flex: 0 0 auto;
  display: block;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid var(--wt-border);
  object-fit: cover;
}
.profile-field-row__arrow {
  flex: 0 0 auto;
  color: var(--wt-text-4);
  font-size: var(--wt-fs-md);
}

@media (min-width: 768px) {
  .profile-field-row {
    min-height: 64px;
    padding: var(--wt-space-3) var(--wt-space-6);
  }
  .profile-field-row__text {
    max-width: 380px;
  }
}

@media (max-width: 360px) {
  .profile-field-row {
    padding: var(--wt-space-3);
  }
  .profile-field-row__label {
    font-size: var(--wt-fs-md);
  }
}
</style>
