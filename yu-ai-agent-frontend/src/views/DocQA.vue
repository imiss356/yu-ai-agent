<template>
  <div class="doc-qa-container">
    <div class="header">
      <div class="back-button" @click="goBack">返回</div>
      <h1 class="title">AI智能文档助手</h1>
      <div class="chat-id">会话ID: {{ chatId }}</div>
    </div>

    <div class="content-wrapper">
      <div class="chat-area">
        <ChatRoom
          ref="chatRoomRef"
          :messages="messages"
          :connection-status="connectionStatus"
          ai-type="doc"
          theme-color="#1890ff"
          theme-color-rgb="24, 144, 255"
          @send-message="sendMessage"
        />
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import ChatRoom from '../components/ChatRoom.vue'
import { chatWithDocQA, getSessionMessages } from '../api'

const props = defineProps({
  chatIdProp: String
})

const emit = defineEmits(['update-history', 'update-chat-id'])

const router = useRouter()
const messages = ref([])
const chatId = ref('')
const connectionStatus = ref('disconnected')
const chatRoomRef = ref(null)
let eventSource = null

const generateChatId = () => {
  const newId = 'doc_' + Math.random().toString(36).substring(2, 10)
  emit('update-chat-id', newId)
  return newId
}

const loadMessages = async (id) => {
  if (!id) return
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  connectionStatus.value = 'disconnected'
  if (chatRoomRef.value) {
    chatRoomRef.value.clearInput()
  }
  try {
    const res = await getSessionMessages(id, 'doc')
    if (res.data && res.data.length > 0) {
      messages.value = res.data.map(m => ({
        content: m.content || m.text || '',
        isUser: m.messageType === 'USER' || m.role === 'USER',
        time: new Date().getTime()
      }))
    } else {
      messages.value = []
    }
  } catch (error) {
    console.error('Failed to load messages:', error)
    messages.value = []
  }
}

watch(() => props.chatIdProp, (newVal) => {
  if (newVal && newVal !== chatId.value) {
    chatId.value = newVal
    loadMessages(newVal)
  }
}, { immediate: true })

const addMessage = (content, isUser) => {
  messages.value.push({ content, isUser, time: new Date().getTime() })
}

const sendMessage = async (message) => {
  if (!chatId.value) {
    chatId.value = generateChatId()
  }
  addMessage(message, true)
  if (eventSource) {
    eventSource.close()
  }
  const aiMessageIndex = messages.value.length
  addMessage('', false)
  connectionStatus.value = 'connecting'
  eventSource = chatWithDocQA(message, chatId.value)
  eventSource.onmessage = (event) => {
    const data = event.data
    if (data && data !== '[DONE]') {
      if (aiMessageIndex < messages.value.length) {
        messages.value[aiMessageIndex].content += data
      }
    }
    if (data === '[DONE]') {
      connectionStatus.value = 'disconnected'
      eventSource.close()
      emit('update-history')
    }
  }
  eventSource.onerror = (error) => {
    console.error('SSE Error:', error)
    connectionStatus.value = 'error'
    eventSource.close()
  }
}

const goBack = () => {
  router.push('/')
}

onMounted(() => {
  if (props.chatIdProp) {
    chatId.value = props.chatIdProp
    loadMessages(chatId.value)
  } else {
    chatId.value = generateChatId()
    messages.value = []
  }
})

onBeforeUnmount(() => {
  if (eventSource) {
    eventSource.close()
  }
})
</script>

<style scoped>
.doc-qa-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #f8faff;
  color: #1e293b;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
  color: #1e293b;
  border-bottom: 1px solid #e8edf5;
  position: sticky;
  top: 0;
  z-index: 10;
}

.back-button {
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: all 0.3s;
  color: #64748b;
}

.back-button:hover {
  color: #1890ff;
  transform: translateX(-4px);
}

.back-button:before {
  content: '\2190';
  margin-right: 8px;
}

.title {
  font-size: 20px;
  font-weight: 700;
  margin: 0;
  color: #1890ff;
  letter-spacing: -0.5px;
}

.chat-id {
  font-size: 13px;
  color: #94a3b8;
  font-family: monospace;
}

.content-wrapper {
  display: flex;
  flex-direction: column;
  flex: 1;
  overflow: hidden;
}

.chat-area {
  flex: 1;
  overflow: hidden;
  position: relative;
}

@media (max-width: 768px) {
  .header { padding: 12px 16px; }
  .title { font-size: 18px; }
  .chat-id { display: none; }
}
</style>
