# TechDoc AI Assistant（AI 智能文档助手）

基于 **Spring Boot 3 + Java 21 + Spring AI** 构建的 AI 智能文档助手，支持 RAG 文档问答与 ReAct 模式自主规划智能体。

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0-blue)](https://spring.io/projects/spring-ai)

---

## 项目结构

```text
techdoc-ai-assistant/
├── yu-ai-agent-frontend/            # Vue3 前端模块
│   ├── src/                         # 前端源码
│   │   ├── api/                     # Axios API 封装 + SSE 连接
│   │   ├── components/              # 通用组件（ChatRoom, AppSidebar 等）
│   │   ├── views/                   # 页面（DocQA, SuperAgent, Login, Register）
│   │   └── router/                  # Vue Router 路由配置
│   ├── package.json
│   └── vite.config.js
└── yu-ai-agent-backend/             # Java 后端项目
    ├── pom.xml                      # 父 POM，管理多模块
    ├── Dockerfile                   # Docker 构建文件
    ├── yu-ai-agent-admin/           # 核心应用模块
    │   └── src/main/
    │       ├── java/com/yuaiagent/
    │       │   ├── advisor/         # 自定义 Advisor（日志、Re-Reading）
    │       │   ├── agent/           # AI 智能体（BaseAgent → ReActAgent → YuManus）
    │       │   ├── chatmemory/      # Redis 持久化对话记忆
    │       │   ├── config/          # Spring Security / Redis / RAG 配置
    │       │   ├── controller/      # REST API（AI 对话、鉴权、健康检查）
    │       │   ├── dto/             # 请求/响应 DTO
    │       │   ├── entity/          # JPA 实体
    │       │   ├── rag/             # RAG 全链路（文档加载/切割/检索/查询增强）
    │       │   ├── repository/      # Spring Data JPA 仓库
    │       │   ├── security/        # JWT 鉴权（Token 生成/验证/过滤器）
    │       │   ├── service/         # 业务服务（文档问答、超级智能体）
    │       │   └── tools/           # 7 种内置工具
    │       └── resources/
    │           ├── application.yml
    │           ├── application-druid.yml
    │           └── document/        # RAG 知识库（Spring Boot/Java/Spring AI 文档）
    └── yu-image-search-mcp-server/  # MCP 图片搜索服务模块
```

---

## 核心功能

### 1. AI 智能文档问答（DocQAService）

- **多轮对话**：基于 Spring AI `ChatClient` + 自定义 `Advisor` 实现连贯会话
- **对话记忆持久化**：使用 Redis 存储会话状态，支持 7 天自动过期
- **RAG 知识库**：加载技术文档 Markdown 文件，经文档切割 → 向量存储 → 查询增强，实现精准知识问答
- **结构化输出**：支持生成结构化 JSON 输出
- **工具调用 & MCP**：可调用内置工具和外部 MCP 服务

### 2. AI 超级智能体（SuperAgentService）

- **ReAct 架构**：`BaseAgent → ToolCallAgent → ReActAgent → YuManus` 四层继承体系
- **自主规划**：根据用户需求自主推理并调用多种工具，直至完成目标
- **SSE 流式输出**：通过 SSE 实时推送智能体推理过程和结果

### 3. 用户认证

- **JWT 鉴权**：注册/登录接口，Token 24 小时有效
- **Spring Security**：无状态会话管理，API 级别权限控制
- **BCrypt 密码加密**

### 4. 内置工具集

| 工具 | 说明 |
|---|---|
| `WebSearchTool` | 联网搜索 |
| `WebScrapingTool` | 网页抓取（Jsoup） |
| `FileOperationTool` | 文件读写操作 |
| `ResourceDownloadTool` | 资源下载 |
| `TerminalOperationTool` | 终端命令执行 |
| `PDFGenerationTool` | PDF 文档生成（iText） |
| `TerminateTool` | 终止智能体执行 |

---

## 技术栈

| 分类 | 技术 |
|---|---|
| **框架** | Spring Boot 3.5 + Java 21 |
| **AI 框架** | Spring AI 1.0 + Spring AI Alibaba |
| **大模型** | 阿里云百炼 DashScope（qwen-plus）/ Ollama 本地模型 |
| **数据库** | MySQL（用户数据）+ Redis（会话记忆） |
| **向量数据库** | SimpleVectorStore（内存）/ PGVector（可选） |
| **安全** | Spring Security + JWT + BCrypt |
| **数据源** | Druid 连接池 + Spring Data JPA |
| **API 文档** | Knife4j (Swagger) |
| **工具库** | Hutool、Jsoup、iText |
| **协议** | MCP（Model Context Protocol）|
| **前端** | Vue 3 + Vue Router + Axios + SSE |
| **部署** | Docker |

---

## 快速开始

### 1. 环境要求

- **JDK 21+**
- **Maven 3.9+**
- **MySQL 8.0+**
- **Redis 7.0+**

### 2. 数据库初始化

创建 MySQL 数据库：

```sql
CREATE DATABASE techdoc_ai DEFAULT CHARACTER SET utf8mb4;
```

用户表由 JPA 自动创建（`ddl-auto: update`）。

### 3. 配置 API Key

编辑 `yu-ai-agent-admin/src/main/resources/application.yml`：

```yaml
spring:
  ai:
    dashscope:
      api-key: 替换为您的API_KEY

search-api:
  api-key: 替换为您的API_KEY
```

### 4. 配置数据库连接

编辑 `application-druid.yml`，修改 MySQL 用户名和密码。

### 5. 启动 Redis

确保 Redis 服务运行在 `localhost:6379`。

### 6. 构建 & 运行

```bash
# 编译打包
mvn clean package -DskipTests

# 启动后端
java -jar yu-ai-agent-admin/target/yu-ai-agent-admin-0.0.1-SNAPSHOT.jar

# 启动前端
cd yu-ai-agent-frontend
npm install
npm run dev
```

### 7. 接口文档

启动后访问 Knife4j 接口文档：

```
http://localhost:8123/api/doc.html
```

---

## API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/login` | 用户登录 |
| GET | `/api/ai/doc_qa/chat/sse` | 文档问答（SSE流式） |
| GET | `/api/ai/manus/chat` | 超级智能体（SSE流式） |
| GET | `/api/ai/sessions` | 获取会话列表 |
| GET | `/api/ai/sessions/{chatId}/messages` | 获取会话消息 |
| DELETE | `/api/ai/sessions/{chatId}` | 清空会话 |
