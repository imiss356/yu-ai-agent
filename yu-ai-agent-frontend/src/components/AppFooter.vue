<template>
  <footer class="app-footer">
    <div class="footer-content">
      <div class="footer-section">
        <div class="footer-logo">
          <h3>AI 智能文档助手</h3>
        </div>
        <div class="footer-links">
          <router-link to="/doc-qa">AI 智能文档问答</router-link>
          <router-link to="/super-agent">AI 超级智能体</router-link>
        </div>
      </div>
    </div>
    <div class="footer-bottom">
      <p>&copy; {{ currentYear }} AI 智能文档助手 - 让技术文档触手可及</p>
    </div>
  </footer>
</template>

<script setup>
import { computed } from 'vue'
const currentYear = computed(() => new Date().getFullYear())
</script>

<style scoped>
.app-footer {
  padding: 24px 0 0;
  z-index: 10;
  margin-top: auto;
}

.footer-content { padding: 0 20px; }
.footer-section { text-align: center; }

.footer-logo h3 {
  font-size: 16px;
  color: #1e293b;
  margin-bottom: 12px;
}

.footer-links {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-bottom: 16px;
}

.footer-links a {
  font-size: 14px;
  color: #64748b;
  text-decoration: none;
  transition: color 0.2s;
}

.footer-links a:hover { color: #1890ff; }

.footer-bottom {
  padding: 16px 20px;
  border-top: 1px solid #e2e8f0;
  margin-top: 16px;
}

.footer-bottom p {
  font-size: 13px;
  color: #94a3b8;
  text-align: center;
}
</style>
