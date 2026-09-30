<template>
  <div v-if="steps.length" class="thinking-panel">
    <div class="thinking-head" @click="expanded = !expanded">
      <span class="thinking-dot" :class="{ active: streaming }"></span>
      <span class="thinking-title">{{ streaming ? '正在思考…' : '已思考 ' + steps.length + ' 步' }}</span>
      <span class="thinking-toggle">{{ expanded ? '收起' : '展开' }}</span>
    </div>
    <ul v-show="expanded" class="thinking-list">
      <li v-for="step in steps" :key="step.seq" class="thinking-item">
        <span class="thinking-badge" :class="'badge-' + step.type">{{ labelOf(step.type) }}</span>
        <span class="thinking-text">
          {{ step.label }}<template v-if="step.text">：{{ step.text }}</template>
        </span>
      </li>
    </ul>
  </div>
</template>

<script setup>
import { ref } from 'vue'

// steps: [{ seq, type, label, text }]，type 取值 tool / retrieval / reasoning / node
const props = defineProps({
  steps: { type: Array, default: () => [] },
  streaming: { type: Boolean, default: false }
})

const expanded = ref(false)

function labelOf(type) {
  switch (type) {
    case 'tool': return '工具'
    case 'retrieval': return '检索'
    case 'reasoning': return '推理'
    case 'node': return '步骤'
    default: return '过程'
  }
}
</script>

<style scoped>
.thinking-panel {
  margin-bottom: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f7fafc;
  overflow: hidden;
}
.thinking-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  user-select: none;
}
.thinking-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #a0aec0;
  flex: none;
}
.thinking-dot.active {
  background: #4299e1;
}
.thinking-title {
  flex: 1;
  font-size: 13px;
  color: #4a5568;
}
.thinking-toggle {
  font-size: 12px;
  color: #a0aec0;
}
.thinking-list {
  list-style: none;
  margin: 0;
  padding: 0 12px 10px;
  border-top: 1px solid #e2e8f0;
}
.thinking-item {
  display: flex;
  gap: 8px;
  padding: 6px 0;
  font-size: 13px;
  line-height: 1.6;
  color: #4a5568;
  border-bottom: 1px dashed #edf2f7;
}
.thinking-item:last-child {
  border-bottom: none;
}
.thinking-badge {
  flex: none;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  background: #edf2f7;
  color: #718096;
}
.badge-tool {
  background: #ebf8ff;
  color: #2b6cb0;
}
.badge-retrieval {
  background: #f0fff4;
  color: #276749;
}
.badge-reasoning {
  background: #faf5ff;
  color: #553c9a;
}
.thinking-text {
  word-break: break-word;
}
</style>
