# 城市管理命令增强 - 产品需求文档

## Overview
- **Summary**: 为NSUK模组新增两个城市管理功能：OP可强制删除任意城市（解决市长失踪导致废弃城市占用地块问题），以及玩家可主动退出所在城市（无需等待市长在线操作）。
- **Purpose**: 解决多人游戏场景下的两个痛点：1）市长长期不在线导致废弃城市无法清理，占用区块资源；2）玩家加入城市后想退出但必须等待市长操作，市长离线时无法脱离。
- **Target Users**: 服务器管理员（OP）、普通玩家

## Goals
- OP能够通过命令强制删除任意城市，无需市长在线
- 玩家能够通过命令主动退出当前所在城市，无需市长批准
- 所有操作有明确的消息反馈和操作记录
- 操作安全性：删除城市需二次确认，退出城市防止误操作

## Non-Goals (Out of Scope)
- 不修改现有GUI界面的城市管理功能
- 不新增城市创建/加入的流程
- 不修改权限等级系统
- 不涉及城市区块的自动回收机制
- 不提供批量删除/批量退出功能

## Background & Context
现有系统中：
- 删除城市只能通过城市核心GUI操作，且仅限市长本人
- 退出城市无任何命令支持，玩家完全依赖市长在线操作
- 已有市长转让命令（`/simukraft city mayor transfer` 和 `transfer-by-name`），支持OP通过城市名转让离线市长
- 已有 `CityService.deleteCity()`、`CityData.removeMember()` 等底层API可复用

## Functional Requirements
- **FR-1**: OP（权限等级2+）可通过 `/simukraft city delete <城市名>` 命令删除指定城市
- **FR-2**: 删除城市命令需要输入确认信息（输入城市名二次确认），防止误删
- **FR-3**: 玩家可通过 `/simukraft city leave` 命令退出当前所在城市
- **FR-4**: 退出城市命令需要确认（输入"确认"或类似关键字），防止误操作
- **FR-5**: 所有操作完成后向操作者发送明确的成功/失败消息
- **FR-6**: 删除城市后通知城市内所有在线成员，刷新其HUD
- **FR-7**: 退出城市后更新城市成员列表，刷新相关HUD

## Non-Functional Requirements
- **NFR-1**: 命令权限符合Minecraft标准（OP命令用 `hasPermission(2)` 保护）
- **NFR-2**: 退出城市时，若玩家是市长，不允许直接退出（需先转让市长或删除城市），防止城市无主
- **NFR-3**: 操作原子性：删除城市要么完全成功要么完全失败，不留下中间状态
- **NFR-4**: 与现有命令风格保持一致（消息格式、错误提示等）

## Constraints
- **Technical**: Minecraft 1.21.1 NeoForge，Java 21，使用 Brigadier 命令系统
- **Business**: 保持向后兼容，不破坏现有命令
- **Dependencies**: 复用现有 `CityService`、`CityManager`、`CityData`、`HudSyncService` 等服务类

## Assumptions
- 城市删除后，城市内的建筑/控制盒归属由系统自动处理（现有deleteCity逻辑已覆盖）
- 玩家退出城市后，其在城市内的个人物品不受影响
- 市长退出城市前必须先转让或删除，这是合理的安全约束

## Acceptance Criteria

### AC-1: OP删除城市命令
- **Given**: 操作者拥有OP权限（等级2+）且目标城市存在
- **When**: 执行 `/simukraft city delete <城市名>`
- **Then**: 系统提示需要二次确认，要求输入完整城市名确认删除
- **Verification**: `programmatic`

### AC-2: OP删除城市确认
- **Given**: 已执行删除命令且系统等待确认
- **When**: 输入正确的城市名进行确认
- **Then**: 城市被删除，所有在线成员收到通知，HUD刷新
- **Verification**: `programmatic`

### AC-3: OP删除城市权限不足
- **Given**: 操作者没有OP权限
- **When**: 执行 `/simukraft city delete <城市名>`
- **Then**: 系统拒绝执行并提示权限不足
- **Verification**: `programmatic`

### AC-4: 玩家退出城市
- **Given**: 玩家属于某个城市且不是市长
- **When**: 执行 `/simukraft city leave` 并确认
- **Then**: 玩家从该城市成员列表中移除，HUD刷新
- **Verification**: `programmatic`

### AC-5: 市长退出城市被阻止
- **Given**: 玩家是当前城市的市长
- **When**: 执行 `/simukraft city leave`
- **Then**: 系统拒绝并提示需先转让市长或删除城市
- **Verification**: `programmatic`

### AC-6: 无城市玩家退出
- **Given**: 玩家不属于任何城市
- **When**: 执行 `/simukraft city leave`
- **Then**: 系统提示玩家当前没有加入任何城市
- **Verification**: `programmatic`

### AC-7: 删除不存在的城市
- **Given**: 目标城市名不存在
- **When**: OP执行 `/simukraft city delete <不存在的城市名>`
- **Then**: 系统提示城市不存在
- **Verification**: `programmatic`

## Open Questions
- [ ] 退出城市确认词使用什么？"确认"还是"yes"或"leave"？
- [ ] 删除城市确认是否需要和现有GUI一致（输入完整城市名）？
