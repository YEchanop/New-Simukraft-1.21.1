# AI 模型选择器折叠与记忆功能 - The Implementation Plan (Decomposed and Prioritized Task List)

## [x] Task 1: ClientConfig 新增按端点上次模型映射配置项
- **Priority**: high
- **Depends On**: None
- **Description**:
  - 在 `ClientConfig.java` 的 `citizenAi` push 区块新增 `CITIZEN_AI_ENDPOINT_LAST_MODEL_MAP` 配置项（`ConfigValue<List<? extends String>>`），格式 `endpointId|modelId`，`defineListAllowEmpty`，默认空列表，配注释说明用途。
  - 新增 `getLastModelByEndpoint(String endpointId)`：解析列表返回该端点上次 modelId 或 null（格式错误条目跳过）。
  - 新增 `setLastModelByEndpoint(String endpointId, String modelId)`：去旧加新并 `SPEC.save()`；若 modelId 为 null 或空则移除该端点条目。
  - 修改 `removeAiEndpoint(String endpointId)`：从 LAST_MODEL_MAP 中一并移除对应端点条目。
  - 新增条目校验方法 `isEndpointLastModelEntry(Object value)`（至少含 1 个 `|`）。
- **Acceptance Criteria Addressed**: AC-3, AC-4, AC-5
- **Test Requirements**:
  - `programmatic` TR-1.1: `setLastModelByEndpoint("e1","m1")` 后，`getLastModelByEndpoint("e1")` 返回 `"m1"`，配置列表大小 +1。
  - `programmatic` TR-1.2: 重复调用 `setLastModelByEndpoint("e1","m2")`，配置列表中该端点仅保留 1 条且 value 为 m2。
  - `programmatic` TR-1.3: 调用 `removeAiEndpoint("e1")` 后，`getLastModelByEndpoint("e1")` 返回 null。
  - `programmatic` TR-1.4: 配置中含 `"bad_entry_no_pipe"` 及 `"  |  "` 等非法条目，`listAiEndpoints()` 与 `getLastModelByEndpoint()` 均不抛异常且跳过。
- **Notes**: 保持现有 `CITIZEN_AI_DEFAULT_ENDPOINT_ID / MODEL_ID` 行为不变；新增方法仅在确认切换模型时调用。

## [x] Task 2: AiModelPickerDialog 端点折叠/展开 UI + 会话态记忆
- **Priority**: high
- **Depends On**: Task 1
- **Description**:
  - 在 `AiModelPickerDialog.create()` 的 state 区域新增折叠态容器：`Map<String,Boolean> collapsed = new HashMap<>()`（或用持有数组包装），key 为 endpointId；默认值 false（展开）。为避免匿名类引用问题，使用 `final java.util.Map[]` 或 `final Object[]` holder。
  - 改造 `endpointSection()`：在头行最左侧增加折叠图标 Label（「▼」=展开 /「▶」=折叠）；为折叠图标 Label 与头行空白处绑定 `MOUSE_DOWN` 点击回调：切换 `collapsed[epId]` 布尔值并触发 `rebuildRef.run()` 重渲染。
  - 折叠时仅渲染头行、不渲染模型行；展开时原样渲染模型行列表。
  - 头行右侧（协议标签后）追加「上次：xxx」微标签：调用 `ClientConfig.getLastModelByEndpoint(ep.id())`；非空时按 `上次：{modelId}` 显示，超过 20 字符截断为前 17 字符 + `..`，颜色用 `DIALOG_SUBTEXT`，字号 9。
  - 搜索态（搜索框非空）若端点存在可见模型，强制忽略 collapsed 标记并渲染模型（保证匹配结果可见）；折叠图标仍按实际状态展示。
- **Acceptance Criteria Addressed**: AC-1, AC-2, AC-6, AC-7
- **Test Requirements**:
  - `human-judgement` TR-2.1: 打开选择器，点击端点头行折叠图标，模型行立即收起、图标变 ▶；再点击展开、图标变 ▼。
  - `programmatic` TR-2.2: 折叠 E1 → 关闭 overlay → 再次打开（同进程、不清空 collapsed map），E1 仍为折叠态（可通过检查 listPanel 子元素中 E1 下模型行数量为 0 验证）。
  - `human-judgement` TR-2.3: 折叠 E1 → 搜索 E1 下存在的 modelId 关键字 → E1 模型行自动显示、不被折叠隐藏。
  - `human-judgement` TR-2.4: 端点 E1 有 `setLastModelByEndpoint(E1, "very-long-model-id-abcdefg")` 记录，头行右侧显示截断后的「上次：very-long-model..」标签，无视觉溢出。
- **Notes**: 使用 `final Object[]` 装 Map（`Object[] holder = {new HashMap<String,Boolean>()}`）以便在 lambda 中可变引用；不引入外部字段。

## [x] Task 3: 按端点选择记忆写入 + 解析默认选中增强
- **Priority**: high
- **Depends On**: Task 1, Task 2
- **Description**:
  - 在 `AiModelPickerDialog.create()` 中「开始对话」按钮的 `onConfirm.accept(ep, model)` 之前，追加 `ClientConfig.setLastModelByEndpoint(ep.id(), model.id())` 一次写盘。
  - 新增内部辅助方法 `resolveEndpointPreferredModel(AiEndpoint ep)`：若 `getLastModelByEndpoint(ep.id())` 非空且对应模型 enabled，返回该 model；否则返回该端点第一个 enabled model；否则 null。
  - 修改选择逻辑：当用户在 UI 点击某端点下的模型（`modelRow` 的 `onSelect`）时，同步写入 `setLastModelByEndpoint(ep.id(), m.id())`（即立即更新记忆，即使未点确认也可被下次切回端点时利用）。
  - 保持原 `resolveDefaultSelection()` 的「全局默认端点/模型 → 第一个启用端点」不变，仅在「切换端点」场景（即已有选中端点后，用户通过点击切换到另一端点的模型）中使用记忆值。
  - 若用户选择的模型被禁用（理论上不应出现，因渲染时已过滤），`resolveEndpointPreferredModel` 回退到端点第一个启用模型。
- **Acceptance Criteria Addressed**: AC-3, AC-4
- **Test Requirements**:
  - `programmatic` TR-3.1: 选中 E1 的 M2 → 点击开始对话 → `getLastModelByEndpoint(E1)` 返回 M2；选中 E1 的 M1 → 点击开始对话 → 返回 M1。
  - `programmatic` TR-3.2: E1 上次记为 M1，但在 AiSettingsPanel 中把 M1.enabled=false 并保存；`resolveEndpointPreferredModel(E1)` 返回 E1 下第一个启用的模型（≠M1）。
  - `programmatic` TR-3.3: 对没有记忆的端点 E2，`resolveEndpointPreferredModel(E2)` 返回其第一个启用模型。
- **Notes**: 写入仅在实际交互触发（点击模型行/点开始对话）发生，避免初始化时脏写配置。

## [x] Task 4: 集成回归验证（端到端手动 + 自动配置校验）
- **Priority**: medium
- **Depends On**: Task 2, Task 3
- **Description**:
  - 验证 CitizenChatDialog →「切换模型」按钮 → 弹出选择器 → 折叠/展开/记忆流程完整无错；切换模型后聊天能继续发送（不要求实际联网，仅不抛异常、UI 刷新）。
  - 模拟配置损坏：手动把 `ENDPOINT_LAST_MODEL_MAP` 条目写坏为 `"a|b|c"` / `"|"` / 空字符串 → 启动并打开选择器，不崩溃且忽略坏条目。
  - 检查折叠图标与标签在多端点（≥3 个端点，每个 ≥3 模型）下对齐一致、无横向溢出（对话框宽度 560）。
- **Acceptance Criteria Addressed**: AC-1, AC-2, AC-6, AC-7, NFR-3, NFR-5
- **Test Requirements**:
  - `human-judgement` TR-4.1: 3 端点 × 5 模型场景，依次折叠 A→B→展开 C→搜索匹配 B 的模型 → B 强制展开并可见匹配，交互顺畅无白屏。
  - `programmatic` TR-4.2: 配置注入坏条目后 `listAiEndpoints()` 与 `getLastModelByEndpoint()` 均返回正常结果且不抛。
  - `human-judgement` TR-4.3: 「上次选中」标签 + 协议标签 + host 标签并存时头行不出现横向挤压溢出或换行重叠。
- **Notes**: 程序构建通过 `./gradlew build` 或项目既定构建命令确认编译无错；如项目已有 checkstyle/spotless 需一并通过。
