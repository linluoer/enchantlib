# EnchantLib 管理员手册 | Admin Guide

> 面向服务端管理员,介绍 EnchantLib 的安装、配置、指令与运维。
> EnchantLib 是 Minecraft 26.2(Fabric)服务端附魔库模组。

## 目录 | Table of Contents

1. [安装 | Installation](#1-安装--installation)
2. [配置文件 | Configuration](#2-配置文件--configuration)
   - 2.1 [主配置 acquisition.toml](#21-主配置-acquisitiontoml)
   - 2.2 [触发策略配置 trigger.toml](#22-触发策略配置-triggertoml)
   - 2.3 [附魔定义目录 enchantments/](#23-附魔定义目录-enchantments)
3. [指令 | Commands](#3-指令--commands)
4. [权限节点 | Permissions](#4-权限节点--permissions)
5. [配置文件定义附魔 | Config-file Enchantments](#5-配置文件定义附魔--config-file-enchantments)
6. [资源包分发 | Resource Pack Distribution](#6-资源包分发--resource-pack-distribution)
7. [调试与排障 | Debugging & Troubleshooting](#7-调试与排障--debugging--troubleshooting)
8. [禁用附魔后的存档行为 | Save Data Behavior](#8-禁用附魔后的存档行为--save-data-behavior)

---

## 1. 安装 | Installation

### 1.1 环境要求

- Minecraft 26.2
- Fabric Loader 0.19.3 或更新版本
- Fabric API 0.155.2+26.2 或更新版本
- Java 25+

### 1.2 安装步骤

1. 将 `enchantlib-1.0.1.jar` 放入服务端 `mods/` 目录
2. 启动服务端,EnchantLib 会自动创建 `config/enchantlib/` 配置目录
3. (可选)安装依赖 EnchantLib 的其他模组

EnchantLib 是**纯服务端模组**,客户端无需安装即可运行。但自定义附魔的本地化名称需要客户端接受资源包支持(见第 6 节)。

---

## 2. 配置文件 | Configuration

EnchantLib 的配置目录位于 `config/enchantlib/`,包含以下文件:

```
config/enchantlib/
├── acquisition.toml          # 主配置(全局开关、HTTP、调试、tick 间隔)
├── trigger.toml              # 触发策略配置(可选,不存在时使用默认 IGNORE)
└── enchantments/             # 配置文件定义附魔目录
    ├── my_enchant.toml
    └── another_enchant.toml
```

### 2.1 主配置 acquisition.toml

**路径**:`config/enchantlib/acquisition.toml`

```toml
# 战利品注入总开关
# 关闭后所有战利品注入规则都不会被注册
loot_injection_enabled = true

# 村民交易总开关
# 关闭后所有村民交易都不会被注入
villager_trade_enabled = true

# 资源分发总开关
# 关闭后跳过目录扫描、语言合并、资源包构建、HTTP 服务器和推送
resource_distribution_enabled = true

# HTTP 服务器端口(范围 1-65535)
http_server_port = 8765

# HTTP 服务器对外完整网址(可含端口)
# 留空则自动探测本机 IP(仅局域网可用,公网玩家无法访问)
# 公网部署必须配置为对外域名或公网 IP;走 80/反代不带端口,直连非 80 端口写成 "play.example.com:8080"
# 注意:http_server_port 只是本地监听端口(服务端绑定用),不进对外 URL;对外端口由此处 host 决定
# 示例:http_server_host = "play.example.com" 或 "203.0.113.5" 或 "play.example.com:8080"
http_server_host = ""

# 调试日志开关
# 开启后,EnchantLib 在关键路径输出详细调试日志
debug_enabled = false

# ENTITY_TICK 触发间隔(单位:tick,>=1)
# 1 = 每 tick 触发(高精度高 CPU)
# 20 = 每秒触发(推荐)
# 40+ = 每 2 秒+(低开销)
# 使用实体 ID 相位偏移避免全服同一 tick 集中触发
entity_tick_interval = 20
```

#### 字段说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `loot_injection_enabled` | bool | `true` | 战利品注入总开关,关闭后所有战利品注入规则不注册 |
| `villager_trade_enabled` | bool | `true` | 村民交易总开关,关闭后所有村民交易不注入 |
| `resource_distribution_enabled` | bool | `true` | 资源分发总开关,关闭后跳过扫描/合并/构建/HTTP/推送 |
| `http_server_port` | int | `8765` | HTTP 服务器端口,范围 1-65535 |
| `http_server_host` | string | `""` | 对外完整网址,可含端口;留空自动探测本机 IP(仅局域网可用,公网必须配置)。对外端口由此处 host 决定,不进 http_server_port |
| `debug_enabled` | bool | `false` | 调试日志开关 |
| `entity_tick_interval` | int | `20` | ENTITY_TICK 触发间隔(>=1)。1=每 tick(高精度高 CPU),20=每秒(推荐)。使用实体 ID 相位偏移避免全服集中触发 |

#### 热重载

```bash
/enchantlib reload
```

`reload` **仅影响全局开关**(上述 7 项)。以下变更需**重启服务端**:

- 附魔定义(`enchantments/*.toml`)
- 互斥组
- 村民交易注入规则
- 战利品注入规则
- `http_server_host`(HTTP 服务器不会通过 reload 重启)
- `trigger.toml`(触发策略覆盖,见 2.2)
- 事件回调注册(由实现模组在启动时一次性收集,无法热重载)

### 2.2 触发策略配置 trigger.toml

**路径**:`config/enchantlib/trigger.toml`(可选)

此配置用于控制充能事件(`POST_ATTACK`、`PROJECTILE_HIT`)的触发策略。**仅对充能事件生效**,其他事件(如 `ENTITY_TICK`、`POST_HURT`、`FOOD_REGEN` 等)忽略此策略。

#### 配置示例

```toml
# 全局默认阈值(THRESHOLD / THRESHOLD_SCALED 模式未配置附魔时使用)
force_threshold_min = 0.0

# 附魔级覆盖(键为附魔 ID,含冒号必须用引号包裹)
["mymod:my_enchant"]
mode = "THRESHOLD"      # IGNORE / THRESHOLD / SCALED / THRESHOLD_SCALED
threshold = 0.7         # 0.0~1.0

["mymod:leech"]
mode = "THRESHOLD_SCALED"
threshold = 0.7
```

#### mode 取值

| mode | 行为 | 说明 |
|------|------|------|
| `IGNORE` | 忽略充能,任何充能都全额触发 | 默认值 |
| `THRESHOLD` | 充能阈值门控,低于阈值不触发 | 适合"必须满充能才生效"的附魔 |
| `SCALED` | 按充能比例缩放效果等级 | 充能越高效果越强 |
| `THRESHOLD_SCALED` | 阈值门控 + 缩放 | 同时满足阈值和缩放 |

#### 字段说明

- `force_threshold_min`(float,默认 `0.0`):全局默认阈值,仅在 `THRESHOLD`/`THRESHOLD_SCALED` 模式下且附魔未单独配置时使用
- 附魔级覆盖:`["<enchantment_id>"]` 表名(含冒号必须用引号包裹)
  - `mode`:触发模式(见上表)
  - `threshold`:该附魔的触发阈值,范围 0.0~1.0

#### 注意事项

- `trigger.toml` 不是必须的。文件不存在时使用默认 `IGNORE` 策略(任何充能都触发)
- 仅当需要门控 `POST_ATTACK`/`PROJECTILE_HIT` 事件触发时配置
- 修改 `trigger.toml` 后需重启服务端(reload 不重载触发策略)

### 2.3 附魔定义目录 enchantments/

每个 TOML 文件定义一个附魔,详见 [第 5 节](#5-配置文件定义附魔--config-file-enchantments)。

---

## 3. 指令 | Commands

所有指令以 `/enchantlib` 为前缀,共 6 个子指令。

### 3.1 查询指令

#### `/enchantlib list`

列出所有已注册的自定义附魔。显示 ID、最大等级、权重、描述、互斥组。

**输出示例**:
```
[EnchantLib] 共 3 个自定义附魔:
- enchantlib-testmod:demo (lv3, w5) Testmod Demo Enchantment | 互斥: #minecraft:exclusive_set/damage
- enchantlib-testmod:fire_aspect (lv2, w5) Testmod Fire Aspect
- enchantlib-testmod:ice_aspect (lv2, w5) Testmod Ice Aspect
```

**权限**:`enchantlib.command.list`(默认所有玩家可用)

#### `/enchantlib groups`

列出所有已注册的自定义互斥组。显示标签 ID、成员列表。

**输出示例**:
```
[EnchantLib] 共 1 个自定义互斥组:
- #enchantlib-testmod:exclusive_set/elemental (2 个) enchantlib-testmod:fire_aspect, enchantlib-testmod:ice_aspect
```

**权限**:`enchantlib.command.groups`(默认所有玩家可用)

### 3.2 管理指令

#### `/enchantlib give <target> <enchantment_id> [level]`

给予玩家附魔。

**行为说明**:
- **手持物品时**:直接对该物品附魔(若该物品支持该附魔)
- **空手时**:给予一本带有该附魔的附魔书

**参数**:
- `target`:目标玩家(支持 `@a` / `@p` / 玩家名)
- `enchantment_id`:附魔 ID(如 `mymod:leech`)
- `level`:附魔等级(1~255,默认 1;超过附魔 maxLevel 会警告但仍给予)

**示例**:
```bash
# 给予最近的玩家 3 级附魔(空手时给附魔书)
/enchantlib give @p enchantlib-testmod:demo 3

# 给予 Steve 2 级附魔(空手时给附魔书)
/enchantlib give Steve enchantlib-testmod:fire_aspect 2

# 手持武器时,直接对该武器附魔
/enchantlib give @p mymod:leech 4
```

**权限**:`enchantlib.command.give`(默认 OP 2)

#### `/enchantlib dump <enchantment_id> [file]`

导出附魔 JSON 定义到 `config/enchantlib/dump/<file>.json`。

**参数**:
- `enchantment_id`:附魔 ID
- `file`:输出文件名(可选,默认 `<enchantment_id>.json`)

**示例**:
```bash
/enchantlib dump enchantlib-testmod:demo
/enchantlib dump enchantlib-testmod:demo my_demo
```

**限制**:仅支持 EnchantLib 注册的附魔,不支持原版附魔。

**权限**:`enchantlib.command.dump`(默认 OP 2)

#### `/enchantlib reload`

重新加载 `acquisition.toml` 的全局开关。显示新旧值对比,并提示以下内容需重启服务端:

- 附魔定义(`enchantments/*.toml`)
- 互斥组
- 村民交易注入
- 战利品注入
- `http_server_host`(HTTP 服务器)
- `trigger.toml`(触发策略覆盖)
- 事件回调注册

**权限**:`enchantlib.command.reload`(默认 OP 2)

### 3.3 调试指令

所有调试指令权限节点为 `enchantlib.command.debug`(默认 OP 2)。

#### `/enchantlib debug status`

显示调试状态和系统统计:

- 调试模式状态
- 已注册附魔数量
- 已注册互斥组数量
- 事件回调覆盖附魔数量
- `loot_injection` / `villager_trade` / `resource_distribution` 状态
- `http_server_host`
- 配置文件 `debug_enabled`

**输出示例**:
```
[EnchantLib] 调试模式: 已关闭
- 已注册附魔: 5 个
- 已注册互斥组: 1 个
- 事件回调覆盖附魔: 5 个
- loot_injection: 开启
- villager_trade: 开启
- resource_distribution: 开启
- http_server_host: play.example.com
- 配置文件 debug_enabled: false
```

> 注:`debug status` 仅统计通过 `EnchantmentEventRegistrar` 注册的回调数(覆盖的附魔数)。
> 全局事件 `EnchantLibEvents.LIVING_ENTITY_TICK` / `FOOD_REGEN` 由实现模组自行注册,
> 不计入此统计。可在启动日志中查询其启用状态(关键字 `LIVING_ENTITY_TICK`/`FOOD_REGEN`)。

#### `/enchantlib debug toggle`

切换运行时调试开关(不持久化,重启后回到配置值)。

#### `/enchantlib debug info <enchantment_id>`

显示附魔详情:描述键、回退、最大等级、权重、铁砧成本、支持物品、主要物品、互斥组、槽位、战利品注入、村民交易。

**输出示例**:
```
[EnchantLib] 附魔详情: enchantlib-testmod:demo
- 描述键: enchantment.enchantlib-testmod.demo
- 描述回退: Testmod Demo Enchantment
- 最大等级: 3
- 权重: 5
- 铁砧成本: 2
- 支持物品: #minecraft:enchantable/sharp_weapon
- 主要物品: #minecraft:enchantable/melee_weapon
- 互斥组: #minecraft:exclusive_set/damage
- 槽位: mainhand
- 战利品注入: false
- 村民交易: false
```

---

## 4. 权限节点 | Permissions

EnchantLib 基于 `fabric-permission-api-v1`,支持权限管理插件(如 LuckPerms)覆盖默认行为。

### 4.1 权限节点清单

| 权限节点 | 默认行为 | 说明 |
|---------|---------|------|
| `enchantlib.command.list` | 所有玩家可用 | 列出附魔 |
| `enchantlib.command.groups` | 所有玩家可用 | 列出互斥组 |
| `enchantlib.command.give` | OP 等级 2 | 给予附魔(物品或附魔书) |
| `enchantlib.command.dump` | OP 等级 2 | 导出附魔 JSON |
| `enchantlib.command.reload` | OP 等级 2 | 重载配置 |
| `enchantlib.command.debug` | OP 等级 2 | 调试指令组 |

### 4.2 LuckPerms 配置示例

```
# 允许 VIP 玩家使用 give 指令
/lp group vip permission set enchantlib.command.give true

# 禁止某玩家使用 reload
/lp user Steve permission set enchantlib.command.reload false
```

---

## 5. 配置文件定义附魔 | Config-file Enchantments

管理员可通过 TOML 配置文件定义附魔,无需修改代码或安装额外模组。

### 5.1 字段总览

每个 TOML 文件定义一个附魔。

**必填字段**:
- `id`:附魔 ID(如 `mymod:my_enchant`)
- `description`:描述文本(回退名称)
- `supported_items`:支持物品标签或物品 ID
- `weight`:权重(>=1)
- `max_level`:最大等级(>=1)
- `anvil_cost`:铁砧成本
- `[min_cost]`:`base` / `per_level_above_first`
- `[max_cost]`:`base` / `per_level_above_first`
- `slots`:槽位数组(如 `["mainhand"]`)

**可选字段**:
- `primary_items`:主要物品标签
- `exclusive_set`:互斥组标签
- `[condition]`:`mod_loaded="modid"` 或 `mod_not_loaded="modid"`(条件加载)
- `[acquisition]`:`loot=true/false`、`trade=true/false`(仅配置附魔生效,自动注册获取途径)
- `[[effects.<effect_key>]]`:`base` / `per_level_above_first`(`effect_key` 用下划线替换冒号)

### 5.2 支持的 effect 类型

| 配置键 | 对应效果 | 说明 |
|--------|---------|------|
| `minecraft_damage` | `minecraft:damage` | 线性伤害加成 |
| `minecraft_knockback` | `minecraft:knockback` | 击退 |
| `minecraft_damage_protection` | `minecraft:damage_protection` | 伤害保护 |
| `minecraft_post_attack_ignite` | `minecraft:post_attack` + `minecraft:ignite` | 攻击后点燃 |

### 5.3 完整配置示例

```toml
# ===== 必填字段 =====
id = "mymod:my_enchant"
description = "My Enchantment"
supported_items = "#minecraft:enchantable/sharp_weapon"
weight = 10
max_level = 5
anvil_cost = 4

[min_cost]
base = 1
per_level_above_first = 11

[max_cost]
base = 21
per_level_above_first = 11

slots = ["mainhand"]

# ===== 可选字段 =====
primary_items = "#minecraft:enchantable/melee_weapon"
exclusive_set = "#minecraft:exclusive_set/damage"

# ===== 条件加载(可选)=====
[condition]
mod_loaded = "sophisticatedbackpacks"    # 仅当该 mod 加载时
# mod_not_loaded = "jei"                  # 仅当该 mod 未加载时

# ===== 获取途径开关(可选,仅配置附魔生效)=====
[acquisition]
loot = true    # 自动注册战利品注入(地下城、矿道箱子)
trade = true   # 自动加入 #minecraft:tradeable 标签

# ===== 效果(可选)=====
# effect_key 用下划线替换冒号
[[effects.minecraft_damage]]
base = 1.0
per_level_above_first = 0.5
```

### 5.4 注意事项

- 配置附魔与代码注册附魔**共享注册空间**,ID 不可重复
- 修改配置需**重启服务端**(reload 不重载附魔定义)
- `ConfigLoader` 会自动 clamp 越界值(`weight < 1`、`max_level < 1` 等),并记录 `WARN` 日志

---

## 6. 资源包分发 | Resource Pack Distribution

EnchantLib 自带客户端资源包分发系统,用于推送附魔的本地化名称、纹理、粒子、音效等客户端资源。

### 6.1 工作流程

1. 服务端启动时扫描所有模组的 `assets/<modid>/enchant_sync/` 目录
2. 语言文件跨模组合并到 `assets/minecraft/lang/`;其他资源(纹理/粒子/声音等)保留模组命名空间
3. 构建运行时资源包(ZIP),包含 `pack.mcmeta` + 语言文件 + 其他资源
4. 启动内置 HTTP 服务器(默认端口 8765)
5. 玩家加入服务端时通过 `ClientboundResourcePackPushPacket` 推送资源包

### 6.2 玩家体验

- 玩家加入时收到资源包推送提示
- 接受后,自定义附魔显示正确的本地化名称
- 拒绝或未接受时,显示附魔的 fallback 文本(兜底名称)

### 6.3 关键配置 http_server_host(公网部署必读)

HTTP 服务器监听 `0.0.0.0:http_server_port`(所有网卡的**本地端口**),但**推送给客户端的下载 URL**由 `http_server_host` 决定。两者分离:

- `http_server_port`(默认 8765)= **本地监听端口**,仅服务端绑定用,**不进对外 URL**
- `http_server_host` = **对外完整网址**,可含端口;对外端口由此处 host 决定

构造规则:
- **留空**(`""`):自动探测本机 IP,URL 拼本地端口(仅局域网可用,公网玩家无法访问)
- **走 80/反代**:`http_server_host = "play.example.com"`(不带端口,URL 为 `http://play.example.com/enchantlib-resourcepack.zip`)
- **直连非 80 端口**:`http_server_host = "play.example.com:8080"`(端口写在 host 里)
- **配置公网 IP**:`http_server_host = "203.0.113.5"` 或 `http_server_host = "203.0.113.5:8080"`

| 场景 | http_server_host | http_server_port | 对外 URL |
|------|------|------|------|
| 局域网联机 | `""` | 8765 | `http://<本机IP>:8765/...`(自动探测) |
| 公网域名(80/反代) | `"play.example.com"` | 8765(本地) | `http://play.example.com/...` |
| 公网域名(直连非80) | `"play.example.com:8080"` | 8765(本地) | `http://play.example.com:8080/...` |
| 公网 IP | `"203.0.113.5"` | 8765(本地) | `http://203.0.113.5/...` |
| 反向代理(CDN) | `"cdn.example.com"` | 8765(本地) | `http://cdn.example.com/...` |

**注意**:修改 `http_server_host` 后需**重启服务端**(`reload` 不重启 HTTP 服务器)。

### 6.4 防火墙配置

确保 `http_server_port`(默认 8765)对玩家客户端可访问。内网服务器需在路由器/防火墙开放该端口。

---

## 7. 调试与排障 | Debugging & Troubleshooting

### 7.1 开启调试模式

三种方式:

```bash
# 方式 1:修改配置文件后重载(持久化)
# config/enchantlib/acquisition.toml: debug_enabled = true
/enchantlib reload

# 方式 2:运行时切换(不持久化,重启回到配置值)
/enchantlib debug toggle

# 方式 3:查看当前状态
/enchantlib debug status
```

### 7.2 调试输出内容

开启调试模式后,以下路径输出详细日志(带 `[EnchantLib-Debug]` 前缀):

- **附魔注册**:ID、maxLevel、weight、exclusiveSet
- **互斥组注册**:标签 ID、成员数
- **事件分发**(基于装备附魔扫描的 `BuiltInEvents`,共 11 种):事件类型、回调数、异常隔离
- **战利品注入**:规则数、目标战利品表
- **村民交易**:交易 ID、职业、层级
- **配置加载**:每个配置文件的加载结果

> 全局事件 `EnchantLibEvents`(LIVING_ENTITY_TICK / FOOD_REGEN)不通过 `EnchantmentEventDispatcher`
> 分发,因此不进入上面的"事件分发"调试日志。但其启用状态会在服务端启动日志中单独输出
> (关键字:`LIVING_ENTITY_TICK 已启用` / `LIVING_ENTITY_TICK 未启用`)。

### 7.3 排障指南

#### 7.3.1 附魔未出现在 `/enchantlib list`

1. 检查模组的 `fabric.mod.json` 是否声明了 `enchantlib:enchantments` entrypoint
2. 查看服务端日志的 entrypoint 收集错误
3. 检查附魔 ID 是否与已注册附魔重复

#### 7.3.2 自定义附魔显示为 fallback 文本

1. 检查 `resource_distribution_enabled = true`
2. 检查 `assets/<modid>/enchant_sync/lang/` 是否有对应语言文件
3. 翻译键应为 `enchantment.<namespace>.<path>`
4. 确认玩家接受了资源包推送

#### 7.3.3 玩家无法下载资源包(公网)

1. 检查 `http_server_host` 是否配置为对外完整网址(域名或公网 IP,可含端口)
2. 检查 `http_server_port`(本地监听端口)是否在防火墙开放(反代场景需开放反代监听端口,内部端口可不对公网暴露)
3. 查看启动日志的资源包下载 URL(配置了 host 时 URL 不应含本地端口)
4. 反代场景确认代理正确转发,且 `http_server_host` 设为反代对外域名(不带端口)
5. 修改 `http_server_host` 后需重启服务端

#### 7.3.4 战利品未注入

1. 检查 `loot_injection_enabled = true`
2. 用 `/enchantlib debug status` 查看 `loot_injection` 状态
3. 检查目标战利品表 ID 是否正确

#### 7.3.5 村民交易未出现

1. 检查 `villager_trade_enabled = true`
2. 确认职业和层级正确
3. 原版图书管理员 tradeable 附魔是随机的,需多次刷新

#### 7.3.6 事件回调未触发

`BuiltInEvents`(11 种基于装备附魔扫描的事件)未触发时:

1. 用 `/enchantlib debug status` 查看"事件回调覆盖附魔"数量
2. 确认附魔已注册
3. 检查装备是否在正确槽位(`mainhand` 附魔需手持;`POST_HURT` 仅扫描护甲+副手不扫主手)
4. 开启调试查看事件分发日志
5. 检查 `trigger.toml` 是否误配阈值过高(见 7.3.8)

`EnchantLibEvents`(2 种全局事件)未触发时:

1. **LIVING_ENTITY_TICK**:此事件采用**懒挂载**,实现模组必须在启动时调用
   `EnchantLibEvents.enableLivingEntityTick()` 才会订阅 `ServerTickEvents`。若未调用,
   即使有回调注册也不会触发。可在服务端启动日志中查关键字 `LIVING_ENTITY_TICK 已启用`
   确认是否被启用。
2. **FOOD_REGEN**:此事件由 `FoodDataMixin` + `LivingEntityHealMixin` 桥接原生 `heal` 调用,
   仅在玩家自然回血(`FoodData.tick` 内部的 heal 调用)时触发。药水治疗、金苹果、信标等
   主动恢复手段不会触发此事件(这是设计如此,而非 bug)。

#### 7.3.7 玩家自然回血被异常压制

若玩家报告"饱食度满但不回血",可能原因:

1. 实现模组注册了 `FOOD_REGEN` 回调并误判条件,导致 `setCancelled(true)` 错误取消回血
2. 检查实现模组的 `FOOD_REGEN` 回调逻辑(如装备判定、附魔等级判定)
3. 临时排查:移除实现模组或调整相关附魔,观察是否恢复

注:`FOOD_REGEN` 仅取消 `FoodData.tick` 的 heal 调用,**不影响**药水/金苹果/信标等主动恢复,
**饱食度仍正常消耗**。

#### 7.3.8 trigger.toml 排障

如果 `POST_ATTACK`/`PROJECTILE_HIT` 事件未触发但附魔已正确注册:

1. **检查 `trigger.toml` 是否存在并配置了该附魔**:若附魔被配置为 `THRESHOLD` 或 `THRESHOLD_SCALED` 模式且 `threshold` 过高,低充能时不会触发
2. **检查阈值合理性**:`threshold = 0.9` 意味着充能需达到 90% 才触发
3. **临时回退测试**:将 mode 改为 `IGNORE` 或删除该附魔的覆盖配置,重启服务端验证
4. **检查全局默认阈值**:`force_threshold_min` 是否过高
5. **开启调试**:查看事件分发日志,确认充能值与阈值比较结果

```toml
# 临时调试:将该附魔改为 IGNORE 模式
["mymod:leech"]
mode = "IGNORE"
```

---

## 8. 禁用附魔后的存档行为 | Save Data Behavior

> 管理员必读:移除实现模组或禁用附魔前,请务必备份世界存档。缺失注册表条目对存档物品的影响是管理员最容易踩的坑。

### 8.1 场景说明

当你从服务端移除一个实现模组(即通过 EnchantLib API 注册自定义附魔的模组),或将某个附魔的 `enabled` 设为 `false` 后带着旧存档启动时,存档中已经存在的附魔物品会受到影响。

### 8.2 验证行为(MC 26.2 实测)

| 项目 | 行为 |
|------|------|
| **服务端启动** | 不崩溃,正常启动 |
| **日志表现** | WARN 级别日志:`Failed to decode value ... Failed to get element <modid>:<enchantment> missed input` |
| **物品展示框** | 反序列化失败,可能变为空(物品丢失) |
| **村民交易** | 含缺失附魔的交易反序列化失败,交易列表可能被清空 |
| **玩家背包** | 附魔物品可能变为空气(需备份保护) |
| **配置附魔(enchantlib 自己的)** | 不受影响,配置附魔由 EnchantLib 直接注册 |

### 8.3 日志示例

```
[Server thread/WARN] (Minecraft) [net.minecraft.world.level.chunk.storage.EntityStorage] Serialization errors:
chunk@[-5, 8]:
  .Entities[1]: Failed to decode value '{components:{"minecraft:stored_enchantments":{"enchantlib-testmod:demo":1}},count:1,id:"minecraft:enchanted_book"}' from field 'Item': Failed to get element enchantlib-testmod:demo missed input
```

### 8.4 恢复行为

- **重新安装实现模组后启动**:服务端恢复,不再出现 WARN
- **但已降级为空气的物品无法恢复**——原始物品数据在无实现模组的运行期间已被覆写保存
- **唯一恢复方式**:从备份还原世界存档

### 8.5 管理员建议

1. **移除实现模组前**:执行 `/world save-all` 保存世界,备份整个 `world/` 目录
2. **临时禁用附魔**:优先通过实现模组自身的 `enabled=false` 配置(如果支持),而非直接移除模组 jar
3. **长期移除**:先通知玩家消耗或转移附魔物品,再执行移除
4. **定期备份**:建议配置自动备份方案,避免意外丢失
5. **原版 `/reload` 安全**:`/reload` 不会破坏附魔 Holder 有效性,战利品注入会自动重新应用(已验证)

### 8.6 原因分析

Minecraft 26.2 使用组件系统(Data Components)存储物品附魔。附魔 ID 引用注册表中的条目。当注册表条目缺失时:

1. 物品的 `minecraft:enchantments` 或 `minecraft:stored_enchantments` 组件无法解码
2. 整个 `ItemStack` 解码失败
3. Minecraft 用默认值(空气)替代失败的物品
4. 保存时,空气覆写原始物品数据

这是 Minecraft 原版处理缺失注册表条目的标准行为,**并非 EnchantLib 的 bug**。所有自定义注册表类模组(自定义附魔、自定义物品、自定义实体等)都有相同的表现。

---
