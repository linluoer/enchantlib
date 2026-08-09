# EnchantLib

**English** | [中文](#中文)

> A pure-server-side custom enchantment library for Minecraft 26.2 (Fabric). It lets mod developers accomplish enchantment registration, exclusive sets, acquisition paths, event callbacks, and client-side resource distribution through a concise API — without hand-writing datapack, Mixin, or resource pack logic.

- **Version**: `26.2-1.0.0`
- **Environment**: Minecraft 26.2 · Fabric Loader ≥ 0.19.3 · Fabric API · Java 25
- **Side**: Pure server-side (no client install required; resources are pushed automatically by the built-in HTTP server)
- **License**: MIT

---

## Why EnchantLib

Under the MC 26.2 native enchantment system, custom enchantments require writing a lot of glue code by hand: runtime datapack injection, event dispatching for equipment enchantment scanning, exclusive set tags, loot table modification, villager trade injection, client language file distribution… EnchantLib wraps all of these into a declarative API; you only need to implement a single entrypoint interface.

**Core capabilities at a glance**:

| Capability | Description |
|------------|-------------|
| Enchantment registration | Chain-style `EnchantmentBuilder`, injects runtime datapacks, fully reuses the native enchantment system |
| Exclusive sets | Declare enchantment exclusivity, supports referencing 7 vanilla exclusive sets + custom exclusive sets |
| Loot injection | Inject enchanted books / already-enchanted items into 60+ vanilla loot tables |
| Villager trades | Add to `#minecraft:tradeable` for automatic sale, or register custom-priced trades (13 professions) |
| Event system | 11 per-enchantment events (attack/tick/projectile/block/interaction…) + 2 global events |
| Trigger policy | Gate by attack charge and scale by level to balance survival server experience |
| Entity categorization | `EntityCategory` marks players as undead/arthropod/illager/aquatic, so corresponding mobs won't attack |
| Entity counter | `EntityCounter` namespace-isolated thread-safe counter, auto-cleaned on player offline |
| Resource distribution | Auto-merges cross-mod language files, built-in HTTP server pushes the resource pack |
| Config-defined enchantments | Admins define enchantments via TOML config files, no code changes required |
| Operations commands | `/enchantlib list / give / groups / dump / reload / debug` |

**Performance design**: BuiltInEvents use bitmask short-circuiting, zero overhead when no implementation mod is installed; `LIVING_ENTITY_TICK` lazy-mounts, does not subscribe to ServerTickEvents when no callback is present; single-enchantment callback exceptions are automatically isolated, not affecting other enchantments or vanilla logic.

---

## Installation

### Player (server user)

Place `enchantlib-26.2-1.0.0.jar` in the server's `mods/` directory. No client install is required; when players join, EnchantLib automatically pushes the localization resource pack (if the server has resource distribution enabled).

Dependencies: Minecraft 26.2, Fabric Loader, Fabric API.

### Developer (mod author)

1. **Add dependency** (in your `build.gradle`):

```gradle
dependencies {
    modImplementation "com.enchantlib:enchantlib:26.2-1.0.0"
}
```

2. **Declare entrypoint** (in your `fabric.mod.json`):

```json
{
  "entrypoints": {
    "enchantlib:enchantments": ["com.mymod.MyModEnchantments"]
  }
}
```

3. **Implement the entrypoint interface** (complete minimal example):

```java
package com.mymod;

import com.enchantlib.api.*;

public class MyModEnchantments implements EnchantmentEntrypoint {

    @Override
    public void onRegisterEnchantments(EnchantmentRegistrar registrar) {
        registrar.register(EnchantmentBuilder.create("mymod:leech")
            .description("Leech")
            .supportedItems("#minecraft:enchantable/sharp_weapon")
            .weight(5).maxLevel(3)
            .minCost(5, 8).maxCost(20, 8).anvilCost(2)
            .slots("mainhand"));
    }
}
```

Start the server, run `/enchantlib list`, and you will see `mymod:leech`.

> This README covers common usage (registration, config, commands, deployment). For the complete API reference (event system, exclusive sets, loot injection, villager trades, trigger policy, EntityCategory, EntityCounter, resource distribution, etc.), see [docs/developer-guide.md](docs/developer-guide.md) inside the GitHub repo; CurseForge users may consult the GitHub repo.

### Building EnchantLib itself from source

If you cloned this repo and want to build EnchantLib yourself (rather than depending on it), note: the build script references Fabric API via local jars in the `libs/` directory (`flatDir` repository); these jars are not included in the repo due to size and convention.

**Runtime only needs 1 jar**: `fabric-api-26.2.jar` is a JarJar container; its `META-INF/jars/` already nests all submodules (fabric-api-base, fabric-loot-api-v3, fabric-lifecycle-events-v1, etc., 42 in total). However, **at compile time** javac does not resolve nested JarJar jars, and the outer container's top level does not contain `.class` files, so `build.gradle` additionally explicitly references 7 sub-module jars as compile class sources. So building from source requires placing the following 8 jars in `libs/`:

```
libs/
  fabric-api-26.2.jar              # runtime container (JarJar, embeds all submodules)
  fabric-api-base-2.0.4.jar        # compile class source: Event base class
  fabric-command-api-v2-3.1.0.jar  # compile class source: CommandRegistrationCallback
  fabric-entity-events-v1-5.0.5.jar
  fabric-events-interaction-v0-5.2.6.jar
  fabric-lifecycle-events-v1-4.1.3.jar
  fabric-loot-api-v3-3.0.17.jar
  fabric-permission-api-v1-1.0.3.jar
```

These jars can be extracted from the Fabric API `0.155.2+26.2` release package (runtime container + flattened sub-module jars). Then run `./gradlew build`. Normal mod developers do not need this step (just depend on EnchantLib via maven).

---

## Configuration files

EnchantLib reads three groups of configs under `config/enchantlib/`:

### `acquisition.toml` (global switches)

```toml
loot_injection_enabled = true        # master switch for loot injection
villager_trade_enabled = true        # master switch for villager trades
resource_distribution_enabled = true # master switch for client resource distribution
http_server_port = 8765              # resource pack HTTP server port
http_server_host = ""                # external full URL (mandatory for public deployment; may include port; empty means LAN only)
debug_enabled = false                # debug log switch
entity_tick_interval = 20            # ENTITY_TICK trigger interval (in ticks)
```

### `trigger.toml` (trigger policy override)

Overrides the code-registered `TriggerPolicy` by enchantment ID; takes priority over code:

```toml
force_threshold_min = 0.0            # global default threshold

["mymod:leech"]
mode = "THRESHOLD_SCALED"            # IGNORE / THRESHOLD / SCALED / THRESHOLD_SCALED
threshold = 0.7                      # charge threshold 0.0~1.0
```

### `enchantments/*.toml` (config-defined enchantments)

Admins can define enchantments without changing code; see section 13 of the developer manual.

---

## Operations commands

| Command | Purpose |
|---------|---------|
| `/enchantlib list` | List all registered enchantments |
| `/enchantlib give <enchantment> [level]` | Enchant the held item directly (for testing) |
| `/enchantlib groups` | List all exclusive sets |
| `/enchantlib dump <enchantment>` | Output the enchantment's full definition (JSON) |
| `/enchantlib reload` | Reload the `acquisition.toml` config |
| `/enchantlib debug status` | View debug status and system stats |
| `/enchantlib debug toggle` | Toggle the runtime debug switch |
| `/enchantlib debug info <enchantment>` | View detailed info for a specific enchantment |

---

## Public deployment notes

Resource distribution listens on `0.0.0.0:8765` by default, but the host in the download URL pushed to clients is determined by `http_server_host`. **Public deployment must configure this** as an external full URL (domain or public IP, may include port); otherwise clients receive a download URL pointing to a LAN address and cannot download the resource pack:

```toml
http_server_host = "play.example.com"   # no port for 80/reverse proxy; use "play.example.com:8080" for direct non-80
```

> `http_server_port` (default 8765) is only the **local listen port** (server bind) and **does not appear in the external URL**. The external port is determined by `http_server_host`: omit the port when behind 80/reverse proxy; include it in the host for direct non-80 access (e.g., `play.example.com:8080`).

If you don't need client localization (e.g., a pure survival server that doesn't show enchantment names), set `resource_distribution_enabled = false` to disable the entire resource distribution system.

---

## Documentation

The following documents are inside the GitHub repo and not shown on the CurseForge project page; please consult the GitHub repo:

- [Developer manual](docs/developer-guide.md) — complete API reference (registration, event system, exclusive sets, loot, trades, trigger policy, EntityCategory, EntityCounter, resource distribution)
- [Player guide](docs/player-guide.md) — for server owners and players
- [Admin guide](docs/admin-guide.md) — for server owners' operations

---

## Example mod

The example mod has been split into a separate repo (`enchantlib-examplemod`), implementing 12 enchantments to showcase the full API usage. See the separate repo's README.

---

## Tech stack

- Minecraft 26.2 native enchantment system (runtime datapack injection)
- Fabric Loader + Fabric API
- 10 Mixins (event bridging, entity categorization interception, runtime datapack injection)
- NightConfig TOML (config parsing)
- Built-in HTTP server (resource pack distribution)

## Author

liluo23 · MIT License

---

## 中文

> 面向 Minecraft 26.2 (Fabric) 的纯服务端自定义附魔库。让模组开发者用一套简洁 API 完成附魔的注册、互斥组、获取途径、事件回调与客户端资源分发,无需手写数据包、Mixin 或资源包逻辑。

- **版本**:`26.2-1.0.0`
- **环境**:Minecraft 26.2 · Fabric Loader ≥ 0.19.3 · Fabric API · Java 25
- **运行侧**:纯服务端(客户端无需安装,资源由内置 HTTP 服务器自动推送)
- **许可证**:MIT

---

## 为什么需要 EnchantLib

在 MC 26.2 原生附魔系统下,自定义附魔需要手写大量胶水代码:运行时数据包注入、装备附魔扫描的事件分发、互斥组标签、战利品表修改、村民交易注入、客户端语言文件分发……EnchantLib 把这些都封装成声明式 API,你只需实现一个 entrypoint 接口即可。

**核心能力一览**:

| 能力 | 说明 |
|------|------|
| 附魔注册 | `EnchantmentBuilder` 链式构建,注入运行时数据包,完全复用原生附魔系统 |
| 互斥组 | 声明附魔互斥关系,支持引用原版 7 个互斥组 + 自定义互斥组 |
| 战利品注入 | 把附魔书/已附魔物品注入原版 60+ 战利品表 |
| 村民交易 | 加入 `#minecraft:tradeable` 自动出售,或注册自定义定价交易(13 种职业) |
| 事件系统 | 11 种 per-enchantment 事件(攻击/tick/弹射物/方块/交互…)+ 2 种全局事件 |
| 触发策略 | 按攻击充能门控与等级缩放,平衡生存服体验 |
| 玩家分类 | `EntityCategory` 把玩家标记为亡灵/节肢/灾厄/水生,让对应怪物不攻击 |
| 实体计数器 | `EntityCounter` 命名空间隔离的线程安全计数器,玩家离线自动清理 |
| 资源分发 | 自动合并跨模组语言文件,内置 HTTP 服务器推送资源包 |
| 配置定义附魔 | 管理员通过 TOML 配置文件定义附魔,无需改代码 |
| 运维指令 | `/enchantlib list / give / groups / dump / reload / debug` |

**性能设计**:BuiltInEvents 使用位掩码短路,未安装实现模组时零开销;`LIVING_ENTITY_TICK` 懒挂载,无回调时不订阅 ServerTickEvents;单附魔回调异常自动隔离,不影响其他附魔与原版逻辑。

---

## 安装

### 玩家(服务端使用者)

把 `enchantlib-26.2-1.0.0.jar` 放入服务端 `mods/` 目录。客户端无需安装,玩家加入时 EnchantLib 会自动推送本地化资源包(如服务器开启了资源分发)。

依赖:Minecraft 26.2、Fabric Loader、Fabric API。

### 开发者(模组作者)

1. **添加依赖**(在你的 `build.gradle`):

```gradle
dependencies {
    modImplementation "com.enchantlib:enchantlib:26.2-1.0.0"
}
```

2. **声明 entrypoint**(在你的 `fabric.mod.json`):

```json
{
  "entrypoints": {
    "enchantlib:enchantments": ["com.mymod.MyModEnchantments"]
  }
}
```

3. **实现入口接口**(完整最小示例):

```java
package com.mymod;

import com.enchantlib.api.*;

public class MyModEnchantments implements EnchantmentEntrypoint {

    @Override
    public void onRegisterEnchantments(EnchantmentRegistrar registrar) {
        registrar.register(EnchantmentBuilder.create("mymod:leech")
            .description("Leech")
            .supportedItems("#minecraft:enchantable/sharp_weapon")
            .weight(5).maxLevel(3)
            .minCost(5, 8).maxCost(20, 8).anvilCost(2)
            .slots("mainhand"));
    }
}
```

启动服务端,执行 `/enchantlib list` 即可看到 `mymod:leech`。

> 本 README 已涵盖常用用法(注册、配置、命令、部署)。完整 API 参考(事件系统、互斥组、战利品注入、村民交易、触发策略、EntityCategory、EntityCounter、资源分发等)见 GitHub 仓库内 [docs/developer-guide.md](docs/developer-guide.md),CurseForge 用户可至 GitHub 仓库查阅。

### 从源码构建 EnchantLib 本身

若你克隆了本仓库想自行构建 EnchantLib(而非作为依赖引入),需注意:构建脚本通过 `libs/` 目录的本地 jar 引用 Fabric API(`flatDir` 仓库),这些 jar 因体积与惯例未纳入仓库。

**运行时只需 1 个 jar**:`fabric-api-26.2.jar` 是 JarJar 容器,`META-INF/jars/` 内已嵌套全部子模块(fabric-api-base、fabric-loot-api-v3、fabric-lifecycle-events-v1 等 42 个)。但**编译期** javac 不会解析 JarJar 嵌套 jar,外层容器顶层不含 `.class`,因此 `build.gradle` 额外显式引用了 7 个子模块 jar 作为编译类源。故从源码构建需把以下 8 个 jar 放入 `libs/`:

```
libs/
  fabric-api-26.2.jar              # 运行时容器(JarJar,内嵌全部子模块)
  fabric-api-base-2.0.4.jar        # 编译类源:Event 基类
  fabric-command-api-v2-3.1.0.jar  # 编译类源:CommandRegistrationCallback
  fabric-entity-events-v1-5.0.5.jar
  fabric-events-interaction-v0-5.2.6.jar
  fabric-lifecycle-events-v1-4.1.3.jar
  fabric-loot-api-v3-3.0.17.jar
  fabric-permission-api-v1-1.0.3.jar
```

这些 jar 可从 Fabric API `0.155.2+26.2` 发布包中提取(运行时容器 + 各子模块平铺 jar)。然后执行 `./gradlew build`。普通模组开发者无需此步骤(通过 maven 引入 EnchantLib 即可)。

---

## 配置文件

EnchantLib 在 `config/enchantlib/` 下读取三组配置:

### `acquisition.toml`(全局开关)

```toml
loot_injection_enabled = true        # 战利品注入总开关
villager_trade_enabled = true        # 村民交易总开关
resource_distribution_enabled = true # 客户端资源分发总开关
http_server_port = 8765              # 资源包 HTTP 服务器端口
http_server_host = ""                # 对外完整网址(公网部署必填,可含端口;留空仅局域网可用)
debug_enabled = false                # 调试日志开关
entity_tick_interval = 20            # ENTITY_TICK 触发间隔(单位 tick)
```

### `trigger.toml`(触发策略覆盖)

按附魔 ID 覆盖代码注册的 `TriggerPolicy`,优先级高于代码:

```toml
force_threshold_min = 0.0            # 全局默认阈值

["mymod:leech"]
mode = "THRESHOLD_SCALED"            # IGNORE / THRESHOLD / SCALED / THRESHOLD_SCALED
threshold = 0.7                      # 充能阈值 0.0~1.0
```

### `enchantments/*.toml`(配置文件定义附魔)

管理员无需改代码即可定义附魔,详见开发者手册第 13 节。

---

## 运维指令

| 指令 | 用途 |
|------|------|
| `/enchantlib list` | 列出所有已注册附魔 |
| `/enchantlib give <enchantment> [level]` | 手持物品直接附魔(测试用) |
| `/enchantlib groups` | 列出所有互斥组 |
| `/enchantlib dump <enchantment>` | 输出附魔完整定义(JSON) |
| `/enchantlib reload` | 重载 `acquisition.toml` 配置 |
| `/enchantlib debug status` | 查看调试状态与系统统计 |
| `/enchantlib debug toggle` | 切换运行时调试开关 |
| `/enchantlib debug info <enchantment>` | 查看指定附魔详细信息 |

---

## 公网部署提示

资源分发默认监听 `0.0.0.0:8765`,但推送给客户端的下载 URL 主机地址由 `http_server_host` 决定。**公网部署必须配置此项**为对外完整网址(域名或公网 IP,可含端口),否则客户端收到的下载 URL 指向局域网地址,无法下载资源包:

```toml
http_server_host = "play.example.com"   # 走 80/反代不带端口；直连非 80 端口写 "play.example.com:8080"
```

> `http_server_port`(默认 8765)只是**本地监听端口**(服务端绑定用),**不进对外 URL**。对外端口由 `http_server_host` 决定:走 80/反代时 host 不带端口;直连非 80 端口时把端口写在 host 里(如 `play.example.com:8080`)。

若不需要客户端本地化(例如纯生存服不显示附魔名),可设 `resource_distribution_enabled = false` 关闭整个资源分发系统。

---

## 文档

以下文档位于 GitHub 仓库内,CurseForge 项目页不展示,请前往 GitHub 仓库查阅:

- [开发者手册](docs/developer-guide.md) — 完整 API 参考(注册、事件系统、互斥组、战利品、交易、触发策略、EntityCategory、EntityCounter、资源分发)
- [玩家指南](docs/player-guide.md) — 面向服主与玩家
- [管理指南](docs/admin-guide.md) — 面向服主运维

---

## 示例模组

示例 mod 已拆为独立仓库(`enchantlib-examplemod`),实现 12 个附魔展示 API 的完整用法。详见独立仓库的 README。

---

## 技术栈

- Minecraft 26.2 原生附魔系统(运行时数据包注入)
- Fabric Loader + Fabric API
- 10 个 Mixin(事件桥接、玩家分类拦截、运行时数据包注入)
- NightConfig TOML(配置解析)
- 内置 HTTP 服务器(资源包分发)

## 作者

liluo23 · MIT License
