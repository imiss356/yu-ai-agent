<template>
  <div class="auth-container">
    <div class="auth-card">
      <div class="auth-header">
        <h1>AI 智能文档助手</h1>
        <p>登录您的账号</p>
      </div>
      <form @submit.prevent="handleLogin" class="auth-form">
        <div class="form-group">
          <label>用户名</label>
          <input v-model="username" type="text" placeholder="请输入用户名" required />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input v-model="password" type="password" placeholder="请输入密码" required />
        </div>
        <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>
        <button type="submit" class="submit-btn" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>
      <div class="auth-footer">
        还没有账号？<router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../api'

const router = useRouter()
const username = ref('')
const password = ref('')
const errorMsg = ref('')
const loading = ref(false)

const handleLogin = async () => {
  if (!username.value || !password.value) {
    errorMsg.value = '请填写用户名和密码'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await login({ username: username.value, password: password.value })
    localStorage.setItem('token', res.data.token)
    localStorage.setItem('username', res.data.username)
    router.push('/')
  } catch (error) {
    errorMsg.value = error.response?.data || '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-color: #f8fafc;
}

.auth-card {
  background: #ffffff;
  border-radius: 20px;
  padding: 48px 40px;
  width: 400px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.06);
}

.auth-header {
  text-align: center;
  margin-bottom: 36px;
}

.auth-header h1 {
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 8px;
}

.auth-header p {
  font-size: 15px;
  color: #64748b;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 8px;
}

.form-group input {
  width: 100%;
  height: 48px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 0 16px;
  font-size: 15px;
  color: #1e293b;
  outline: none;
  transition: border-color 0.2s;
}

.form-group input:focus {
  border-color: #1890ff;
}

.error-msg {
  color: #ef4444;
  font-size: 14px;
  margin-bottom: 16px;
}

.submit-btn {
  width: 100%;
  height: 48px;
  background: #1890ff;
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  margin-top: 8px;
}

.submit-btn:hover:not(:disabled) {
  filter: brightness(1.1);
}

.submit-btn:disabled {
  background: #94a3b8;
  cursor: not-allowed;
}

.auth-footer {
  text-align: center;
  margin-top: 24px;
  font-size: 14px;
  color: #64748b;
}

.auth-footer a {
  color: #1890ff;
  text-decoration: none;
  font-weight: 600;
}
</style>
