import axios from 'axios'

const isProduction = import.meta.env.PROD
const API_BASE_URL = isProduction
  ? '/api'
  : 'http://localhost:8123/api'

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000
})

// 请求拦截器：添加 JWT Token
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器：401 时跳转登录
request.interceptors.response.use(
  response => response,
  error => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

// 封装SSE连接（通过 query param 传递 token）
export const connectSSE = (url, params) => {
  const token = localStorage.getItem('token')
  if (token) params.token = token

  const queryString = Object.keys(params)
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
    .join('&')

  const fullUrl = `${API_BASE_URL}${url}?${queryString}`

  const eventSource = new EventSource(fullUrl)

  eventSource.onmessage = event => {
    let data = event.data
    if (data === '[DONE]') {
      // handled by caller
    }
  }

  eventSource.onerror = error => {
    eventSource.close()
  }

  return eventSource
}

// 鉴权 API
export const register = (data) => request.post('/auth/register', data)
export const login = (data) => request.post('/auth/login', data)

// AI 智能文档助手聊天
export const chatWithDocQA = (message, chatId) => {
  return connectSSE('/ai/doc_qa/chat/sse', { message, chatId })
}

// AI 超级智能体聊天
export const chatWithManus = (message, chatId) => {
  return connectSSE('/ai/manus/chat', { message, chatId })
}

// 获取会话列表
export const getSessions = (type) => {
  return request.get('/ai/sessions', { params: { type } })
}

// 获取会话消息
export const getSessionMessages = (chatId, type) => {
  return request.get(`/ai/sessions/${chatId}/messages`, { params: { type } })
}

// 清空会话
export const deleteSession = (chatId, type) => {
  return request.delete(`/ai/sessions/${chatId}`, { params: { type } })
}

export default {
  login,
  register,
  chatWithDocQA,
  chatWithManus,
  getSessions,
  getSessionMessages,
  deleteSession
}
