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

### 城市管理界面增强

#### 退出城市按钮
- 在城市管理界面新增「退出城市」按钮
- 仅对已加入他人城市、没有城市管理权限的普通成员显示
- 点击后可直接退出当前城市，无需输入命令或等待市长批准
- 城市市长和拥有城市管理权限的成员不会显示该按钮

#### 城市核心 OP 管理界面
- 在城市核心 GUI 中新增仅权限等级2及以上 OP 可见的「OP城市管理」入口
- 显示服务器当前全部城市、城市名称、市长、成员数量、维度和城市核心坐标
- 每个城市提供删除按钮，第一次点击进入确认状态，第二次点击才会执行删除
- 删除操作按城市 UUID 处理，并在服务端再次校验 OP 权限和确认状态
- 删除成功后自动刷新城市列表，并同步在线成员通知、HUD 和城市区块数据
