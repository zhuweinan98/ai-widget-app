# ai-widget-app 架构说明

> 完整平台设计见同目录上级文档：[桌面任务平台-技术方案.md](../桌面任务平台-技术方案.md)（v11.0）  
> 后端仓库：`../widget-agent-server`

## 1. 定位

本 App 是 **AI 桌面任务平台的 Android 客户端**。手机侧 **不跑大模型**，只负责：

1. 展示（界面 / 未来的桌面 Widget）
2. 调用后端 HTTP API（`user_id` + `message`）
3. 本地缓存与降级（Widget 阶段，见技术方案 §十）

当前阶段：**联调测试页**（对应后端 `/test` 浏览器页），尚未实现桌面 Widget 与 WorkManager。

---

## 2. 分层结构（现状）

```
┌─────────────────────────────────────────┐
│  MainActivity          应用入口          │
└──────────────────┬──────────────────────┘
                   ▼
┌─────────────────────────────────────────┐
│  ui/TestScreen         Compose 界面      │
│  ui/TestViewModel      状态与业务编排    │
└──────────────────┬──────────────────────┘
                   ▼
┌─────────────────────────────────────────┐
│  network/AgentRepository  发请求、解析 SSE │
└──────────────────┬──────────────────────┘
                   ▼
┌─────────────────────────────────────────┐
│  network/RetrofitClient + ApiService     │
└──────────────────┬──────────────────────┘
                   │ HTTP + X-API-Key
                   ▼
         widget-agent-server (FastAPI)
```

| 包 / 目录 | 职责 |
|-----------|------|
| `data/` | 与后端对齐的 JSON 模型、默认配置、SharedPreferences |
| `network/` | Retrofit 接口、OkHttp、SSE 流解析 |
| `ui/` | Compose 界面与 ViewModel |
| `ui/theme/` | Material3 主题色 |

---

## 3. 目录与文件

```
app/src/main/java/com/example/aiwidget/
├── MainActivity.kt              # 启动 Activity，挂载 TestScreen
├── data/
│   ├── Models.kt              # ChatRequest、WidgetResult、SkillMetadata
│   ├── Presets.kt             # 默认 URL/Key、AI HOT 快捷 message
│   └── TestPrefs.kt           # 测试页配置持久化
├── network/
│   ├── ApiService.kt          # Retrofit 声明：chat、getSkills
│   ├── RetrofitClient.kt      # 动态 baseUrl、鉴权头、超时
│   └── AgentRepository.kt     # 仓库：JSON 对话 + SSE 流
└── ui/
    ├── TestScreen.kt          # 测试 UI（芯片、发送、结果区）
    └── TestViewModel.kt       # UiState、send、refreshSkills
```

---

## 4. 一次「发送」的数据流

```
用户点「发送」
  → TestViewModel.send()
  → ChatRequest(userId, message)
  → AgentRepository.chat() 或 chatStream()
  → POST {baseUrl}/api/v1/agent/chat[/stream]
  → 后端 AgentEngine（LangGraph + Skill + Tool）
  → WidgetResult JSON
  → TestUiState 更新 → TestScreen 重绘
```

**约定**（与技术方案 §五、§八 一致）：

- 请求体仅 `user_id`、`message`（`message` 必填）
- 响应 `WidgetResult`：`title`、`content`、`status`、`debug_trace` 等
- App **不上传** Widget 的 `cache_slot`（仅未来 Worker 本地使用）

---

## 5. API 地址说明

| 场景 | `baseUrl` 示例 |
|------|----------------|
| 模拟器访问本机后端 | `http://10.0.2.2:8000`（`Presets.DEFAULT_BASE_URL`） |
| 真机 + 同一 WiFi | `http://<电脑局域网IP>:8000` |
| 真机 + `adb reverse tcp:8000 tcp:8000` | `http://127.0.0.1:8000` |

`X-API-Key` 需与后端 `.env` 中 `API_KEY` 一致（默认 `local-dev-key`）。

---

## 6. 目标架构（技术方案 §九、§十，待实现）

```
MainActivity（对话 + 预设）
TaskWidgetProvider（桌面组件）
WidgetUpdateWorker（WorkManager 定时）
        ↓
WidgetHelper（schedule / render / ensureUserId）
        ↓
RetrofitClient（与现网层复用）
```

三种入口 **共用** `POST /api/v1/agent/chat`，仅 `message` 来源不同。

---

## 7. 与技术方案章节对照

| 想了解 | 读方案章节 |
|--------|------------|
| 全系统图、三种交互 | 零章 0.1～0.3 |
| Android 类索引（终态） | 0.2 前端表 |
| 数据结构 | §五 |
| HTTP API | §八 |
| 目标目录结构 | §九 |
| 网络 / Widget / Worker 细节 | §十 |
| 踩坑与 MVP 注意 | §十三 |

---

## 8. 本地调试

1. 启动后端：`cd widget-agent-server && uvicorn app.main:app --reload --host 0.0.0.0 --port 8000`
2. Android Studio Run `app`
3. 测试页填 API 地址与 Key，点「发送」或 AI HOT 快捷芯片

详见项目根目录说明（或团队内联调文档）。
