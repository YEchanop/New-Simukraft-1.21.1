![icon](https://cdn.modrinth.com/data/cached_images/42fb4c14522d728405429424c54214521d7740d8_0.webp)

# New:Sim-U-Kraft

New:Sim-U-Kraft is a city-building and NPC life simulation mod for Minecraft. It lets you
found a city, claim land, place buildings, hire citizens, and turn a simple settlement into
a working town with construction, farming, commerce, industry, and logistics.

The mod is available for **Minecraft 1.21.1 on NeoForge** and also supports
**Minecraft 1.20.1 on Forge**.

![Available for NeoForge](https://img.shields.io/badge/Available%20for-NeoForge-f16436?style=for-the-badge)
![Available for Forge](https://img.shields.io/badge/Available%20for-Forge-f16436?style=for-the-badge)

## Installation Guide

Download the file that matches your Minecraft version and mod loader. Use the **NeoForge**
build for Minecraft **1.21.1**, or the **Forge** build for Minecraft **1.20.1**.

Once the correct loader is installed, place New: Sim-U-Kraft into your `mods` folder. You
also need the matching version of **LDLib / LDLib2** for your Minecraft version.

## Gameplay

Start by placing a City Core to create your city. From there, you can claim chunks, manage
city funds, and begin growing a population of citizen NPCs.

Buildings are selected through the Build Box. Choose a building, preview it in the world,
confirm the placement, and let hired builders gather materials and construct it.

Citizens can take on different jobs such as builder, planner, farmer, commercial worker,
industrial worker, and logistics worker. Over time, your city can grow from a small base
into a living production network with homes, farms, shops, factories, warehouses, and item
routes.

## Extra Notes

New: Sim-U-Kraft is designed for both singleplayer and dedicated servers. City data and NPC
work are handled by the server, while the client displays HUDs, screens, previews, and
status updates.

The mod includes English and Simplified Chinese localization, configurable client and
server options, and optional city highlighting support for Xaero's World Map.

## License

New: Sim-U-Kraft is licensed under **GPL 3.0**.

## Features

### 市民重命名功能
- 玩家可通过市民管理界面对已有的市民进行重命名
- 名字长度限制为1-32个字符，不能为空
- 重命名后立即生效并持久化保存

### 城市管理命令增强

#### 退出城市命令
- 命令：`/simukraft city leave`
- 任何玩家均可使用，无需OP权限
- 玩家可主动退出当前所在城市，无需等待市长批准
- 若玩家是当前城市的市长，则不允许直接退出，需先转让市长职位或删除城市

#### OP删除城市命令
- 命令：`/simukraft city delete <城市名>`
- 仅限OP（权限等级2+）使用
- 可强制删除任意城市，无需市长在线
- 适用于市长长期不在线导致的废弃城市清理场景
- 删除后自动通知原城市所有在线成员并同步HUD
