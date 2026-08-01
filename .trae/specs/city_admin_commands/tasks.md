# 城市管理命令增强 - 实施计划

## [x] Task 1: 新增退出城市命令
- **Priority**: high
- **Depends On**: None
- **Description**: 
  - 在 SimuKraftCommand.java 中注册 `/simukraft city leave` 子命令
  - 实现退出城市逻辑：检查玩家是否有城市、是否是市长（市长需先转让）、执行 removeMember
  - 添加消息反馈（成功/失败/无城市/是市长）
  - 退出后同步 HUD
- **Acceptance Criteria Addressed**: AC-4, AC-5, AC-6
- **Test Requirements**:
  - `programmatic` TR-1.1: 非市长玩家执行 leave 命令后成功从城市成员中移除
  - `programmatic` TR-1.2: 市长执行 leave 命令被拒绝并收到提示
  - `programmatic` TR-1.3: 无城市玩家执行 leave 命令收到提示
  - `human-judgement` TR-1.4: 命令帮助信息清晰，与现有命令风格一致
- **Notes**: 复用 CityData.removeMember() 和 HudSyncService

## [x] Task 2: 新增OP删除城市命令
- **Priority**: high
- **Depends On**: None
- **Description**: 
  - 在 SimuKraftCommand.java 中注册 `/simukraft city delete <城市名>` 子命令（需OP权限）
  - 实现删除城市逻辑：检查城市是否存在、复用 CityService.deleteCity()
  - 与现有GUI删除保持一致：需要输入城市名二次确认
  - 删除后通知所有在线成员并同步 HUD
- **Acceptance Criteria Addressed**: AC-1, AC-2, AC-3, AC-7
- **Test Requirements**:
  - `programmatic` TR-2.1: OP执行 delete 命令后收到确认提示
  - `programmatic` TR-2.2: 确认后城市被成功删除，所有在线成员收到通知
  - `programmatic` TR-2.3: 非OP玩家执行 delete 命令被拒绝
  - `programmatic` TR-2.4: 删除不存在的城市收到错误提示
  - `human-judgement` TR-2.5: 确认提示清晰明确，防止误删
- **Notes**: 复用现有 transferMayorByName 的城市名查找模式，参考 CityCoreManageCityPacket.deleteCity 的确认逻辑

## [x] Task 3: 添加语言文件翻译
- **Priority**: medium
- **Depends On**: Task 1, Task 2
- **Description**: 
  - 在 zh_cn.json 和 en_us.json 中添加所有新增命令的消息翻译键
  - 覆盖所有成功/失败/提示消息
- **Acceptance Criteria Addressed**: AC-1 ~ AC-7
- **Test Requirements**:
  - `programmatic` TR-3.1: 所有新增翻译键在两种语言文件中都存在
  - `human-judgement` TR-3.2: 中文翻译准确自然，与现有风格一致
- **Notes**: 命名风格参考现有 command.city_mayor.* 和 command.city_funds.*

## [x] Task 4: 构建验证
- **Priority**: medium
- **Depends On**: Task 1, Task 2, Task 3
- **Description**: 
  - 执行构建确保无编译错误
  - 运行时验证命令注册和基本逻辑
- **Acceptance Criteria Addressed**: AC-1 ~ AC-7
- **Test Requirements**:
  - `programmatic` TR-4.1: `gradlew jar` 构建成功无错误
  - `programmatic` TR-4.2: 命令在游戏中可被 Tab 补全
