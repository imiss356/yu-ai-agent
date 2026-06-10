<template>
  <div class="app-layout">
    <AppSidebar
      v-if="showSidebar"
      :aiType="aiType"
      :currentChatId="currentChatId"
      :historyList="historyList"
      @new-chat="handleNewChat"
      @select-chat="handleSelectChat"
      @clear-chat="handleClearChat"
    />
    <main class="main-content">
      <router-view
        :chatIdProp="currentChatId"
        @update-history="loadHistory"
        @update-chat-id="handleUpdateChatId"
      />
    </main>
  </div>
</template>

<script setup>
import { computed, ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppSidebar from './components/AppSidebar.vue'
import { getSessions, deleteSession } from './api'

const route = useRoute()
const router = useRouter()

const showSidebar = computed(() => {
  return ['/doc-qa', '/super-agent'].includes(route.path)
})

const aiType = computed(() => {
  if (route.path === '/doc-qa') return 'doc'
  if (route.path === '/super-agent') return 'super'
  return 'default'
})

const currentChatId = ref('')
const historyList = ref([])

const loadHistory = async () => {
  if (!showSidebar.value) return
  try {
    const res = await getSessions(aiType.value)
    if (res.data) {
      historyList.value = res.data
    }
  } catch (error) {
    console.error('Failed to load history:', error)
  }
}

watch(() => aiType.value, (newType) => {
  if (newType !== 'default') {
    loadHistory()
    currentChatId.value = ''
  }
})

onMounted(() => {
  loadHistory()
})

const handleUpdateChatId = (id) => {
  currentChatId.value = id
}

const handleNewChat = () => {
  const prefix = aiType.value === 'doc' ? 'doc_' : 'super_'
  const newId = prefix + Math.random().toString(36).substring(2, 10)
  currentChatId.value = newId
}

const handleSelectChat = (chat) => {
  currentChatId.value = chat.id
}

const handleClearChat = async () => {
  if (!currentChatId.value) return
  try {
    await deleteSession(currentChatId.value, aiType.value)
    currentChatId.value = ''
    loadHistory()
  } catch (error) {
    console.error('Failed to clear chat:', error)
  }
}
</script>

<style>
.app-layout {
  display: flex;
  width: 100%;
  height: 100vh;
  background-color: #f8fafc;
  color: #1e293b;
  overflow: hidden;
}

.main-content {
  flex: 1;
  width: 0;
  height: 100%;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

html, body {
  font-family: 'PingFang SC', 'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial, sans-serif;
  font-size: 16px;
  color: #1e293b;
  background-color: #f8fafc;
  width: 100%;
  height: 100%;
  overflow-x: hidden;
}

a {
  text-decoration: none;
  color: inherit;
}

button {
  cursor: pointer;
}

@media (max-width: 768px) {
  html, body {
    font-size: 15px;
  }
}

@media (max-width: 480px) {
  html, body {
    font-size: 14px;
  }
}

::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb {
  background: #ccc;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: #aaa;
}
</style>
