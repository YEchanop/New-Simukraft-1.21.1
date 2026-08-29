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

### 市民自定义皮肤功能
- 玩家可将自定义皮肤图片（PNG/JPG）放入游戏目录的 `simukraftskins` 文件夹，为市民更换专属外观
- 服务端持有皮肤文件，玩家登录或点击「刷新」时自动下发到客户端加载，多人游戏同样生效
- 在市民信息界面的「身份证」卡片底部新增皮肤设置区，可查看当前皮肤、进入皮肤选择列表或刷新皮肤
- 皮肤选择列表带缩略图预览，点击即可应用；支持「恢复默认」和「返回」操作
- 皮肤选择列表支持删除本地皮肤：删除 `simukraftskins` 文件夹中的皮肤文件并即时刷新列表；若删除的是当前正在使用的皮肤会自动回退默认，服务端下发（无本地文件）的皮肤不可删除
- 更换皮肤立即生效并持久化保存，重新进入游戏依然保留
- 文件名以 `_f` 结尾的皮肤自动使用纤细模型
- 单个皮肤文件建议使用 64×64 PNG，大小最好不超过 30KB（超出大小限制的文件会被跳过并记录日志）

#### 皮肤资源下载中心
- 在市民信息界面的皮肤选择界面新增「下载中心」，从皮肤目录 API 拉取全部皮肤，以缩略图列表展示供玩家自由下载
- 每个皮肤提供「下载」按钮，下载后自动存入 `simukraftskins` 文件夹并出现在皮肤选择列表，行内标记「已下载」
- 默认接入 LittleSkin 皮肤库（littleskin.cn/skinlib），自动适配其数据接口；alex 皮肤自动使用纤细模型
- 支持关键词搜索、按点赞/最新排序切换、上一页/下一页翻页（每页 20 条）
- 支持按皮肤类型筛选：全部 / 男（steve）/ 女（alex），LittleSkin 接口原生按类型过滤，女（alex）皮肤自动使用纤细模型
- 「自定义API」可管理多个皮肤目录 API 地址：添加（名称 + http/https 地址）、一键切换使用、删除（删除使用中的项自动回退）、恢复默认
- 通用第三方 API 需返回 `[{name,url}]` 格式的 JSON 数组，LittleSkin 地址自动识别适配
- 下载中心配置（当前地址与已保存列表）持久化在 `config/simukraft-client.toml` 的 `citizenSkin` 小节
