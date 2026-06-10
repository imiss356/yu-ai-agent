<template>
  <div
    class="chat-container"
    :style="{
      '--theme-color': themeColor,
      '--theme-color-rgb': themeColorRgb
    }"
  >
    <div class="chat-messages" ref="messagesContainer">
      <div v-if="messages.length === 0" class="chat-welcome">
        <div class="welcome-header">
          <div class="welcome-avatar">
            <template v-if="aiType === 'doc'">📚</template>
            <template v-else>🤖</template>
          </div>
          <h1 class="welcome-title">{{ aiType === 'doc' ? 'AI 智能文档助手' : 'AI 超级智能体' }}</h1>
          <p class="welcome-subtitle">基于RAG技术，精准回答技术文档问题</p>
        </div>

        <div class="quick-query-section">
          <div class="query-header">
            <span class="light-icon">💡</span> 常见问题快捷查询：
          </div>
          <div class="quick-questions">
            <button
              v-for="(q, i) in quickQuestions"
              :key="i"
              @click="sendQuickMessage(q)"
              class="quick-q-btn"
            >
              {{ q }}
            </button>
          </div>
        </div>
      </div>

      <div v-for="(msg, index) in messages" :key="index" class="message-wrapper">
        <div class="message" :class="msg.isUser ? 'user-message' : 'ai-message'">
          <div v-if="!msg.isUser" class="avatar ai-avatar">
            <AiAvatarFallback :type="aiType" />
          </div>
          <div class="message-bubble">
            <div class="message-content">{{ msg.content }}</div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
          <div v-if="msg.isUser" class="avatar user-avatar">
            <div class="avatar-placeholder">我</div>
          </div>
        </div>
      </div>

      <div v-if="connectionStatus === 'connecting'" class="message-wrapper">
        <div class="message ai-message">
          <div class="avatar ai-avatar">
            <AiAvatarFallback :type="aiType" />
          </div>
          <div class="message-bubble loading-bubble">
            <div class="loading-dots">
              <span></span><span></span><span></span>
            </div>
            <span class="loading-text">思考中...</span>
          </div>
        </div>
      </div>
    </div>

    <div class="chat-input-container">
      <div class="input-wrapper">
        <textarea
          v-model="inputMessage"
          @keydown.enter.prevent="sendMessage"
          placeholder="向我提问..."
          class="input-box"
          :disabled="connectionStatus === 'connecting'"
          rows="1"
        ></textarea>
        <button
          @click="sendMessage"
          class="send-btn"
          :disabled="connectionStatus === 'connecting' || !inputMessage.trim()"
        >
          <span class="send-icon">↑</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch, computed } from 'vue'
import AiAvatarFallback from './AiAvatarFallback.vue'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  connectionStatus: { type: String, default: 'disconnected' },
  aiType: { type: String, default: 'default' },
  themeColor: { type: String, default: '#1890ff' },
  themeColorRgb: { type: String, default: '24, 144, 255' }
})

const emit = defineEmits(['send-message'])

const inputMessage = ref('')
const messagesContainer = ref(null)

const clearInput = () => { inputMessage.value = '' }
defineExpose({ clearInput })

const quickQuestions = computed(() => {
  if (props.aiType === 'doc') {
    return [
      'Spring Boot如何配置多数据源？',
      'Java中Stream和for循环哪个性能更好？',
      '如何使用Spring AI实现RAG问答？'
    ]
  } else {
    return [
      '如何快速学习一门新语言？',
      '帮我制定一个健身计划',
      '如何排查线上Java应用CPU飙升？'
    ]
  }
})

const sendQuickMessage = (text) => emit('send-message', text)

const sendMessage = () => {
  if (!inputMessage.value.trim()) return
  emit('send-message', inputMessage.value)
  inputMessage.value = ''
}

const formatTime = (timestamp) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

watch(() => props.messages.length, scrollToBottom)
watch(() => props.messages.map(m => m.content).join(''), scrollToBottom)
onMounted(scrollToBottom)
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background-color: transparent;
  position: relative;
  color: #1e293b;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 40px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.chat-welcome { width: 100%; max-width: 800px; text-align: center; margin-top: 40px; }
.welcome-header { margin-bottom: 48px; }
.welcome-avatar { font-size: 64px; margin-bottom: 24px; }
.welcome-title { font-size: 36px; font-weight: 700; color: #1e293b; margin-bottom: 12px; }
.welcome-subtitle { font-size: 18px; color: #64748b; }

.quick-query-section { width: 100%; margin-top: 20px; }

.query-header {
  background: #eff6ff;
  border-radius: 12px 12px 0 0;
  padding: 12px 24px;
  font-size: 16px;
  font-weight: 600;
  color: #1d4ed8;
  display: flex;
  align-items: center;
  border: 1px solid #dbeafe;
  border-bottom: none;
}

.light-icon { margin-right: 10px; }

.quick-questions {
  display: flex;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 0 0 12px 12px;
  padding: 20px;
  gap: 16px;
  justify-content: space-between;
}

.quick-q-btn {
  flex: 1;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 14px 10px;
  font-size: 14px;
  color: #475569;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.quick-q-btn:hover {
  border-color: var(--theme-color, #1890ff);
  color: var(--theme-color, #1890ff);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.message-wrapper { width: 100%; max-width: 850px; margin-bottom: 32px; }
.message { display: flex; gap: 16px; }
.user-message { flex-direction: row-reverse; }

.avatar {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 14px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ai-avatar { background: var(--theme-color, #1890ff); }
.user-avatar { background: #f1f5f9; border: 1px solid #e2e8f0; }
.avatar-placeholder { color: #64748b; font-weight: 600; font-size: 14px; }

.message-bubble {
  max-width: 80%;
  padding: 14px 20px;
  border-radius: 18px;
  line-height: 1.6;
  font-size: 15px;
}

.ai-message .message-bubble {
  background: #ffffff;
  border: 1px solid #f1f5f9;
  color: #1e293b;
  border-top-left-radius: 4px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.02);
}

.user-message .message-bubble {
  background: var(--theme-color, #1890ff);
  color: #fff;
  border-top-right-radius: 4px;
  box-shadow: 0 4px 15px rgba(var(--theme-color-rgb, 24, 144, 255), 0.2);
}

.message-time { font-size: 11px; color: #94a3b8; margin-top: 6px; display: block; }
.user-message .message-time { color: rgba(255, 255, 255, 0.7); text-align: right; }

.message-content { word-break: break-word; white-space: pre-wrap; min-height: 1.2em; }

.loading-bubble { display: flex; align-items: center; gap: 12px; }
.loading-dots { display: flex; gap: 4px; }

.loading-dots span {
  width: 6px;
  height: 6px;
  background-color: var(--theme-color, #1890ff);
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out both;
}

.loading-dots span:nth-child(1) { animation-delay: -0.32s; }
.loading-dots span:nth-child(2) { animation-delay: -0.16s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1.0); }
}

.loading-text { font-size: 14px; color: var(--theme-color, #1890ff); }

.chat-input-container {
  padding: 24px 20px 40px;
  background: linear-gradient(to top, #f8fafc 80%, transparent);
  display: flex;
  justify-content: center;
  position: sticky;
  bottom: 0;
  z-index: 10;
}

.input-wrapper {
  width: 100%;
  max-width: 850px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  padding: 12px 16px;
  display: flex;
  align-items: flex-end;
  gap: 12px;
  transition: all 0.3s ease;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.04);
}

.input-wrapper:focus-within {
  border-color: var(--theme-color, #1890ff);
  box-shadow: 0 10px 30px rgba(var(--theme-color-rgb, 24, 144, 255), 0.1);
}

.input-box {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: #1e293b;
  font-size: 15px;
  padding: 8px 0;
  resize: none;
  min-height: 24px;
  max-height: 200px;
  font-family: inherit;
}

.input-box::placeholder { color: #94a3b8; }

.send-btn {
  width: 40px;
  height: 40px;
  background: var(--theme-color, #1890ff);
  border: none;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) { filter: brightness(1.1); transform: scale(1.05); }
.send-btn:active:not(:disabled) { transform: scale(0.95); }
.send-btn:disabled { background: #e2e8f0; cursor: not-allowed; }
.send-icon { font-size: 20px; color: #ffffff; font-weight: bold; }

@media (max-width: 768px) {
  .message-bubble { max-width: 90%; }
  .chat-welcome { padding: 0 10px; }
}
</style>
