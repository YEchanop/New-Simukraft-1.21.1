# AI 模型选择器折叠与记忆功能 - Product Requirement Document

## Overview
- **Summary**: 为现有的 AI 模型选择对话框（AiModelPickerDialog）新增两个增强功能：1）每个端点（网站/服务）区块支持折叠/展开，减少长列表滚动负担；2）按端点维度记忆用户上次选择的模型，下次打开选择器或切换回该端点时自动恢复该模型，无需重复选择。
- **Purpose**: 改善多端点、多模型场景下的模型选择效率，降低滚动和重复点击成本，提升用户使用 AI 聊天功能的体验流畅度。
- **Target Users**: Simukraft 模组中使用「市民 AI 聊天」功能的玩家用户，特别是配置了多个 AI 端点（如 OpenRouter、本地模型、商用 API）且每个端点下有多个可用模型的高级用户。

## Goals
- 支持点击端点头部切换该端点下模型列表的折叠/展开状态，视觉明确可区分。
- 折叠状态与当前 Minecraft 会话持久绑定（同会话内再次打开对话框保留折叠状态）。
- 对每个端点单独记录并持久化「上次选中模型 ID」，切换端点或重开对话框自动恢复。
- 保持原有搜索、默认模型、全局默认端点的逻辑兼容，不破坏既有选点流程。
- UI 交互响应即时、无明显卡顿。

## Non-Goals (Out of Scope)
- 不引入新的 AI 协议或端点类型。
- 不修改 CitizenAiChatService 的网络调用和模型推理逻辑。
- 不在本需求中实现跨游戏实例的云端同步（仅使用本地 ClientConfig）。
- 不改造 AiSettingsPanel（设置面板）的模型编辑器 UI。
- 不实现模型使用统计报表或切换历史回溯界面（仅存储、不展示历史）。

## Background & Context
- 代码库已实现市民 AI 聊天系统，入口为 [CitizenChatDialog.java](file:///c:/Users/n/Desktop/2026420/New-Simukraft-1.21.1/src/main/java/client/cn/kafei/simukraft/client/citizen/ai/CitizenChatDialog.java)。
- 模型选择器位于 [AiModelPickerDialog.java](file:///c:/Users/n/Desktop/2026420/New-Simukraft-1.21.1/src/main/java/client/cn/kafei/simukraft/client/citizen/ai/AiModelPickerDialog.java)，当前按「端点区块 + 模型行」平铺展示，无折叠能力。
- 配置项集中在 [ClientConfig.java](file:///c:/Users/n/Desktop/2026420/New-Simukraft-1.21.1/src/main/java/common/cn/kafei/simukraft/config/ClientConfig.java)，已存在 `CITIZEN_AI_DEFAULT_ENDPOINT_ID` 与 `CITIZEN_AI_DEFAULT_MODEL_ID` 两个全局默认值，但缺乏按端点维度的上次选择记忆。
- 端点区块的渲染入口为 `endpointSection()` 方法（[AiModelPickerDialog.java#L206-L257](file:///c:/Users/n/Desktop/2026420/New-Simukraft-1.21.1/src/main/java/client/cn/kafei/simukraft/client/citizen/ai/AiModelPickerDialog.java#L206-L257)），默认选中解析在 `resolveDefaultSelection()`（[AiModelPickerDialog.java#L308-L330](file:///c:/Users/n/Desktop/2026420/New-Simukraft-1.21.1/src/main/java/client/cn/kafei/simukraft/client/citizen/ai/AiModelPickerDialog.java#L308-L330)）。

## Functional Requirements
- **FR-1**: 每个端点区块头行新增一个折叠/展开指示图标（▶/▼或同等视觉），点击头行除模型选择区域外的区域（或直接点击图标）切换显示状态。折叠状态下该端点仅显示头行（别名、主机名、协议标签），不展示模型行。
- **FR-2**: 折叠状态在同一次 Minecraft 运行会话内的多次打开/关闭模型选择器之间保持（内存级记忆）；默认所有端点初始为展开。
- **FR-3**: 在 ClientConfig 新增配置项 `CITIZEN_AI_ENDPOINT_LAST_MODEL_MAP`（字符串列表，每项格式 `endpointId|lastModelId`），用于按端点维度持久化「上次选中的模型 ID」。
- **FR-4**: 当用户点击确认（「开始对话」）切换模型时，除原有的全局默认写入外，同步将当前（端点, 模型）对写入 FR-3 的映射表。
- **FR-5**: 打开模型选择器解析默认选中项时，优先沿用全局默认端点与模型；若用户在选择器内切换端点（即选中某端点下另一模型，随后因搜索过滤或临时展开另一端点又点回），目标端点若存在 FR-3 的记录，自动选中该端点记录的上次模型（若模型已被禁用则回退该端点的第一个启用模型）。
- **FR-6**: 端点头行右侧展示「上次选中：xxx」微标签（可选，长度限制 18 字符内），当该端点存在有效记忆模型时显示。
- **FR-7**: 若某端点被删除，自动从 FR-3 的映射表中清除对应条目，避免配置膨胀。

## Non-Functional Requirements
- **NFR-1**: 折叠/展开点击响应时间 ≤ 100ms（不做磁盘 IO，纯 UI 重建）。
- **NFR-2**: 配置读写使用 ClientConfig 既有的 `SPEC.save()` 机制，避免频繁写盘；用户确认切换模型时写一次即可。
- **NFR-3**: 向后兼容：未配置 FR-3 新项的老用户升级后行为等同当前（无记忆即回退默认），配置格式错误条目被安全跳过。
- **NFR-4**: 搜索过滤（关键字匹配）下仍正确渲染折叠状态：若端点被搜索匹配到其下模型，强制展开该端点以显示匹配结果；折叠图标随之同步。
- **NFR-5**: 所有新增 UI 元素遵循既有配色（DIALOG_ACCENT / DIALOG_PAPER / DIALOG_SUBTEXT），尺寸与 `AiModelPickerDialog` 现有布局对齐，不出现横向滚动条溢出。

## Constraints
- **Technical**: NeoForge 1.21.1 + Client 端 UI，使用 LowDragLib2（UIElement/Button/Label/ScrollerView）与 Taffy 布局；不得引入额外第三方 UI 库。
- **Business**: 仅客户端（Dist.CLIENT）生效；不触碰服务端网络包协议。
- **Dependencies**: 仅依赖既有的 `ClientConfig.java` 配置体系与 `AiModelPickerDialog.java`，不得新增跨模块硬依赖。

## Assumptions
- 「端点 ID」在用户会话期内稳定唯一（由 UUID 生成，设置面板保存后不变）。
- 用户希望按端点记忆，而非按市民/聊天上下文记忆；跨端点切换时端点内上次模型即可。
- 折叠态仅需会话级（内存），无需跨游戏重启持久化（若用户后续需要可再扩展）。

## Acceptance Criteria

### AC-1: 端点区块可折叠/展开
- **Given**: 模型选择器已打开，至少有一个端点下存在模型。
- **When**: 用户点击该端点头行上的折叠/展开图标或头行空白区域。
- **Then**: 该端点下所有模型行立即隐藏（折叠）或重新显示（展开）；折叠图标随之切换（▶/▼）。
- **Verification**: `human-judgment`
- **Notes**: 折叠状态下不影响模型单选选择的一致性。

### AC-2: 折叠态在会话内保持
- **Given**: 用户已折叠某端点并关闭选择器。
- **When**: 通过「切换模型」按钮或重新打开市民聊天再次弹出选择器。
- **Then**: 该端点仍保持上次的折叠/展开状态。
- **Verification**: `programmatic`
- **Notes**: 重启游戏后可重置为全部展开。

### AC-3: 按端点持久化上次选中模型
- **Given**: 选择器中有端点 E1（模型 M1、M2）与 E2（模型 M3），用户先在 E1 选 M2 并点开始对话。
- **When**: 用户再次打开模型选择器。
- **Then**: 选择器默认选中仍为全局默认端点；若用户随后在选择器中展开/切换到 E1（例如点击 E1 下任一位置或其模型出现），E1 的上次选中标记为 M2 并自动高亮为选中态（若 M2 仍启用）。
- **Verification**: `programmatic`

### AC-4: 记忆的模型被禁用时安全回退
- **Given**: 端点 E1 的记忆模型是 M1，但用户在设置面板中禁用了 M1。
- **When**: 打开选择器并处理 E1 的默认选中。
- **Then**: 系统跳过 M1，自动选中 E1 下第一个启用的模型（若无则不选）。
- **Verification**: `programmatic`

### AC-5: 删除端点时同步清理记忆映射
- **Given**: 已存在 E1|m1 的记忆条目，用户在 AI 设置面板中删除端点 E1。
- **When**: 执行 `removeAiEndpoint(E1.id)` 并读取配置。
- **Then**: `CITIZEN_AI_ENDPOINT_LAST_MODEL_MAP` 列表中不再包含 E1 的条目。
- **Verification**: `programmatic`

### AC-6: 搜索过滤下强制展开匹配端点
- **Given**: 端点 E1 处于折叠态，其下模型 M1 的 ID 含关键字「granite」。
- **When**: 用户在搜索框输入 `granite`。
- **Then**: E1 区块自动展开并可见匹配的模型行；搜索清空后仍为展开（或恢复折叠，由实现决定，但必须保证匹配结果可见）。
- **Verification**: `human-judgment`

### AC-7: 「上次选中」标签显示正确
- **Given**: E1 已有记忆模型 M2（ID=ibm-granite/granite-4.2-8b）。
- **When**: 渲染 E1 头行。
- **Then**: 头行右侧显示一个小型文本标签，内容为「上次：ibm-granite/gra..」或等效截断格式，颜色使用 DIALOG_SUBTEXT。
- **Verification**: `human-judgment`

## Open Questions
- [ ] 折叠状态是否需要跨游戏重启持久化（目前假设仅会话级）？
- [ ] 「按端点记忆切换」是否强于「全局默认端点」（即打开选择器时直接定位到上次使用的端点 + 模型，而非使用全局默认）？当前假设保持全局默认优先，仅端点切换内部使用记忆。
