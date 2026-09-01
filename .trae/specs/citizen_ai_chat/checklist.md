# 市民管理界面 AI 聊天对接 - 验证检查清单

> 验收顺序与 tasks.md 保持一致；每完成一个任务，把对应检查点更新为 `[x]` 并附一句说明（如有必要）。

## Task 1: 配置数据模型与持久化
- [x] `ClientConfig.java` 存在 `citizenAi` 配置块，字段包含：endpoint 列表（可序列化/反序列化）、默认 endpointId、默认 modelId。
- [x] 增删 endpoint：`addAiEndpoint / updateAiEndpoint / removeAiEndpoint` 三个方法能正确修改内存 + 触发 `SPEC.save()`。
- [x] 删除当前默认 endpoint/model 时，系统自动回退到第一个启用项或置空（不会出现「默认指向已删项」）。
- [x] 日志输出 / UI 显示 / 调试打印 API Key 全部为 `***` 或只显示前缀长度（Task1 无 logger 输出；`maskApiKey` 实现保证调用侧可安全拼接）。
- [x] `ServerConfig.java` 存在 `ENABLE_CITIZEN_AI_CHAT`，默认 true；文档注释写明用途。

## Task 2: 网络包（CitizenChatContext Request/Response）
- [x] `CitizenChatContextRequestPacket` 和 `CitizenChatContextResponsePacket` 已在网络注册处注册（ModNetwork.playToServer / playToClient，id 为 `simukraft:citizen_chat_context_request/response`）。
- [x] 服务端 handle 先读 `ServerConfig.ENABLE_CITIZEN_AI_CHAT`：false 返回 `errorCode=3`。
- [x] 权限二次校验：OP≥2 或城市 OFFICIAL+ 通过；否则返回 `errorCode=1`。
- [x] 市民不存在 → `errorCode=2`；正常情况下返回 name/gender/age/jobKey/cityName/cityLevel 非 null（空串允许）、recentEvents 长度为 3。
- [x] 客户端侧包处理入口存在：`receiveOnClientThread`（lastReceived + future.complete）+ `ClientboundNetworkBridge.handleCitizenChatContextResponse`；并对外提供 `requestFuture(UUID, long)`。

## Task 3: AI 聊天服务
- [x] `CitizenAiChatService` 使用统一的 `HttpClient`，60s 超时、followRedirect=NORMAL；`sendMessage` 返回 `CompletableFuture`，不在主线程阻塞。
- [x] system prompt 拼装包含：市民姓名、性别、年龄、职业、城市、性格摘要、家庭角色；中文环境优先中文模板，空字段自动跳句子。
- [x] history 轮数超限（`maxHistoryPairs*2`）自动丢弃最老 user/assistant 对，但 system 永远第 0 条。
- [x] 错误分类完备：401→auth_failed(认证失败)、404→not_found(路径错误)、429→rate_limit(限流)、5xx→server_error(服务错误)、IO/超时→network_general/timeout、解析异常→parse_failed。
- [x] 网络类错误（network_general / timeout / server_error）自动重试 1 次；认证/解析/404 等不重试。
- [x] 所有 logger 调用不输出明文 `Authorization` 值：本服务未使用任何 logger，异常消息不含 apiKey；debug 侧不暴露敏感信息。
- [x] JSON 序列化不引入新依赖：复用项目既有 Gson（同 CitizenSkinDownloadService 使用的 `com.google.gson`）。

## Task 4: AI 设置面板
- [ ] 市民管理面板顶栏出现「AI 设置」按钮；模型选择器右上角出现齿轮「AI 设置」按钮；两者打开同一个面板。
- [ ] 域名编辑行：别名、基础地址（https/http）、API Key（默认密文，可切换显示）、协议（默认 `OpenAI 通用` + 路径 `/v1/chat/completions` 预览）、启用开关、删除按钮，均存在。
- [ ] 模型列表支持：手动输入 modelId 新增 → 保存；一键获取可用模型（GET /v1/models）并批量填入；每行设为默认/启用/删除。
- [ ] 「测试连通」按钮点击后执行 GET models，UI 即时显成功/错误，不卡死窗口。
- [ ] 保存后关闭再打开，读取到的值与刚刚保存一致（即 `SPEC.save()` 生效）。

## Task 5: 模型选择对话框
- [ ] 点击某市民卡片的「聊天」按钮能弹出模型选择对话框。
- [ ] 对话框列出所有启用 endpoint 下启用的 model：域名 alias/baseUrl/modelId 三栏信息齐全；默认项打标。
- [ ] 空配置：对话框直接显示「未配置模型」与跳转「AI 设置」按钮。
- [ ] 选中后点击「开始对话」：回调拿到 `(endpoint, model)` 非 null 对；点「取消」或 ESC 正常关闭无副作用。

## Task 6: 聊天对话框与会话
- [ ] 打开聊天对话框立即发送一次 `CitizenChatContextRequestPacket`；`errorCode=1/2/3` 分别显示对应中文错误提示并禁用输入框。
- [ ] 顶部显示市民头像+姓名+职业/年龄/性别/城市摘要。
- [ ] 聊天区气泡：玩家（右对齐深色/玩家色）、市民（左对齐羊皮纸风）、长文本自动换行。
- [ ] Loading：点击发送后输入框禁用、出现「对方正在输入...」或三点动画；收到回复或错误后恢复。
- [ ] 重新生成：把最后一条 user 消息作为历史重放；不重复增加一条 user 气泡。
- [ ] 清空上下文后，下一次 HTTP 请求 messages 只有 system + 当前 user（history 被清为 0 对）。
- [ ] 切换模型：不丢失现有历史，仅替换 future 要发的 endpoint/model；下次发送使用新模型。
- [ ] 停止按钮：发送中可 cancel，窗口恢复可输入状态。
- [ ] 普通成员（无权限）打开市民管理时，该行按钮组不渲染「聊天」按钮；OP/OFFICIAL 渲染。

## Task 7: 翻译与文案
- [ ] `zh_cn.json` 与 `en_us.json` 两份新增 key 完全对称、无缺失；JSON 格式合法（可用 IDE 格式化或解析校验）。
- [ ] 错误提示与成功提示语义清晰，不出现 `TODO / TBD / placeholder` 等开发期占位字符串。
- [ ] 「聊天」按钮文案长度（≤5 字中英文）不撑宽 44px 现有 memberActionButton 宽度（必要时调 48px）。

## Task 8: 集成编译 + 冒烟
- [ ] `./gradlew compileJava`（或等效 build 子任务）退出 0。
- [ ] 打开城市核心 GUI → 市民管理 → 点击有管理权限的市民 → 点「聊天」→ 整个链路不崩、无 ClassNotFound/MethodNotFound。
- [ ] 手动配一个真实 SenseNova/其它 OpenAI 兼容域名（若有），或用 `nc -l` / 本地 mock server 收一次请求：请求头 Authorization 格式正确、请求体 `model/messages` 合法、system prompt 非空。
- [ ] 故意把 Key 改错：发送消息给出红字错误提示「认证失败」，不崩溃，可继续操作。
- [ ] 搜索 `logs/latest.log` / IDE 控制台：grep `Bearer sk-` / `apiKey` 全文无完整明文泄漏。

## Task 9: README 更新（仅用户要求时执行）
- [ ] README「功能」段落追加「市民 AI 对话」与「AI 域名/模型管理」小节，风格与原有皮肤条目一致。
- [ ] 文档说明服务器侧 `enableCitizenAiChat` 开关和客户端侧配置文件位置。
