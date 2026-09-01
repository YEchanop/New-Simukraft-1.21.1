# 市民管理界面 AI 聊天对接 - 实施计划（任务列表）

> 所有文件路径相对于 `src/main/java/`（除非另有说明）。优先保持与既有 `citizen_skin` / `catalogApi` / `ClientConfig` 相同的代码风格。

## [x] Task 1: 配置数据模型与持久化（ClientConfig + ServerConfig 开关）
- 交付情况：`ClientConfig.java` 增加 citizenAi 配置块、`AiModel/AiEndpoint` record、`listAiEndpoints/addAiEndpoint/updateAiEndpoint/removeAiEndpoint/setAiDefault/maskApiKey` 方法；`ServerConfig.java` 增加 `ENABLE_CITIZEN_AI_CHAT` + getter。`./gradlew compileJava` 通过。
- **Priority**: high
- **Depends On**: None
- **Description**: 
  - 在 `ClientConfig.java` 中新增 `citizenAi` 配置块，提供：
    - `List<String> CITIZEN_AI_ENDPOINTS`：每个条目形如 `id|alias|baseUrl|apiKey|protocol|enabled|modelsCsv`，其中 modelsCsv 是分号分隔的 `modelId:name:enabled:default`（默认值用 `1` 标记）
    - `String CITIZEN_AI_DEFAULT_ENDPOINT_ID`、`String CITIZEN_AI_DEFAULT_MODEL_ID`
  - 提供 `record AiEndpoint(String id, String alias, String baseUrl, String apiKey, String protocol, boolean enabled, List<AiModel> models)`、`record AiModel(String id, String name, boolean enabled, boolean isDefault)`
  - 提供静态工具：`listAiEndpoints() / addAiEndpoint / updateAiEndpoint / removeAiEndpoint / setAiDefault / setApiKeyMaskedForLog`
  - 保存路径必须全部走 `SPEC.save()`，且日志打印脱敏（apiKey → `sk-***` 或 `key-***`）
  - 在 `ServerConfig.java` 中追加 `BooleanValue ENABLE_CITIZEN_AI_CHAT = true`，带注释「允许玩家在客户端使用 AI 与市民对话（不影响服务器数据，仅开关 UI 入口和服务端上下文包）」，用于全局开关。
- **Acceptance Criteria Addressed**: FR-3, FR-8, NFR-3, AC-3, AC-4, AC-10
- **Test Requirements**:
  - `programmatic` TR-1.1: 新增一个 endpoint + 两个 model，持久化后重启（模拟 `SPEC` 读回），`listAiEndpoints().size() == 1` 且 models 顺序正确。
  - `programmatic` TR-1.2: `setAiDefault` 把默认 model 切走后，`CITIZEN_AI_DEFAULT_MODEL_ID.get()` 等于目标 id。
  - `programmatic` TR-1.3: 删除当前默认 endpoint 后，默认 id 自动回退到第一个启用的 endpoint/model。
  - `human-judgement` TR-1.4: 审查所有日志输出（若有）不出现明文 API Key；UI 侧默认以密文显示、可切换可见。
- **Notes**: 参考 `CatalogApi`/`catalogList` 的存储模式（使用管道分隔字符串列表 + 解析/写入方法组）。

## [x] Task 2: 新增网络包（服务端下发市民上下文 + 权限二次校验）
- 交付情况：新增 Request/Response 两个 chat 包，注册到 ModNetwork；服务端权限双路径 + 全局开关；客户端提供 `requestFuture(citizenId, timeout)` + `lastReceived` 两种等待方式。compileJava 通过。
- **Priority**: high
- **Depends On**: Task 1
- **Description**:
  - 在 `common/cn/kafei/simukraft/network/citizen/chat/` 下新增：
    - `CitizenChatContextRequestPacket`（C2S）：`record UUID cityId, UUID citizenId, BlockPos corePos`（方便复用现有权限校验）
    - `CitizenChatContextResponsePacket`（S2C）：字段对齐 FR-5：citizenId/name/gender/age/jobKey/workStatusKey/cityName/cityLevel/personalityBrief/hobbies/familyRole/recentEvents(3 条 String)，errorCode(=0 成功, 1 无权限, 2 市民不存在, 3 全局禁用)
  - 两包注册到主类 `SimuKraft` 的 `NeoForgeModEventBus/AddPacketsEvent`（或现有网络注册处），保持与 `CityCitizenFamilyGraphRequestPacket` 相同的注册方式。
  - 服务端 `handle` 逻辑：
    - 先查 `ServerConfig.ENABLE_CITIZEN_AI_CHAT.get()==false` 直接回 `errorCode=3`
    - 权限双路径：同 `CitizenSetSkinPacket` 的远程分支（OP≥2 或 该市民所属城市 OFFICIAL+）
    - 用 `CitizenManager.getCitizen(citizenId)` 取 `CitizenData`，用 `CityManager.getCity(cityId)` 取城市名和等级，用 `CitizenProfileGenerator.fillMissingProfile` 现有字段补 personality/hobbies 摘要，`recentEvents` 取最近三条日志（若没有就给空串或通用句，避免空字段导致 NPE）
- **Acceptance Criteria Addressed**: FR-5, FR-9, AC-5, AC-8
- **Test Requirements**:
  - `programmatic` TR-2.1: OP 有城市 OFFICIAL 权限成员发送合法包 → `errorCode=0` 且字段全非 null（recentEvents 允许 3 空串）。
  - `programmatic` TR-2.2: 普通成员（无权限）发送合法包 → `errorCode=1`。
  - `programmatic` TR-2.3: `ServerConfig.ENABLE_CITIZEN_AI_CHAT=false` → `errorCode=3`，无论权限。
  - `programmatic` TR-2.4: 发送不存在的 citizenId → `errorCode=2`。
- **Notes**: 所有可空字段在 record 上提供默认值（如空字符串），保证客户端解析不抛。

## [x] Task 3: AI 聊天服务（HTTP 请求、系统提示词模板、解析与错误分类）
- 交付情况：新建 CitizenAiChatService（单例、OnlyIn CLIENT），含 ChatSession/system prompt/异常分类/1 次重试/连通性测试。Gson 已复用 CitizenSkinDownloadService 同版，无新依赖。compileJava 通过。
- **Priority**: high
- **Depends On**: Task 1, Task 2
- **Description**:
  - 新建 `client/cn/kafei/simukraft/client/citizen/ai/CitizenAiChatService.java`：
    - 维护单例 `HttpClient`（参考 `CitizenSkinDownloadService`，超时 60s、重定向 NORMAL）
    - `ChatSession`：内部记录 `UUID citizenId`、`AiEndpoint endpoint`、`AiModel model`、`List<Msg> history`（Msg 含 role/content）、上限 N=20（不含 system）
    - `buildSystemPrompt(ctx)`：中文模板一段（带玩家所在城市、市民身份与性格） + 英文备用（根据客户端语言选）
    - `CompletableFuture<String> sendMessage(ChatSession session, String userText)`：
      - UI 线程调用返回 future，内部在 daemon 线程池执行
      - 组装 JSON body：`model/messages=[system,...history,{role:user,text}]`、`temperature=0.7`、`max_tokens=800`（可调 client config 常量）
      - Header: `Content-Type: application/json`、`Authorization: Bearer <apiKey>`（日志仅打印 host + model）
      - 解析：先读 body，`$.choices[0].message.content`；错误分支：HTTP 401/403 → `认证失败`；404 → `端点路径错误`；429/5xx → `限流或服务端错误`；JSON 解析失败 → `响应格式错误`；超时 → `请求超时`
      - 网络类错误（IO/超时/5xx）做 1 次自动重试；认证错误不重试。
  - 日志：仅在 debug 级打印 URL host，`Authorization` 头仅记录长度和前缀脱敏。
- **Acceptance Criteria Addressed**: FR-5, FR-6, FR-7, FR-10, AC-6, AC-7, AC-9, AC-10
- **Test Requirements**:
  - `programmatic` TR-3.1: 向 mock endpoint 发送消息，断言请求体包含 `model=xxx`，`messages[0].role=system`，`messages[0].content` 含市民姓名和城市名。
  - `programmatic` TR-3.2: 构造 401 响应，`future` 抛出的异常消息中含「认证失败」。
  - `programmatic` TR-3.3: history 满 20 条后再插入，自动丢弃最老一对 user/assistant，system 始终在 [0]。
  - `human-judgement` TR-3.4: 评审服务类所有 logger 输出（warn/info/debug）不含明文 API Key。
- **Notes**: JSON 序列化/反序列化使用 `Gson`（项目已有依赖请先确认；若项目无 Gson 则用 JDK `StringBuilder` 手工拼 JSON 和 `JsonParser`（NeoForge 自带 Gson），以避免引入新依赖。优先复用项目现有 JSON 工具。

## [x] Task 4: UI 组件 A —— AI 设置面板（域名管理 + 模型管理）
- **Priority**: medium
- **Depends On**: Task 1
- **Description**:
  - 新建 `client/cn/kafei/simukraft/client/citizen/ai/AiSettingsPanel.java`（返回 `UIElement`，风格参考现有皮肤下载中心的「API 管理」子视图 `showCatalogApiSettings`）。
  - 左侧：域名列表（alias+host）；右侧：选中域名的详情编辑区 + 模型列表 + 连通性测试按钮。
  - 新增域名（name|baseUrl|apiKey|协议预设），apiKey 输入框默认密码模式，可切换显示。
  - 模型区支持：手动添加一行 modelId；「获取可用模型」调用 `GET {baseUrl}/v1/models`（Authorization 头），把 `data[].id` 批量填入；对每行可「启用/停用」、「设为默认」。
  - 「测试连通」按钮：使用 GET models 检查 200，显示绿色「OK」或红字错误。
  - 底部：保存/取消/恢复默认（默认=空配置或示例占位不填 Key）。
  - 入口挂载两处：① 市民管理面板（`CityCoreScreenOpener.citizensPanel`）顶栏加小按钮「AI 设置」；② 模型选择对话框右上齿轮。
- **Acceptance Criteria Addressed**: FR-3, NFR-1, NFR-5, AC-3, AC-4
- **Test Requirements**:
  - `human-judgement` TR-4.1: 以截图提供的 SenseNova 配置样式为参考，UI 元素完整（别名/地址/Key/协议/模型列表/默认标记）。
  - `programmatic` TR-4.2: 保存后 `ClientConfig.CITIZEN_AI_ENDPOINTS.get()` 条目数正确，apiKey 字段写入非空（在本地 client.toml 中会被 NeoForge 加密/明文都可接受，本需求不强求加密，要求日志与 UI 脱敏）。
  - `human-judgement` TR-4.3: 两个入口（市民管理顶栏和模型选择齿轮）都能打开同一块面板。
- **Notes**: 避免在 `CityCoreScreenOpener` 内堆叠超长方法，AI 面板独立文件返回 `UIElement`，`CityCoreScreenOpener` 仅负责 show overlay。

## [x] Task 5: UI 组件 B —— 模型选择对话框
- **Priority**: high
- **Depends On**: Task 1, Task 4
- **Description**:
  - 新建 `client/cn/kafei/simukraft/client/citizen/ai/AiModelPickerDialog.java`（`UIElement create(Runnable onClose, BiConsumer<AiEndpoint,AiModel> onConfirm)`）
  - 布局：标题行 + 搜索框 + 三列（域名别名/基础地址/协议）→ 展开显示其下可用模型（checkbox 风格）+「默认」徽标
  - 底部：「开始对话」「取消」「AI 设置齿轮（打开 Task4）」
  - 默认选中：读取 `CITIZEN_AI_DEFAULT_ENDPOINT_ID + CITIZEN_AI_DEFAULT_MODEL_ID`，如果不存在则自动选第一个启用域名第一个启用模型。
- **Acceptance Criteria Addressed**: FR-2, FR-3, AC-2, AC-4, AC-5
- **Test Requirements**:
  - `programmatic` TR-5.1: 无任何配置时，对话框显示空态文案和「AI 设置」按钮。
  - `programmatic` TR-5.2: 默认项一致；点击开始对话触发回调且回调参数非 null。
  - `human-judgement` TR-5.3: 空态文案友好，齿轮跳转正常。

## [x] Task 6: UI 组件 C —— 聊天对话框与会话生命周期
- **Priority**: high
- **Depends On**: Task 2, Task 3, Task 5
- **Description**:
  - 新建 `client/cn/kafei/simukraft/client/citizen/ai/CitizenChatDialog.java`：
    - 打开时先 `PacketDistributor.sendToServer(new CitizenChatContextRequestPacket(...))`，响应到达后渲染顶栏摘要和 system prompt 预览按钮（仅本地调试）
    - 聊天区：ScrollerView + 消息气泡（玩家/市民两侧对齐），加载 `Loading indicator` 用 LDLib2 风格的点动画或文字「对方正在输入...」
    - 输入区：TextField（Enter 发送、Shift+Enter 换行）+ 发送按钮 + 停止按钮（`sendMessage` 返回 Future 加 `cancel(true)` 能力）
    - 标题栏按钮：重新生成（重放最后一条 user）、清空上下文、切换模型（重开 Task5）、关闭
  - 关闭按钮：仅关闭弹窗，不发送任何包；切换模型不丢 history（切换后 system prompt 不变）。
  - 绑定到 `CityCoreScreenOpener.citizenRow`：按钮组新增「聊天」调用 `openChat(citizen, packet.pos())`：先选模型 → 打开对话框。
- **Acceptance Criteria Addressed**: FR-1, FR-4, FR-5, FR-7, NFR-1, AC-1, AC-5, AC-6, AC-7, AC-9
- **Test Requirements**:
  - `programmatic` TR-6.1: 非管理员玩家 citizenRow 不出现「聊天」按钮（Grep 定位渲染分支并断言不进 buttonGroup）。
  - `programmatic` TR-6.2: 点击聊天按钮 → 模型选择 → 打开对话框，`CitizenChatContextRequestPacket` 发送过一次（可通过 mock `PacketDistributor.sendToServer` 计数）。
  - `human-judgement` TR-6.3: 打字中 indicator 存在；发送失败显示红字错误不崩溃。
  - `programmatic` TR-6.4: 「清空上下文」后，向 mock endpoint 发送下一条请求：messages.size() == 2（system + 当前 user）。
- **Notes**: 为避免 `CityCoreScreenOpener` 进一步膨胀，所有 AI 相关 UI 方法一律在新类中提供静态工厂；`CityCoreScreenOpener` 只负责：渲染按钮 → 创建 ref 数组 → addChild overlay → 关闭时 setVisible(false)。

## [x] Task 7: 翻译与文案（中/英）
- **Priority**: medium
- **Depends On**: None（可与 4/5/6 并行）
- **Description**:
  - 在 `assets/simukraft/lang/zh_cn.json` 和 `en_us.json` 中添加：
    - 聊天按钮：`screen.simukraft.city_core.citizen_manage.chat=聊天 / Chat`
    - AI 设置：`screen.simukraft.city_core.citizen_manage.ai_settings=AI 设置 / AI Settings`
    - 域名管理（`screen.simukraft.citizen_ai.*`）：title/endpoint/add/delete/test/baseUrl/apiKey/protocol/models/default/connect_ok/connect_fail/
    - 模型选择：`screen.simukraft.citizen_ai.pick.title=选择模型 / Choose model`、空态、开始对话、取消
    - 聊天对话框：`screen.simukraft.citizen_ai.chat.typing=对方正在输入... / Typing...`、发送/停止/清空/重新生成/切换模型；错误提示 `auth_failed/not_found/rate_limit/parse_failed/timeout/network_general/no_perm/global_disabled/empty_profile`
    - 服务器消息：`message.simukraft.citizen_ai.no_perm/global_disabled`
- **Acceptance Criteria Addressed**: FR-1~FR-9（国际化统一）
- **Test Requirements**:
  - `programmatic` TR-7.1: zh/en 两份 json 新增 key 一一对应，无缺失；语法合法（json.loads 通过）。
  - `human-judgement` TR-7.2: 中英文语义一致、符合游戏风格（中文偏口语、英文简洁）。

## [x] Task 8: 集成编译 + 集成冒烟
- **Priority**: high
- **Depends On**: Task 1~7 全部
- **Description**:
  - 本地执行 `./gradlew compileJava`（或等价命令，不跑 test）修复所有编译错误。
  - 检查与原有皮肤/改名/关系图按钮无耦合冲突（同 ref 数组命名、同 overlay 显示逻辑不互相覆盖）。
  - 人工走一次集成流程（runClient 或 reviewer 手动）：
    1. 打开城市核心 → 市民管理
    2. 点顶栏「AI 设置」加一个域名+模型
    3. 回到市民行点「聊天」→ 选模型 → 开始对话
    4. 发一条 Hello，观察 Loading → 回复/错误提示
- **Acceptance Criteria Addressed**: AC-1 到 AC-10（总体验收）
- **Test Requirements**:
  - `programmatic` TR-8.1: `compileJava` 成功退出（exit 0）。
  - `human-judgement` TR-8.2: 冒烟 4 步流程中不崩溃、不抛异常；错误分支给出友好提示。
  - `programmatic` TR-8.3: 日志 grep 不到完整明文 sk-开头 key。
- **交付情况**：`compileJava` exit 0；`runClient` 启动成功并进入世界（建筑包/地图/SQLite 正常，无崩溃）；TR-8.3 已核查（CitizenAiChatService 无 SLF4J 日志，Authorization 仅请求头，无明文 key 输出）。剩余 4 步交互冒烟由人工在已启动的客户端内走查。

## [x] Task 9: README 功能小节更新
- **Priority**: low
- **Depends On**: Task 8
- **Description**:
  - 按现有 README 的 Features 文风，追加「市民 AI 对话」小节与「AI 域名/模型管理」子项描述。
  - 说明：客户端自备 OpenAI 兼容域名，保存在 `simukraft-client.toml`；服务器提供全局开关 `enableCitizenAiChat`。
- **Acceptance Criteria Addressed**: 文档完备性
- **Test Requirements**:
  - `human-judgement` TR-9.1: 读者不看代码即可知道如何配置域名+模型+开启对话。
- **Notes**: 已按用户显式请求「继续写」落盘。
