<template>
  <div class="home-container">
    <div class="header">
      <div class="glitch-wrapper">
        <h1 class="glitch-title">AI 智能文档助手</h1>
      </div>
      <p class="subtitle">/ 让技术文档触手可及 /</p>
      <div class="cyber-line"></div>
    </div>

    <div class="apps-container">
      <div class="app-card" @click="navigateTo('/doc-qa')">
        <div class="card-glow"></div>
        <div class="app-icon doc-icon">📚</div>
        <div class="app-info">
          <div class="app-title">AI智能文档问答</div>
          <div class="app-desc">基于RAG技术，精准回答技术文档问题</div>
        </div>
        <div class="app-button">
          <span class="btn-text">立即体验</span>
          <span class="btn-icon">→</span>
        </div>
      </div>

      <div class="app-card" @click="navigateTo('/super-agent')">
        <div class="card-glow"></div>
        <div class="app-icon robot-icon">🤖</div>
        <div class="app-info">
          <div class="app-title">AI超级智能体</div>
          <div class="app-desc">全能型AI助手，解决各类专业问题</div>
        </div>
        <div class="app-button">
          <span class="btn-text">立即体验</span>
          <span class="btn-icon">→</span>
        </div>
      </div>
    </div>

    <div class="user-actions">
      <button class="logout-btn" @click="logout">退出登录</button>
    </div>

    <div class="cyber-circles">
      <div class="circle circle-1"></div>
      <div class="circle circle-2"></div>
      <div class="circle circle-3"></div>
    </div>
    <AppFooter />
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import AppFooter from '../components/AppFooter.vue'

const router = useRouter()

const navigateTo = (path) => {
  router.push(path)
}

const logout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  router.push('/login')
}
</script>

<style scoped>
.home-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: #f8fafc;
  background-image:
    radial-gradient(circle at 100% 0%, rgba(24, 144, 255, 0.05) 0%, transparent 40%),
    radial-gradient(circle at 0% 100%, rgba(34, 197, 94, 0.05) 0%, transparent 40%);
  position: relative;
  overflow: hidden;
  padding: 80px 20px;
}

.header {
  text-align: center;
  margin-bottom: 80px;
  position: relative;
  z-index: 10;
}

.glitch-title {
  font-size: 48px;
  font-weight: 800;
  color: #1a1a1a;
  margin-bottom: 16px;
  letter-spacing: -1px;
}

.subtitle {
  font-size: 20px;
  color: #64748b;
  font-weight: 400;
}

.cyber-line {
  width: 60px;
  height: 4px;
  background: linear-gradient(90deg, #1890ff, #10b981);
  margin: 24px auto 0;
  border-radius: 2px;
}

.apps-container {
  display: flex;
  justify-content: center;
  gap: 32px;
  max-width: 1200px;
  margin: 0 auto;
  position: relative;
  z-index: 10;
}

.app-card {
  background: #ffffff;
  border-radius: 24px;
  padding: 48px 32px;
  width: 380px;
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.02);
}

.app-card:hover {
  transform: translateY(-10px);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.08);
}

.doc-icon {
  background: #eff6ff;
  color: #1890ff;
}

.robot-icon {
  background: #f0f7ff;
  color: #1890ff;
}

.app-icon {
  width: 80px;
  height: 80px;
  border-radius: 20px;
  font-size: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24px;
}

.app-info {
  margin-bottom: 32px;
}

.app-title {
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 12px;
}

.app-desc {
  font-size: 16px;
  color: #64748b;
  line-height: 1.6;
}

.app-button {
  width: 100%;
  height: 54px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  font-weight: 600;
  transition: all 0.3s;
}

.app-card:nth-child(1) .app-button {
  background: #1890ff;
  color: white;
}

.app-card:nth-child(2) .app-button {
  background: #10b981;
  color: white;
}

.app-button:hover {
  filter: brightness(1.1);
  gap: 16px;
}

.btn-icon {
  font-size: 20px;
}

.user-actions {
  display: flex;
  justify-content: center;
  margin-top: 48px;
  z-index: 10;
}

.logout-btn {
  background: transparent;
  border: 1px solid #e2e8f0;
  color: #64748b;
  padding: 10px 32px;
  border-radius: 10px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.logout-btn:hover {
  border-color: #ef4444;
  color: #ef4444;
}

.circle {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  z-index: 1;
  opacity: 0.1;
}

.circle-1 {
  width: 400px; height: 400px;
  background: #1890ff;
  top: -100px; right: -100px;
}

.circle-2 {
  width: 300px; height: 300px;
  background: #10b981;
  bottom: -50px; left: -50px;
}

@media (max-width: 768px) {
  .apps-container { flex-direction: column; align-items: center; }
  .app-card { width: 100%; max-width: 380px; }
  .glitch-title { font-size: 36px; }
}
</style>
