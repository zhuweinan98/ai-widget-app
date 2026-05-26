# ai-widget-app 架构说明

> 完整平台设计见同目录上级文档：[桌面任务平台-技术方案.md](../桌面任务平台-技术方案.md)（v11.0）  
> 后端仓库：`../widget-agent-server`

## 1. 定位

本 App 是 **AI 桌面任务平台的 Android 客户端**。手机侧 **不跑大模型**，只负责：

1. 展示（对话页 / 桌面 Widget）
2. 调用后端 HTTP API（`user_id` + `message`）
3. 本地缓存与降级（Widget 阶段，见技术方案 §十）

当前阶段：**Agent 对话页** + **桌面 Widget（aihot 1h 速报）**。

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
HomeWidgetRefreshWorker → AgentRepository.chat
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
│   ├── Models.kt              # ChatRequest、WidgetResult、SkillMetadata
│   ├── AppPrefs.kt            # API 地址/Key、统一 user_id
│   ├── SessionPrefs.kt        # SSE 开关（API/userId 代理 AppPrefs）
│   ├── Presets.kt             # 默认 URL/Key、对话快捷 prompt
│   ├── WidgetConfig.kt        # Widget 默认 prompt / 常量
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
│   ├── AppShellScreen.kt      # 导航外壳 + 对话页（输入栏、快捷芯片）
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
  → ChatRequest(userId, message)
  → AgentRepository.chat / chatStream(source="chat")
  → AppShellUiState.chatMessages + agentTraceLines 更新
```

### Widget 刷新

```
定时 / 手动 ↻
  → HomeWidgetRefreshWorker
  → 检查 WidgetCache TTL（force_refresh 可跳过）
  → AgentRepository.chat(source="widget/{taskId}/{trigger}")
  → WidgetCache.saveSuccess → HomeWidgetCoordinator.renderAllWidgets
  → periodic 时 WidgetRunLogStore.append（设置页可查看）
```

**约定**（与技术方案 §五、§八 一致）：

- 请求体仅 `user_id`、`message`
- 响应 `WidgetResult`：`title`、`content`、`status`、`debug_trace` 等
- 对话页与 Widget **共用** `AppPrefs` 的 baseUrl / apiKey / **user_id**（首次启动 UUID，设置页可改）

---

## 5. Widget 任务与 enabled

| 项 | 说明 |
|----|------|
| 任务模型 | `WidgetTask`：title、prompt、intervalMinutes、cacheTtlSeconds、cacheSlot、**enabled** |
| 设置页 | 每条任务可编辑；**启用** Checkbox 控制是否参与定时 |
| 定时 | 仅 `enabled=true` 的任务登记 WorkManager（`widget_task_{id}`） |
| 展示 | 桌面 Widget 展示 **[WidgetTaskStore.primaryDisplayTask]** 的缓存 |
| 默认 | 一条「AI 1h 速报」，prompt = `WidgetConfig.REFRESH_MESSAGE` |

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
3. 设置页配置 Widget 任务；长按桌面添加 **AI 1h 速报** 小组件
4. Widget 首添立即请求；点 **↻** 强制刷新（忽略缓存 TTL）

---

## 8. 与技术方案章节对照

| 想了解 | 读方案章节 |
|--------|------------|
| 全系统图、三种交互 | 零章 §0.1～0.3 |
| 数据结构 | §五 |
| HTTP API | §八 |
| 网络 / Widget / Worker | §十 |
