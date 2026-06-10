<template>
  <div class="super-agent-container">
    <div class="header">
      <div class="back-button" @click="goBack">返回</div>
      <h1 class="title">AI超级智能体</h1>
      <div class="placeholder"></div>
    </div>
    
    <div class="content-wrapper">
      <div class="chat-area">
        <ChatRoom 
          ref="chatRoomRef"
          :messages="messages" 
          :connection-status="connectionStatus"
          ai-type="super"
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
import { chatWithManus, getSessionMessages } from '../api'

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

// 生成随机会话ID
const generateChatId = () => {
  const newId = 'super_' + Math.random().toString(36).substring(2, 10)
  emit('update-chat-id', newId)
  return newId
}

// 加载历史消息
const loadMessages = async (id) => {
  if (!id) return
  
  // 清理现有连接
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  connectionStatus.value = 'disconnected'
  
  // 清空输入框
  if (chatRoomRef.value) {
    chatRoomRef.value.clearInput()
  }
  
  try {
    const res = await getSessionMessages(id, 'super')
    if (res.data && res.data.length > 0) {
      messages.value = res.data.map(m => ({
        content: m.content || m.text || '', // 兼容不同版本的字段名
        isUser: m.messageType === 'USER' || m.role === 'USER',
        time: new Date().getTime()
      }))
    } else {
      // 如果没有历史消息，显示欢迎界面（messages 保持空）
      messages.value = []
    }
  } catch (error) {
    console.error('Failed to load messages:', error)
    // 出错也尝试清空并显示欢迎界面
    messages.value = []
  }
}

// 监听 ID 变化
watch(() => props.chatIdProp, (newVal) => {
  if (newVal && newVal !== chatId.value) {
    chatId.value = newVal
    loadMessages(newVal)
  }
}, { immediate: true })

// 添加消息到列表
const addMessage = (content, isUser, type = '') => {
  messages.value.push({
    content,
    isUser,
    type,
    time: new Date().getTime()
  })
}

// 发送消息
const sendMessage = (message) => {
  if (!chatId.value) {
    chatId.value = 'super_' + Math.random().toString(36).substring(2, 10)
  }

  addMessage(message, true, 'user-question')
  
  // 连接SSE
  if (eventSource) {
    eventSource.close()
  }
  
  // 设置连接状态
  connectionStatus.value = 'connecting'
  
  // 临时存储
  let messageBuffer = []; // 用于存储SSE消息的缓冲区
  let lastBubbleTime = Date.now(); // 上一个气泡的创建时间
  let isFirstResponse = true; // 是否是第一次响应
  
  const chineseEndPunctuation = ['。', '！', '？', '…']; // 中文句子结束标点
  const minBubbleInterval = 800; // 气泡最小间隔时间(毫秒)
  
  // 创建消息气泡的函数
  const createBubble = (content, type = 'ai-answer') => {
    if (!content.trim()) return;
    
    // 添加适当的延迟，使消息显示更自然
    const now = Date.now();
    const timeSinceLastBubble = now - lastBubbleTime;
    
    if (isFirstResponse) {
      // 第一条消息立即显示
      addMessage(content, false, type);
      isFirstResponse = false;
    } else if (timeSinceLastBubble < minBubbleInterval) {
      // 如果与上一气泡间隔太短，添加一个延迟
      setTimeout(() => {
        addMessage(content, false, type);
      }, minBubbleInterval - timeSinceLastBubble);
    } else {
      // 正常添加消息
      addMessage(content, false, type);
    }
    
    lastBubbleTime = now;
    messageBuffer = []; // 清空缓冲区
  };
  
  eventSource = chatWithManus(message, chatId.value)
  
  // 监听SSE消息
  eventSource.onmessage = (event) => {
    const data = event.data
    
    if (data && data !== '[DONE]') {
      messageBuffer.push(data);
      
      // 检查是否应该创建新气泡
      const combinedText = messageBuffer.join('');
      
      // 句子结束或消息长度达到阈值
      const lastChar = data.charAt(data.length - 1);
      const hasCompleteSentence = chineseEndPunctuation.includes(lastChar) || data.includes('\n\n');
      const isLongEnough = combinedText.length > 40;
      
      if (hasCompleteSentence || isLongEnough) {
        createBubble(combinedText);
      }
    }
    
    if (data === '[DONE]') {
      // 如果还有未显示的内容，创建最后一个气泡
      if (messageBuffer.length > 0) {
        const remainingContent = messageBuffer.join('');
        createBubble(remainingContent, 'ai-final');
      }
      
      // 完成后关闭连接
      connectionStatus.value = 'disconnected'
      eventSource.close()
      // 发送完第一条消息后通知父组件更新历史记录
      emit('update-history')
    }
  }
  
  // 监听SSE错误
  eventSource.onerror = (error) => {
    console.error('SSE Error:', error)
    connectionStatus.value = 'error'
    eventSource.close()
    
    // 如果出错时有未显示的内容，也创建气泡
    if (messageBuffer.length > 0) {
      const remainingContent = messageBuffer.join('');
      createBubble(remainingContent, 'ai-error');
    }
  }
}

// 返回主页
const goBack = () => {
  router.push('/')
}

// 页面加载时处理
onMounted(() => {
  if (props.chatIdProp) {
    chatId.value = props.chatIdProp
    loadMessages(chatId.value)
  } else {
    // 欢迎界面显示（messages 为空即可）
    messages.value = []
  }
})

// 组件销毁前关闭SSE连接
onBeforeUnmount(() => {
  if (eventSource) {
    eventSource.close()
  }
})
</script>

<style scoped>
.super-agent-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #f8fafc;
  color: #1e293b;
}

.header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 16px 24px;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
  color: #1e293b;
  border-bottom: 1px solid #e2e8f0;
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
  content: '←';
  margin-right: 8px;
}

.title {
  font-size: 20px;
  font-weight: 700;
  margin: 0;
  color: #1890ff;
  letter-spacing: -0.5px;
}

.placeholder {
  width: 100%;
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

/* 响应式样式 */
@media (max-width: 768px) {
  .header {
    padding: 12px 16px;
  }
  
  .title {
    font-size: 18px;
  }
}
</style> 