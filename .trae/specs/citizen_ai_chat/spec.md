# 市民管理界面 AI 聊天对接 - 产品需求文档

## Overview
- **Summary**: 在城市核心 GUI 的「市民管理」标签页（第三个界面：城市信息/地图之后实际菜单项为 `市民管理`；本项目按现有菜单项即在 `市民管理` tab 的每行市民卡片新增「聊天」按钮）中接入 OpenAI 兼容协议的大模型能力，玩家点击「聊天」后先选择自己的模型配置（从「域名管理界面」管理的 API 端点 + 模型列表中选择），打开一个独立的聊天窗口与该市民进行 NPC 化对话。对话回复由客户端直连玩家配置的 LLM Endpoint（与现有的皮肤下载中心 HTTP 调用风格一致），并使用市民属性（姓名、职业、年龄、性别、所在城市等）拼装系统提示词，让回答具备角色人格。
- **Purpose**: 解决原有 NPC 交流缺失「自然语言对话」的问题，让玩家可以随时在市民管理界面与任何市民用自然语言互动，增强城市经营沉浸感和代入感；同时复用玩家已有的第三方 OpenAI 兼容中转/直连域名（SenseNova / OneAPI / 其它 OpenAI-Compatible 账号池），不强制单一供应商。
- **Target Users**: 单机/多人服务器里加入了城市并拥有市民管理权限的成员（`OFFICIAL+` 或 OP≥2），以及在客户端通过配置界面填入自己 API Key 的玩家。

## Goals
- 在市民管理 tab 的每行市民卡片按钮组（现有「皮肤 / 改名 / 解雇 / 流放」旁）新增「聊天」入口，按钮仅当玩家拥有管理权限时显示。
- 提供与用户截图风格一致的「域名 / 模型管理」子界面（作为客户端侧配置面板）：管理多个 OpenAI 兼容域名（Endpoint 地址、API Key、兼容协议、可选代理）、每个域名下挂载的模型列表、切换默认模型；支持手动添加模型、测试连通、设置全局默认域名 + 默认模型。
- 点击市民卡片「聊天」→ 弹出模型选择器（列出玩家已配置的所有可用模型）→ 选中后打开专用聊天对话框，与该市民进行一对一自然语言对话。
- 对话请求由客户端发起（服务端不持有玩家的 API Key；网络请求链路完全走玩家本地 Java HttpClient），请求体为 OpenAI `/v1/chat/completions` 格式，解析响应并追加在对话框；支持会话上下文（最近 N 轮）、停止响应、手动清空上下文。
- 服务端提供市民资料查询包（返回用于 prompt 的结构化字段：姓名、性别、年龄、职业/职位、工作状态、城市名、最近状态文本、家庭角色、性格/兴趣摘要），由客户端发送请求并用于拼装 system prompt，避免客户端 UI 重复去读取市民列表。
- 所有域名/模型/默认选项持久化到客户端配置文件 `config/simukraft-client.toml` 的 `citizenAi` 小节（参考 `citizen_skin` 小节的 API 管理模式）。

## Non-Goals (Out of Scope)
- 不会把市民的回答用于改数据（工作分配、改名、解雇），聊天仅为沉浸感的只读互动。
- 不会在服务端持有或中转任何 API Key，避免合规与安全风险。
- 不提供内置的官方全局模型或默认 API Key，玩家必须自备。
- 不实现语音 / 图片输入，仅文本对话。
- 不做流式响应（先做整段返回，后续迭代可扩展 streaming）。
- 不做多玩家共享会话，仅发起者本地可见对话。
- 不修改已有皮肤/下载中心相关功能。

## Background & Context
- 当前 GUI 层级：城市核心界面 = `CityCoreScreenOpener`（LDLib2 / Taffy），左侧菜单：OP 城市管理、城市信息、城市地图、编辑城市、城市升级、**市民管理**、官员管理、财政管理。其中「市民管理」对应 tab `citizens`，由 `CityCitizenManageResponsePacket` 到达后 `window.openTab` 渲染。
- 每行市民渲染入口：`citizenRow(packet, citizen, renameCitizenId, renameField, renameDialogHolder, skinTargetCitizenId, skinTargetCurrentPath, skinDialogHolder)`。按钮组目前在 `packet.canManage()` 为真时显示：皮肤 / 改名 / 解雇 / 流放。
- 客户端已存在 HTTP 调用范式：`client/citizen/CitizenSkinDownloadService.java` 使用 `java.net.http.HttpClient`（默认超时 + 跟随重定向），可以复用同一套 HTTP 客户端工具。
- 客户端侧配置已有范式：`ClientConfig.java` 使用 `ModConfigSpec` + `SPEC.save()` 写入 `simukraft-client.toml`；`CatalogApi` 记录 + `catalogList` + `activeUrl` 的结构可以直接复制出「AI 域名 + 模型 + 默认选中」的 1:N 存储。
- 用户截图明确 OpenAI 兼容协议，SenseNova 展示为 `https://token.sensenova.cn` + `/v1/chat/completions`，模型列表 `deepseek-v4-flash/sensenova-6.7-flash-lite/glm-5.2/sensenova-u1-fast/...`；本功能按「标准 OpenAI Chat Completions」实现。

## Functional Requirements
- **FR-1（聊天按钮入口）**: 市民管理 tab 的每行市民卡片，在 `canManage()` 按钮组追加「聊天」按钮（翻译 key `screen.simukraft.city_core.citizen_manage.chat`），按钮仅对有权限的操作者可见且点击合法。
- **FR-2（模型选择器）**: 点击「聊天」按钮后弹出模型选择对话框，列出玩家所有「启用」的域名→模型，显示域名别名、模型名、协议/基础地址；用户选择一个模型并点击「开始对话」，或点「管理模型」跳转到域名管理界面。
- **FR-3（域名管理界面/AI配置）**: 在城市核心 GUI 中新增一个入口（例如市民管理面板顶栏加「AI 设置」按钮，或者聊天对话框里加「齿轮→管理域名/模型」按钮），打开域名管理面板。功能：
  - 域名列表（别名 + 基础地址 + 协议 + 状态）
  - 新增/编辑域名：别名、基础地址、API Key（密文显示，有眼睛切换）、协议 `OpenAI (通用)`、接口预设路径、请求地址拼接预览、启用/停用、连通性检测（`GET/POST` 一个轻量端点）
  - 每个域名下管理模型列表：手动添加/批量粘贴、从「获取可用模型」拉取列表、开关启用、当前默认模型标记
  - 全局默认：设一个「默认域名 + 默认模型」，对话时无需每次选择（第一次打开时自动预填）
  - 删除域名/模型：删除中若为默认项自动回退到第一个启用项
- **FR-4（Chat 对话框）**: 选中模型后，打开新的 Chat 对话框：
  - 顶部：市民头像 + 姓名 + 职业/年龄/性别摘要；右上：重新生成、清空上下文、切换模型、关闭
  - 中部：滚动消息列表（玩家消息右对齐灰色气泡，市民消息左对齐白/羊皮纸气泡，带打字中 Loading indicator）
  - 底部：输入框 + 发送按钮 + 停止按钮
- **FR-5（请求体组装 + 服务端下发市民资料）**: 客户端向服务端发送 `CitizenChatContextRequestPacket(citizenId)`，收到 `CitizenChatContextResponsePacket`（含 name/gender/age/jobKey/workStatus/cityName/cityLevel/personality/hobbies/familyRole/recentEvents）。客户端用模板拼成 system prompt（中文、符合角色风格），然后按 OpenAI Chat Completions 组装 messages：[{role:system,...}, {role:user,...}, {role:assistant,...}, ...]。
- **FR-6（响应解析与重试）**: HTTP 请求异步执行（不阻塞 UI 线程）。超时 60s，单域名并发 1。成功解析：`choices[0].message.content`；失败分情况提示（网络错误/认证错误/模型不存在/配额不足/JSON 解析失败），有 UI toast 或对话框内红字；支持 1 次重试（仅网络类错误）。
- **FR-7（上下文窗口）**: 客户端为每次聊天会话维护一个上下文，默认保留最近 20 条消息（system 始终置顶）。超过上限自动丢弃最老的 user/assistant 对。提供手动「清空上下文」按钮。
- **FR-8（持久化与隐私）**:
  - 域名/模型/默认值写到 `ClientConfig` 的 `citizenAi` 小节。
  - API Key 存储在本地客户端配置文件中，不通过网络同步、不上传服务端。
  - 聊天消息仅存在当前会话内存，关闭窗口即清空。不写入存档。
- **FR-9（权限与可见性）**:
  - 聊天按钮仅 `packet.canManage()` 为真（即 OP≥2 或 OFFICIAL+）时渲染。
  - 服务端 `CitizenChatContextRequestPacket.handle` 再次校验权限（与皮肤包同样的双路径），未授权返回失败。
  - 模型选择器在玩家未配置任何模型时显示「去 AI 设置添加域名」提示。

## Non-Functional Requirements
- **NFR-1（性能/UI）**: 打开对话框/模型选择器的首帧延迟 ≤100ms（本地 UI）。发送消息后 UI 显示 Loading，切换 tab 不丢失当前会话状态（直到关闭窗口）。
- **NFR-2（兼容/协议）**: 仅使用标准 OpenAI `/v1/chat/completions` 格式与响应结构，兼容 SenseNova / OneAPI / XQ / Aput / Lapin / 自建兼容转发。协议层不做厂商私有分支。
- **NFR-3（安全）**: API Key 绝不写入日志/任何上传网络包。日志仅打印 endpoint host + model，打印 `Authorization: Bearer sk-***` 做脱敏；配置界面默认隐藏 Key。
- **NFR-4（稳定性）**: HTTP 调用统一超时、统一错误处理；UI 线程绝不阻塞。异常时必须恢复到可操作状态（关闭 Loading、停止按钮恢复、不锁死）。
- **NFR-5（可维护）**: 代码组织（不把全部逻辑塞进 `CityCoreScreenOpener`）：拆成 `client/citizen/ai/CitizenAiClientConfig.java`（数据模型+存取）、`client/citizen/ai/CitizenAiChatService.java`（HTTP+解析）、`client/citizen/ai/AiSettingsPanel.java`、`client/citizen/ai/AiModelPickerDialog.java`、`client/citizen/ai/CitizenChatDialog.java`；网络包放到 `network/citizen/chat/`。

## Constraints
- **Technical**: Minecraft 1.21.1 NeoForge, Java 21, LDLib2 / Taffy UI 组件，网络用 `java.net.http.HttpClient`（无额外依赖）。
- **Business**: 不提供任何内置 AI 供应商，不对接官方付费服务；完全由玩家自备。
- **Dependencies**: 复用 `ClientConfig + ModConfigSpec` 持久化范式；复用 `CityCitizenManageResponsePacket.canManage()` + `CityPermissionLevel.OFFICIAL` 权限体系；复用 `CitizenAvatarFactory`、`SimuKraftUiTheme`、`memberActionButton/contentButton` 等 UI 部件。

## Assumptions
- 玩家的 OpenAI-Compatible 供应商都支持 `POST <base>/v1/chat/completions`，Header `Authorization: Bearer <api-key>`，Body JSON 至少 `model/messages/temperature`。
- 玩家在单人世界或本地客户端持有 API Key，对本机文件系统可写（NeoForge client config 已满足）。
- 服务端不会主动发起 AI 请求；所有 AI 调用走客户端本地网络。
- 网络环境若需系统代理，默认使用 JVM 代理参数 `-Dhttp.proxyHost`/`-Dhttps.proxyHost`（`HttpClient.newBuilder().proxy(ProxySelector.getDefault())` 默认行为）。

## Acceptance Criteria

### AC-1：聊天按钮渲染与权限
- **Given**: 已打开城市核心→市民管理 tab，且玩家是城市 OFFICIAL+ 或 OP≥2
- **When**: 渲染任意一行市民卡片
- **Then**: 该行按钮组在「皮肤」与「改名」之间（或同组内）出现「聊天」按钮
- **Verification**: `programmatic`
- **Notes**: 非管理员行不会出现该按钮

### AC-2：聊天按钮无模型配置的友好提示
- **Given**: 玩家未配置任何 AI 域名/模型
- **When**: 点击某市民「聊天」
- **Then**: 弹出模型选择器或直接提示「未配置模型，前往 AI 设置」，并提供跳转到域名管理的按钮
- **Verification**: `human-judgment`

### AC-3：域名管理——新增域名并测试连通
- **Given**: 打开 AI 设置面板
- **When**: 新增一个 OpenAI 兼容域名（别名、基础地址、API Key、协议 `OpenAI 通用`）并点击「测试连通」
- **Then**: 结果显示「成功」或具体错误（401/无效地址/超时），并保存到 client config
- **Verification**: `human-judgment`

### AC-4：域名管理——模型列表管理与默认选中
- **Given**: 已存在一个域名
- **When**: 手动添加或一键获取可用模型，并把某条模型设置为全局默认
- **Then**: 模型出现在该域名列表并高亮「默认」；配置文件 `citizenAi` 中能看到 `defaultEndpointId` + `defaultModelId`
- **Verification**: `programmatic`

### AC-5：模型选择器——启动对话
- **Given**: 玩家已配置域名+模型并有默认值
- **When**: 点击市民「聊天」→ 选择默认模型 → 点「开始对话」
- **Then**: 打开新聊天对话框，顶部显示市民资料摘要，服务端已通过 `CitizenChatContextResponsePacket` 返回数据
- **Verification**: `programmatic`

### AC-6：发送/接收消息闭环
- **Given**: 聊天对话框已打开、默认模型合法、网络可达
- **When**: 输入一句问候（如「你好，今天工作怎么样？」）点发送
- **Then**: UI 显示 Loading → 收到回复并追加市民气泡，无报错；HTTP 请求体中 messages[0].role=system 且包含市民姓名
- **Verification**: `human-judgment` + `programmatic`（抓日志脱敏确认 system prompt 内容非空且含 name）

### AC-7：错误处理
- **Given**: 聊天模型 API Key 故意填错
- **When**: 发送消息
- **Then**: 对话框内红色错误提示「认证失败（401），请在 AI 设置检查 Key」，不崩溃，可继续操作
- **Verification**: `human-judgment`

### AC-8：服务端权限再校验
- **Given**: 玩家没有市民管理权限，却伪造发送 `CitizenChatContextRequestPacket`
- **When**: 服务端收到包
- **Then**: 拒绝返回资料，返回一个空或错误状态；玩家 UI 显示「没有权限」
- **Verification**: `programmatic`

### AC-9：上下文保持与清空
- **Given**: 玩家连续对话 5 轮，都成功返回
- **When**: 第 6 轮手动点击「清空上下文」，再发送一句新的
- **Then**: 下一条请求 messages 中不再包含前 5 轮 user/assistant 历史（仅保留最新一轮 user 和 system）
- **Verification**: `human-judgment` + `programmatic`（debug 日志或本地断言确认上下文大小）

### AC-10：隐私与安全
- **Given**: 玩家完成一次对话并在 log 中搜索 Bearer
- **When**: 检查 `logs/latest.log` 和 `debug.log`
- **Then**: 不出现明文 API Key（仅出现 `Bearer sk-****` 或完全省略 Authorization 头日志）
- **Verification**: `programmatic`

## Open Questions
- [ ] 「域名管理界面」的入口放在哪里最合适？建议 A：聊天对话框内齿轮按钮；B：市民管理面板顶栏「AI 设置」；C：两者都放。（本次按「A+B 都放」实现，以便切换）
- [ ] 连通性测试使用哪个请求？选项：(1) `POST /v1/chat/completions` 用最小的假消息（消耗 1 token）；(2) `GET /models` 仅检查列表返回 200。本次先实现 (2)，失败时给出可切换到 (1) 的提示。
- [ ] 系统提示词模板是否要做成可配置文本（资源包 lang 或 client config）？本次写死为内置模板+中英双语，后续迭代开放。
- [ ] 是否需要 OP 可强制关闭全局 AI 功能？服务器侧 ServerConfig 开关 `enableCitizenAiChat = true`（默认 true），可由 OP/配置文件控制。本次加上。
