# ai-widget-app

Android 客户端：**Agent 对话页** + **桌面 Widget 多任务速报**。手机侧不跑大模型，通过 HTTP 调用 `widget-agent-server`。

> 完整平台设计见上级文档：[桌面任务平台-技术方案.md](../桌面任务平台-技术方案.md)（v11.0）  
> 后端仓库：`../widget-agent-server`

## 1. 定位

1. 展示（对话页 / 桌面 Widget）
2. 调用后端 HTTP API（`user_id` + `message` / `session_id`）
3. 本地缓存与降级（Widget 阶段，见技术方案 §十）

---

## 2. 分层结构

命名约定：**`app/`** = 打开 App 后看到的 Compose 界面；**`homewidget/`** = 桌面小组件（RemoteViews + WorkManager）。二者 UI 实现不同，故不用笼统的 `ui/` 包名。

```
┌─────────────────────────────────────────┐
│  MainActivity          应用入口          │
└──────────────────┬──────────────────────┘
                   ▼
┌─────────────────────────────────────────┐
│  app/AppShellScreen    对话 / 设置路由   │
│  app/AppShellViewModel 状态与业务编排    │
└──────────────────┬──────────────────────┘
                   ▼
┌─────────────────────────────────────────┐
│  network/AgentRepository  发请求、解析 SSE │
└──────────────────┬──────────────────────┘
                   ▼
         widget-agent-server (FastAPI)

并行链路（桌面 Widget，不经对话页）：

HomeWidgetProvider / WorkManager
        ↓
HomeWidgetRefreshWorker → AgentRepository.widgetRun
        ↓
WidgetCache + HomeWidgetCoordinator.renderAllWidgets
```

| 包 / 目录 | 职责 |
|-----------|------|
| `app/` | **App 内界面**：对话页、设置页、导航外壳 ViewModel |
| `homewidget/` | **桌面小组件**：AppWidget 生命周期、定时器、RemoteViews 渲染 |
| `data/` | JSON 模型、SharedPreferences；`Widget*` 前缀 = 桌面任务相关数据 |
| `network/` | Retrofit 接口、OkHttp、SSE 流解析 |
| `util/` | 统一日志 |

---

## 3. 目录与文件

```
app/src/main/java/com/example/aiwidget/
├── MainActivity.kt
├── data/
│   ├── Models.kt              # ChatTurnRequest、ChatResponse、WidgetRunRequest、WidgetResult
│   ├── ChatLocalStore.kt      # 聊天 session_id / 会话列表 / 消息本地缓存（SP）
│   ├── AppPrefs.kt            # API 地址/Key、统一 user_id
│   ├── SessionPrefs.kt        # SSE 开关（API/userId 代理 AppPrefs）
│   ├── Presets.kt             # API 默认 + 全部 Agent 自然语言（芯片/Widget 共用）
│   ├── WidgetConfig.kt        # Widget 默认任务 / 常量
│   ├── WidgetTask.kt          # 定时任务模型（含 enabled）
│   ├── WidgetTaskStore.kt     # 任务列表 JSON 持久化
│   ├── WidgetCache.kt         # 按 cache_slot 缓存展示内容
│   ├── WidgetRunLogEntry.kt   # 定时任务执行记录条目
│   └── WidgetRunLogStore.kt   # 定时执行日志（最多 30 条）
├── network/
│   ├── ApiService.kt
│   ├── RetrofitClient.kt
│   └── AgentRepository.kt
├── app/                       # 打开 App 后看到的界面（Compose）
│   ├── AppShellViewModel.kt   # AppShellUiState、对话发送、Widget 任务编辑
│   ├── AppShellScreen.kt      # 导航外壳 + 会话列表 / 对话页
│   ├── ChatSessionListScreen.kt # 消息 Tab 一级：会话列表
│   ├── SettingsScreen.kt      # API 环境、Widget 任务、定时执行记录
│   ├── ChatMessages.kt        # 聊天气泡、trace 面板、消息模型
│   ├── ChatMarkdownText.kt
│   └── theme/
└── homewidget/                # 桌面小组件（RemoteViews，非 Compose）
    ├── HomeWidgetProvider.kt
    ├── HomeWidgetRefreshWorker.kt
    ├── HomeWidgetCoordinator.kt   # 定时登记 + RemoteViews 渲染
    ├── HomeWidgetDisplayFormatter.kt
    └── HomeWidgetBootReceiver.kt
```

---

## 4. 两条数据流

### 对话页发送

```
用户点「发送」/ 快捷芯片
  → AppShellViewModel.sendChatMessage()
  → ChatTurnRequest(userId, session_id?, message)
  → AgentRepository.chatTurn / chatStream
  → 持久化 session_id + ChatLocalStore；UI 更新 chatMessages
```

进入消息 Tab / 会话列表时 `GET /chat/sessions` merge 列表；点击进入某会话后再 `GET .../messages` 拉该会话历史。返回列表清空 `activeChatSessionId`。

### Widget 刷新

```
定时 / 手动 ↻
  → HomeWidgetRefreshWorker
  → 检查 WidgetCache TTL（force_refresh 可跳过）
  → AgentRepository.widgetRun（无 session_id）
  → WidgetCache.saveSuccess → HomeWidgetCoordinator.renderAllWidgets
  → periodic 时 WidgetRunLogStore.append（设置页可查看）
```

**API 分轨**：

| 场景 | 接口 |
|------|------|
| 聊天 | `POST /api/v1/chat`（+ `session_id` 续聊） |
| 聊天流式 | `POST /api/v1/chat/stream` |
| Widget | `POST /api/v1/widget/run` |

- 聊天响应 `ChatResponse` 含 `session_id`；Widget 响应 `WidgetResult` 无会话
- 对话页与 Widget **共用** `AppPrefs` 的 baseUrl / apiKey / **user_id**

### Prompt 两条链路

| 场景 | 存什么 | 发给 Agent |
|------|--------|------------|
| 对话页 / 快捷芯片 | 不持久化 | [Presets] 用户自然语言，原样发送 |
| Widget 定时任务 | `WidgetTask.prompt` = [Presets] 同款用户语 | 仅 `user_id` + `message`（用户意图）；**WidgetResult 格式由后端拼接** |

聊天不传 history 数组。取数、终局格式与推理由后端 Agent 完成。

---

## 5. Widget 任务与 enabled

| 项 | 说明 |
|----|------|
| 任务模型 | `WidgetTask`：title、prompt（用户自然语言）、intervalMinutes、cacheTtlSeconds、cacheSlot、**enabled** |
| 设置页 | 每条任务可编辑；**启用** Checkbox 控制是否参与定时 |
| 定时 | 仅 `enabled=true` 的任务登记 WorkManager（`widget_task_{id}`） |
| 展示 | 单页 RemoteViews + **◀ ▶** 循环切换（MIUI 兼容）；页数 = enabled 任务数；↻ 只刷当前页 |
| 默认 | 「AI 1h 速报」+「持仓盈亏」两条 enabled 任务 |

---

## 6. API 地址

| 场景 | `baseUrl` 示例 |
|------|----------------|
| 默认 | `http://127.0.0.1:8000`（真机需 `adb reverse tcp:8000 tcp:8000`） |
| 模拟器 | `http://10.0.2.2:8000` |
| 真机 + 同一 WiFi | `http://<电脑局域网IP>:8000` |

`X-API-Key` 需与后端 `.env` 中 `API_KEY` 一致（默认 `local-dev-key`）。

---

## 7. 本地调试

1. 启动后端：`cd widget-agent-server && uvicorn app.main:app --reload --host 0.0.0.0 --port 8000`
2. Run `app`；对话页发 prompt 或点快捷芯片
3. 设置页配置 Widget 任务；长按桌面添加 Widget 小组件
4. Widget 首添立即请求；点 **↻** 强制刷新（忽略缓存 TTL）

---

## 8. 与技术方案章节对照

| 想了解 | 读方案章节 |
|--------|------------|
| 全系统图、三种交互 | 零章 §0.1～0.3 |
| 数据结构 | §五 |
| HTTP API | §八 |
| 网络 / Widget / Worker | §十 |
