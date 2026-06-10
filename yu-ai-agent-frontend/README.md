# TechDoc AI Assistant - 前端

基于 Vue 3 构建的 AI 智能文档助手前端应用。

## 功能

- **AI 智能文档问答**：基于 RAG 技术的文档问答系统
- **AI 超级智能体**：ReAct 模式自主规划智能体
- **用户认证**：登录/注册，JWT Token 鉴权
- **对话管理**：会话历史记录、新建/切换/清空对话
- **SSE 流式输出**：实时推送 AI 生成内容

## 技术栈

- Vue 3 (Composition API)
- Vue Router
- Axios
- Server-Sent Events (SSE)

## 开发

```bash
npm install
npm run dev
```

开发服务器运行在 `http://localhost:3000`。

## 构建

```bash
npm run build
```

构建产物输出到 `dist/` 目录。

## 后端 API

后端默认地址：`http://localhost:8123/api`
