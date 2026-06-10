<template>
  <aside class="sidebar" :class="aiType">
    <div class="sidebar-top">
      <div class="logo-container" @click="goHome">
        <div class="logo-icon">{{ aiType === 'doc' ? '📚' : '🤖' }}</div>
        <div class="logo-text">
          <h2>{{ aiType === 'doc' ? 'AI 智能文档助手' : 'AI 超级智能体' }}</h2>
          <p>{{ aiType === 'doc' ? '技术文档问答专家' : '专业解决所有相关难题' }}</p>
        </div>
      </div>

      <button class="new-chat-btn" @click="startNewChat">
        <span class="plus-icon">+</span> 开启新对话
      </button>
    </div>

    <div class="history-container">
      <div class="history-title">对话记录</div>
      <div class="history-group">
        <div class="group-label">最近</div>
        <div class="history-list">
          <div
            v-for="(chat, index) in historyList"
            :key="index"
            class="history-item"
            :class="{ active: currentChatId === chat.id }"
            @click="selectChat(chat)"
          >
            <span class="chat-icon">💬</span>
            <span class="chat-title">{{ chat.title }}</span>
            <span class="chat-more">...</span>
          </div>
        </div>
      </div>
    </div>

    <div class="sidebar-bottom">
      <button class="clear-btn" @click="clearChat">
        <span class="trash-icon">🗑️</span> 清空当前对话
      </button>
    </div>
  </aside>
</template>

<script setup>
import { useRouter } from 'vue-router'

const props = defineProps({
  currentChatId: String,
  historyList: { type: Array, default: () => [] },
  aiType: { type: String, default: 'default' }
})

const emit = defineEmits(['new-chat', 'select-chat', 'clear-chat'])
const router = useRouter()

const goHome = () => router.push('/')
const startNewChat = () => emit('new-chat')
const selectChat = (chat) => emit('select-chat', chat)
const clearChat = () => emit('clear-chat')
</script>

<style scoped>
.sidebar {
  width: 260px;
  height: 100vh;
  background-color: #ffffff;
  border-right: 1px solid rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  z-index: 100;
  color: #1e293b;
}

.sidebar-top { padding: 24px 16px; }

.logo-container {
  display: flex;
  align-items: center;
  margin-bottom: 32px;
  cursor: pointer;
}

.logo-icon { font-size: 32px; margin-right: 12px; }

.logo-text h2 { font-size: 20px; font-weight: 700; color: #1e293b; }
.logo-text p { font-size: 12px; color: #64748b; margin-top: 2px; }

.new-chat-btn {
  width: 100%;
  height: 48px;
  background: var(--btn-bg, #1890ff);
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
  box-shadow: 0 4px 12px var(--btn-shadow, rgba(24, 144, 255, 0.2));
}

.sidebar.doc {
  --btn-bg: #1890ff;
  --btn-shadow: rgba(24, 144, 255, 0.2);
  --active-bg: #eff6ff;
  --active-text: #1d4ed8;
}

.sidebar.super {
  --btn-bg: #10b981;
  --btn-shadow: rgba(16, 185, 129, 0.2);
  --active-bg: #ecfdf5;
  --active-text: #059669;
}

.new-chat-btn:hover { transform: translateY(-2px); filter: brightness(1.05); }
.plus-icon { font-size: 20px; margin-right: 8px; }

.history-container { flex: 1; overflow-y: auto; padding: 0 12px; }

.history-title {
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin: 24px 12px 12px;
}

.group-label { font-size: 11px; color: #94a3b8; margin: 16px 12px 8px; }

.history-list { display: flex; flex-direction: column; gap: 4px; }

.history-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  color: #475569;
}

.history-item:hover { background-color: #f1f5f9; color: #1e293b; }

.history-item.active {
  background-color: var(--active-bg, #eff6ff);
  color: var(--active-text, #1d4ed8);
  font-weight: 500;
}

.chat-icon { margin-right: 12px; font-size: 16px; opacity: 0.7; }

.chat-title {
  flex: 1;
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chat-more { opacity: 0; transition: opacity 0.2s; padding: 0 4px; }
.history-item:hover .chat-more { opacity: 1; }

.sidebar-bottom { padding: 16px; border-top: 1px solid rgba(0, 0, 0, 0.05); }

.clear-btn {
  width: 100%;
  height: 40px;
  background: transparent;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  color: #64748b;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.clear-btn:hover { background-color: #fef2f2; border-color: #fecaca; color: #ef4444; }
.trash-icon { margin-right: 8px; }
</style>
