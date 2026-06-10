<template>
  <div class="ai-avatar-fallback" :class="type">
    <span v-if="type === 'doc'">📚</span>
    <span v-else>🤖</span>
  </div>
</template>

<script setup>
defineProps({
  type: { type: String, default: 'default' }
})
</script>

<style scoped>
.ai-avatar-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  border-radius: 50%;
}

.doc {
  background: linear-gradient(45deg, #1890ff, #5677fc);
}

.default, .super {
  background: linear-gradient(45deg, #10b981, #34d399);
}
</style>
