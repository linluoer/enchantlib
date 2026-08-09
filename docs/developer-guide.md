# EnchantLib 开发者手册

> 面向使用 EnchantLib API 注册自定义附魔的模组开发者。EnchantLib 是一个纯服务端 Fabric 模组,基于 MC 26.2 原生附魔系统提供完整的附魔生命周期管理。

## 目录

1. [概述](#1-概述)
2. [架构总览](#2-架构总览)
3. [快速开始](#3-快速开始)
4. [核心 API:注册附魔](#4-核心-api注册附魔)
5. [附魔效果](#5-附魔效果)
6. [互斥组](#6-互斥组)
7. [战利品注入](#7-战利品注入)
8. [村民交易](#8-村民交易)
9. [事件系统](#9-事件系统)
   - 9.1 [两套事件系统对比](#91-两套事件系统对比)
   - 9.2 [BuiltInEvents:基于装备附魔扫描的事件](#92-buildevents基于装备附魔扫描的事件)
   - 9.3 [EnchantLibEvents:全局事件](#93-enchantlibevents全局事件)
10. [触发策略 TriggerPolicy](#10-触发策略-triggerpolicy)
11. [EntityCategory 玩家分类 API](#11-entitycategory-玩家分类-api)
12. [EntityCounter 实体计数器 API](#12-entitycounter-实体计数器-api)
13. [配置文件定义附魔](#13-配置文件定义附魔)
14. [触发策略配置 trigger.toml](#14-触发策略配置-triggertoml)
15. [客户端资源分发](#15-客户端资源分发)
16. [调试](#16-调试)
17. [API 速查](#17-api-速查)

---

## 1. 概述

EnchantLib 是一个纯服务端 Fabric 模组,为其他模组提供自定义附魔的完整生命周期管理:

- **注册管线**:通过 entrypoint 收集附魔定义,注入运行时数据包
- **互斥组**:声明附魔互斥关系(不可共存于同一物品)
- **获取途径**:战利品注入、村民交易
- **事件系统(两套)**:
  - **BuiltInEvents**(11 种):基于装备附魔扫描的 per-enchantment 事件,经 `EnchantmentEventRegistrar` 注册,回调签名 `(event, ctx) -> void`,覆盖攻击/防御/tick/弹射物/方块破坏/交互等场景
  - **EnchantLibEvents**(2 种):全局事件,经 Fabric API `Event.register` 注册,不绑定附魔、不扫装备,回调签名 `(event) -> void`,用于在任意实体上触发效果(`LIVING_ENTITY_TICK`)或拦截原生机制(`FOOD_REGEN` 取消自然回血)
- **触发策略**:基于充能比例门控与等级缩放(TriggerPolicy),仅作用于充能事件(`POST_ATTACK` / `PROJECTILE_HIT`)
- **玩家分类**:将玩家标记为亡灵/节肢/灾厄/水生(EntityCategory)
- **实体计数器**:命名空间隔离的线程安全计数器(EntityCounter)
- **资源分发**:自动合并客户端语言文件,通过 HTTP 推送资源包
- **运维指令**:`/enchantlib list / give / groups / dump / reload / debug`

**运行环境**:Minecraft 26.2 + Fabric Loader + Fabric API

---

## 2. 架构总览

```
你的模组 (fabric.mod.json: entrypoints["enchantlib:enchantments"])
    │
    ▼
EnchantmentEntrypoint 接口实现
    │
    ├── onRegisterEnchantments(EnchantmentRegistrar)             → 附魔定义
    ├── onRegisterExclusiveGroups(ExclusiveGroupRegistrar)       → 互斥组
    ├── onRegisterLootInjections(LootInjectionRegistrar)         → 战利品注入
    ├── onRegisterVillagerTrades(VillagerTradeRegistrar)         → 村民交易
    └── onRegisterEventCallbacks(EnchantmentEventRegistrar, HolderLookup.Provider)
                │                                              ↳ BuiltInEvents 回调注册
                │                                              ↳ 可在此调用 EnchantLibEvents.enableLivingEntityTick()
                │                                                与 EnchantLibEvents.<EVENT>.register(...)
                ▼
        EnchantLib 主入口收集
                │
                ▼
        EnchantmentValidator 全局校验 (fail-fast)
                │
                ▼
        RuntimeDatapackContent 注入运行时数据包
                │
                ▼
        Minecraft 原生附魔系统加载
                │
                ▼
        事件分发
            ├── EnchantmentEventDispatcher (BuiltInEvents, Mixin + Fabric API 桥接,扫描装备附魔)
            └── EnchantLibEvents.<EVENT>.invoker() (全局事件,直接 Fabric Event 分发)
```

**关键设计**:

- 附魔定义以 JSON 形式注入运行时数据包,完全复用 MC 26.2 原生附魔系统
- BuiltInEvents 通过 Mixin + Fabric API 桥接原生事件,扫描实体装备附魔,单回调异常不影响其他附魔
- BuiltInEvents 使用位掩码短路(Mixin 热路径入口检查 `EnchantmentEventDispatcher.hasCallbacks(type)`),未安装实现模组时零开销
- `EnchantLibEvents.LIVING_ENTITY_TICK` 采用**懒挂载**:仅当实现模组调用 `enableLivingEntityTick()` 时才订阅 `ServerTickEvents.END_SERVER_TICK`,未启用时零开销
- 资源分发自动扫描 `assets/<modid>/enchant_sync/`,合并语言文件后通过 HTTP 推送
- 启动时由 `EnchantmentValidator` 进行全局校验(fail-fast),检测到错误立即崩溃

---

## 3. 快速开始

### 3.1 添加依赖

在你的 `build.gradle` 中添加 EnchantLib 依赖(作为 `modImplementation`):

```gradle
dependencies {
    modImplementation "com.enchantlib:enchantlib:1.0.0"
}
```

### 3.2 声明 entrypoint

在 `fabric.mod.json` 中声明 `enchantlib:enchantments` entrypoint:

```json
{
  "schemaVersion": 1,
  "entrypoints": {
    "enchantlib:enchantments": ["com.mymod.MyModEnchantments"]
  }
}
```

### 3.3 实现 EnchantmentEntrypoint

```java
package com.mymod;

import com.enchantlib.api.*;

public class MyModEnchantments implements EnchantmentEntrypoint {

    @Override
    public void onRegisterEnchantments(EnchantmentRegistrar registrar) {
        registrar.register(EnchantmentBuilder.create("mymod:leech")
            .description("Leech")
            .supportedItems("#minecraft:enchantable/sharp_weapon")
            .weight(5)
            .maxLevel(3)
            .minCost(5, 8)
            .maxCost(20, 8)
            .anvilCost(2)
            .slots("mainhand"));
    }
}
```

启动服务端后,`/enchantlib list` 应能看到 `mymod:leech`。

---

## 4. 核心 API:注册附魔

### 4.1 EnchantmentEntrypoint 入口接口

`com.enchantlib.api.EnchantmentEntrypoint` 是模组接入 EnchantLib 的唯一入口,包含 5 个方法:

| 方法 | 必填 | 说明 |
|------|------|------|
| `onRegisterEnchantments(EnchantmentRegistrar)` | 是(abstract) | 注册附魔定义 |
| `onRegisterExclusiveGroups(ExclusiveGroupRegistrar)` | 否(default 空) | 注册互斥组 |
| `onRegisterLootInjections(LootInjectionRegistrar)` | 否(default 空) | 注册战利品注入 |
| `onRegisterVillagerTrades(VillagerTradeRegistrar)` | 否(default 空) | 注册村民交易 |
| `onRegisterEventCallbacks(EnchantmentEventRegistrar, HolderLookup.Provider)` | 否(default 空) | 注册事件回调(BuiltInEvents + EnchantLibEvents) |

> 注:`onRegisterEventCallbacks` 在 `SERVER_STARTED` 阶段被调用(注册表完全就绪后),因此可在此通过 `registries.lookupOrThrow(Registries.ENCHANTMENT)` 解析附魔 `Holder`。EnchantLibEvents 的全局回调通常也在此处注册,因为此时 SmeltingLookup 等基础设施已就绪。

### 4.2 EnchantmentBuilder 链式构建

`com.enchantlib.api.EnchantmentBuilder` 提供链式 API 构建附魔:

```java
registrar.register(EnchantmentBuilder.create("mymod:my_enchant")
    .description("My Enchantment")                              // 兜底显示名
    .description("enchantment.mymod.my_enchant", "My Enchant")  // 指定翻译键 + 兜底
    .supportedItems("#minecraft:enchantable/sharp_weapon")       // 必填:支持物品
    .primaryItems("#minecraft:enchantable/melee_weapon")         // 可选:附魔台优先
    .weight(10)                                                  // 权重 1~15
    .maxLevel(5)                                                 // 最大等级
    .minCost(1, 11)                                              // base + perLevelAboveFirst
    .maxCost(21, 11)                                              // 同上
    .anvilCost(4)                                                // 铁砧成本
    .slots("mainhand", "offhand")                                // 生效槽位(可变参数)
    .addSlot("armor")                                            // 追加单个槽位
    .exclusiveSet(ExclusiveSets.DAMAGE)                          // 可选:互斥组
    .effects(EnchantmentEffectsBuilder.create()                  // 可选:附魔效果
        .addDamage(1.0, 0.5))
    .acquisition(true, true));                                   // 可选:获取途径开关
```

### 4.3 EnchantmentBuilder 全部方法

| 方法 | 说明 |
|------|------|
| `static create(String id)` / `create(Identifier id)` | 创建构建器,ID 格式 "modid:name" |
| `description(String fallback)` | 设置兜底显示文本,自动生成翻译键 `enchantment.<ns>.<path>` |
| `description(String key, String fallback)` | 指定翻译键 + 兜底文本 |
| `supportedItems(String tagOrItem)` | 必填,标签如 `#minecraft:enchantable/sharp_weapon` 或物品 ID |
| `primaryItems(String tagOrItem)` | 可选,附魔台优先物品 |
| `weight(int)` | 权重 1~15 |
| `maxLevel(int)` | 最大附魔等级 |
| `minCost(int base, int perLevelAboveFirst)` | 最小成本 |
| `maxCost(int base, int perLevelAboveFirst)` | 最大成本 |
| `anvilCost(int)` | 铁砧修复成本 |
| `slots(String...)` | 设置生效槽位(覆盖) |
| `addSlot(String)` | 追加单个生效槽位 |
| `exclusiveSet(String tag)` | 互斥组标签引用 |
| `effects(JsonObject)` | 直接传 effects JSON |
| `effects(EnchantmentEffectsBuilder)` | 通过 builder 构建 effects |
| `acquisition(boolean loot, boolean trade)` | 单附魔获取途径开关(仅配置文件定义的附魔生效) |
| `getId()` / `getDescriptionFallback()` / `getDescriptionKey()` | getter |
| `getSupportedItems()` / `getPrimaryItems()` / `getWeight()` / `getMaxLevel()` / `getAnvilCost()` | getter |
| `getSlots()` / `getExclusiveSet()` / `isAcquisitionLoot()` / `isAcquisitionTrade()` | getter |
| `toJson()` / `toBytes()` | 序列化为 JSON 字符串/UTF-8 字节数组 |

### 4.4 字段说明

| 字段 | 必填 | 说明 |
|------|------|------|
| `description` | 是 | 兜底显示文本,自动生成翻译键 `enchantment.<ns>.<path>` |
| `supportedItems` | 是 | 支持物品,标签(`#minecraft:...`)或物品 ID |
| `primaryItems` | 否 | 主要物品,附魔台优先为此类物品附魔 |
| `weight` | 是 | 权重 1~15,影响附魔台出现概率 |
| `maxLevel` | 是 | 最大附魔等级 |
| `minCost` | 是 | `base` 基础需求 + `perLevelAboveFirst` 每级递增 |
| `maxCost` | 是 | 同上 |
| `anvilCost` | 是 | 铁砧修复成本(经验等级) |
| `slots` | 是 | 生效槽位:`mainhand`/`offhand`/`armor`/`feet`/`legs`/`chest`/`head`/`body` |
| `exclusiveSet` | 否 | 互斥组标签引用(见第 6 节) |
| `effects` | 否 | 附魔效果(见第 5 节) |
| `acquisition` | 否 | 单附魔获取途径开关(仅配置文件定义的附魔生效) |

### 4.5 description 与翻译

- `description(String fallback)`:自动生成翻译键 `enchantment.<namespace>.<path>`
- `description(String key, String fallback)`:手动指定翻译键
- 客户端资源包需提供 `lang/<lang_code>.json` 中的翻译条目
- 裸客户端(无资源包)显示 fallback 文本

### 4.6 EnchantmentRegistrar 注册器

`com.enchantlib.api.EnchantmentRegistrar`:

| 方法 | 说明 |
|------|------|
| `register(EnchantmentBuilder)` | 注册附魔,重复 ID 抛 `IllegalStateException` |
| `getBuilders()` | 不可修改的附魔构建器列表 |
| `size()` | 已注册附魔数量 |
| `contains(Identifier)` | 判断是否已注册指定 ID |

---

## 5. 附魔效果

### 5.1 EnchantmentEffectsBuilder 便捷方法

`com.enchantlib.api.EnchantmentEffectsBuilder`:

```java
EnchantmentEffectsBuilder.create()
    .addDamage(1.0, 0.5)                    // 线性伤害(sharpness 风格)
    .addDamage(2.5, 2.5, smiteCondition)     // 带条件的伤害(smite 风格)
    .addKnockback(1.0, 1.0)                  // 击退
    .addDamageProtection(1.0, 0.5)           // 伤害保护
    .addDamageProtection(1.0, 0.5, cond)     // 带条件的保护
    .addPostAttackIgnite(3.0, 1.0);          // 攻击后点燃(fire_aspect 风格)
```

### 5.2 EnchantmentEffectsBuilder 全部方法

| 方法 | 对应 effect 类型 | 说明 |
|------|----------------|------|
| `static create()` | - | 创建构建器 |
| `addDamage(double base, double perLevelAboveFirst)` | `minecraft:damage` | 线性伤害 |
| `addDamage(double base, double perLevelAboveFirst, JsonObject requirements)` | `minecraft:damage` | 带条件的伤害 |
| `addKnockback(double base, double perLevelAboveFirst)` | `minecraft:knockback` | 击退 |
| `addDamageProtection(double base, double perLevelAboveFirst)` | `minecraft:damage_protection` | 伤害保护 |
| `addDamageProtection(double base, double perLevelAboveFirst, JsonObject requirements)` | `minecraft:damage_protection` | 带条件的保护 |
| `addPostAttackIgnite(double baseDuration, double perLevelAboveFirst)` | `minecraft:post_attack` + `minecraft:ignite` | 攻击后点燃 |
| `addValueEffectEntry(String effectType, JsonObject effect, JsonObject requirements)` | 通用值类型 | 通用值效果 |
| `addPostAttackEntry(String affected, String enchanted, JsonObject effect, JsonObject requirements)` | `minecraft:post_attack` | 通用 post_attack |
| `addEntry(String effectType, JsonObject entry)` | 任意 | 完全自定义条目 |
| `build()` | - | 返回 JsonObject |

### 5.3 通用方法

对于未提供便捷方法的效果类型,使用通用 API:

```java
EnchantmentEffectsBuilder.create()
    // 添加值类型效果(damage/knockback/protection/armor_effectiveness 等)
    .addValueEffectEntry("minecraft:damage", effectJson, requirementsJson)
    // 添加 post_attack 效果
    .addPostAttackEntry("victim", "attacker", effectJson, requirementsJson)
    // 添加任意效果条目(完全自定义)
    .addEntry("minecraft:my_custom_effect", entryJson);
```

### 5.4 事件驱动 vs effects JSON

EnchantLib 支持两种附魔效果实现方式:

| 方式 | 适用场景 | 示例 |
|------|---------|------|
| **effects JSON** | 原版支持的值类型效果 | 伤害加成、击退、保护、点燃 |
| **事件回调** | 需要复杂逻辑的效果 | 吸血、条件性增益、自定义粒子 |

一个附魔可以同时使用两种方式:effects JSON 定义基础数值,事件回调处理特殊逻辑。

---

## 6. 互斥组

### 6.1 引用原版互斥组

```java
EnchantmentBuilder.create("mymod:my_damage")
    .exclusiveSet(ExclusiveSets.DAMAGE)    // 与 sharpness/smite 等互斥
    ...
```

`com.enchantlib.api.ExclusiveSets` 提供的原版互斥组常量:

| 常量 | 标签引用 | 成员附魔 |
|------|---------|---------|
| `DAMAGE` | `#minecraft:exclusive_set/damage` | sharpness, smite, bane_of_arthropods, impaling, density, breach |
| `ARMOR` | `#minecraft:exclusive_set/armor` | protection, blast_protection, fire_protection, projectile_protection |
| `BOOTS` | `#minecraft:exclusive_set/boots` | frost_walker, depth_strider |
| `BOW` | `#minecraft:exclusive_set/bow` | infinity, mending |
| `CROSSBOW` | `#minecraft:exclusive_set/crossbow` | multishot, piercing |
| `MINING` | `#minecraft:exclusive_set/mining` | fortune, silk_touch |
| `TRIDENT` | `#minecraft:exclusive_set/riptide` | loyalty, channeling |

附魔 ID 列表常量:`DAMAGE_ENCHANTMENTS` / `ARMOR_ENCHANTMENTS` / `BOOTS_ENCHANTMENTS` / `BOW_ENCHANTMENTS` / `CROSSBOW_ENCHANTMENTS` / `MINING_ENCHANTMENTS` / `TRIDENT_ENCHANTMENTS`(均为 `Set<String>`)。

辅助方法:

| 方法 | 说明 |
|------|------|
| `isVanillaGroup(String tagRef)` | 判断标签引用是否为原版互斥组 |
| `getVanillaGroupEnchantments(String tagRef)` | 获取原版互斥组成员附魔 ID 列表,非原版组返回 null |
| `VANILLA_GROUPS` | `Map<String, Set<String>>`,原版互斥组标签引用 → 成员附魔 ID 列表 |

### 6.2 自定义互斥组

`com.enchantlib.api.ExclusiveGroupBuilder`:

```java
@Override
public void onRegisterExclusiveGroups(ExclusiveGroupRegistrar registrar) {
    registrar.register(ExclusiveGroupBuilder.create("mymod", "elemental")
        .add("mymod:fire_aspect")
        .add("mymod:ice_aspect"));
}
```

在附魔定义中引用:

```java
EnchantmentBuilder.create("mymod:fire_aspect")
    .exclusiveSet("#mymod:exclusive_set/elemental")
    ...
```

### 6.3 ExclusiveGroupBuilder 全部方法

| 方法 | 说明 |
|------|------|
| `static create(String groupId)` | 创建构建器,默认命名空间 `enchantlib` |
| `static create(String namespace, String groupId)` | 创建构建器,指定命名空间 |
| `add(String enchantmentId)` | 添加互斥的附魔 |
| `addAll(List<String> enchantmentIds)` | 批量添加 |
| `getTagId()` | 标签 ID,格式 `<ns>:exclusive_set/<group>` |
| `getTagReference()` | 标签引用字符串(以 `#` 开头),可直接传入 `exclusiveSet()` |
| `getResourceId()` | 数据包资源 ID,路径 `tags/enchantment/exclusive_set/<group>.json` |
| `getEnchantments()` | 不可修改的附魔 ID 列表 |
| `toJson()` / `toBytes()` | 序列化为 JSON 字符串/字节数组 |

### 6.4 ExclusiveGroupRegistrar

| 方法 | 说明 |
|------|------|
| `register(ExclusiveGroupBuilder)` | 注册互斥组,重复标签 ID 抛异常 |
| `getBuilders()` | 不可修改的互斥组构建器列表 |
| `size()` | 已注册互斥组数量 |
| `contains(Identifier tagId)` | 判断是否已注册指定标签 ID |

---

## 7. 战利品注入

将自定义附魔以附魔书或已附魔物品形式注入到原版战利品表。

### 7.1 LootInjectionBuilder

```java
@Override
public void onRegisterLootInjections(LootInjectionRegistrar registrar) {
    // 向地下城、矿道箱子注入附魔书
    registrar.register(LootInjectionBuilder.create()
        .toTables(LootTables.SIMPLE_DUNGEON, LootTables.ABANDONED_MINESHAFT)
        .asBook()
        .withEnchantments("mymod:fire_aspect", "mymod:ice_aspect")
        .chance(0.5F)        // 50% 概率出现
        .weight(1)           // 战利品池权重(>=1,默认 1)
        .quality(0));        // luck 属性下的权重加成(默认 0)

    // 向末地城注入附魔钻石剑
    registrar.register(LootInjectionBuilder.create()
        .toTables(LootTables.END_CITY_TREASURE)
        .asItem(Items.DIAMOND_SWORD)
        .withEnchantments("mymod:sharpness_plus")
        .chance(0.3F));
}
```

### 7.2 LootInjectionBuilder 全部方法

| 方法 | 说明 |
|------|------|
| `static create()` | 创建构建器 |
| `toTables(String...)` | 指定目标战利品表 |
| `toTables(Set<String>)` | 指定目标战利品表(集合) |
| `asBook()` | 物品形式为附魔书(默认) |
| `asItem(Item)` / `asItem(ItemLike)` | 物品形式为已附魔物品 |
| `withEnchantments(String...)` | 添加候选附魔 |
| `withEnchantments(Set<String>)` | 添加候选附魔(集合) |
| `chance(float)` | 注入概率 0.0~1.0(默认 1.0) |
| `weight(int)` | 战利品条目权重 >=1(默认 1) |
| `quality(int)` | 战利品条目质量(默认 0) |
| `build()` | 构建不可变 `LootInjection` |

### 7.3 LootInjection(不可变)

| 方法 | 说明 |
|------|------|
| `getTargetTables()` | 目标战利品表 ID 列表(不可修改) |
| `getForm()` | 物品形式,`Form.BOOK` 或 `Form.ITEM` |
| `getItem()` | 目标物品(仅 form==ITEM 时有效,BOOK 返回 null) |
| `getEnchantments()` | 候选附魔 ID 列表(不可修改) |
| `getChance()` | 注入概率 |
| `getWeight()` | 权重 |
| `getQuality()` | 质量 |

### 7.4 LootInjectionRegistrar

| 方法 | 说明 |
|------|------|
| `register(LootInjectionBuilder)` | 注册规则(自动调用 `build()`) |
| `register(LootInjection)` | 直接注册已构建的规则 |
| `getInjections()` | 不可修改的注入规则列表 |
| `size()` | 已注册规则数量 |

### 7.5 LootTables 战利品表常量

`com.enchantlib.api.LootTables` 提供 MC 26.2 所有原版战利品表 ID 字符串常量。

**箱子战利品**(chests/*):

`SPAWN_BONUS_CHEST`, `END_CITY_TREASURE`, `SIMPLE_DUNGEON`, `VILLAGE_WEAPONSMITH`, `VILLAGE_TOOLSMITH`, `VILLAGE_ARMORER`, `VILLAGE_CARTOGRAPHER`, `VILLAGE_MASON`, `VILLAGE_SHEPHERD`, `VILLAGE_BUTCHER`, `VILLAGE_FLETCHER`, `VILLAGE_FISHER`, `VILLAGE_TANNERY`, `VILLAGE_TEMPLE`, `VILLAGE_DESERT_HOUSE`, `VILLAGE_PLAINS_HOUSE`, `VILLAGE_TAIGA_HOUSE`, `VILLAGE_SNOWY_HOUSE`, `VILLAGE_SAVANNA_HOUSE`, `ABANDONED_MINESHAFT`, `NETHER_BRIDGE`, `STRONGHOLD_LIBRARY`, `STRONGHOLD_CROSSING`, `STRONGHOLD_CORRIDOR`, `DESERT_PYRAMID`, `JUNGLE_TEMPLE`, `IGLOO_CHEST`, `WOODLAND_MANSION`, `UNDERWATER_RUIN_SMALL`, `UNDERWATER_RUIN_BIG`, `BURIED_TREASURE`, `SHIPWRECK_MAP`, `SHIPWRECK_SUPPLY`, `SHIPWRECK_TREASURE`, `PILLAGER_OUTPOST`, `BASTION_TREASURE`, `BASTION_OTHER`, `BASTION_BRIDGE`, `BASTION_HOGLIN_STABLE`, `ANCIENT_CITY`, `ANCIENT_CITY_ICE_BOX`, `RUINED_PORTAL`

**试炼密室**(trial_chambers/*):

`TRIAL_CHAMBERS_REWARD`, `TRIAL_CHAMBERS_REWARD_COMMON`, `TRIAL_CHAMBERS_REWARD_RARE`, `TRIAL_CHAMBERS_REWARD_UNIQUE`, `TRIAL_CHAMBERS_REWARD_OMINOUS`, `TRIAL_CHAMBERS_REWARD_OMINOUS_COMMON`, `TRIAL_CHAMBERS_REWARD_OMINOUS_RARE`, `TRIAL_CHAMBERS_REWARD_OMINOUS_UNIQUE`, `TRIAL_CHAMBERS_SUPPLY`, `TRIAL_CHAMBERS_CORRIDOR`, `TRIAL_CHAMBERS_INTERSECTION`, `TRIAL_CHAMBERS_INTERSECTION_BARREL`, `TRIAL_CHAMBERS_ENTRANCE`

**钓鱼**(gameplay/fishing/*):

`FISHING`, `FISHING_JUNK`, `FISHING_TREASURE`, `FISHING_FISH`

**玩法**(gameplay/*):

`CAT_MORNING_GIFT`, `PIGLIN_BARTERING`, `SNIFFER_DIGGING`

**考古**(archaeology/*):

`DESERT_WELL_ARCHAEOLOGY`, `DESERT_PYRAMID_ARCHAEOLOGY`, `TRAIL_RUINS_ARCHAEOLOGY_COMMON`, `TRAIL_RUINS_ARCHAEOLOGY_RARE`, `OCEAN_RUIN_WARM_ARCHAEOLOGY`, `OCEAN_RUIN_COLD_ARCHAEOLOGY`

**受全局开关控制**:`config/enchantlib/acquisition.toml` 中 `loot_injection_enabled = false` 时所有注入规则不生效。

---

## 8. 村民交易

### 8.1 让附魔可被图书管理员出售

```java
@Override
public void onRegisterVillagerTrades(VillagerTradeRegistrar registrar) {
    // 加入 #minecraft:tradeable 标签,图书管理员 Level 1~4 自动出售
    registrar.registerTradeableEnchantments(
        TradeableEnchantmentsBuilder.create()
            .addEnchantments("mymod:fire_aspect", "mymod:ice_aspect"));
}
```

### 8.2 自定义村民交易

```java
@Override
public void onRegisterVillagerTrades(VillagerTradeRegistrar registrar) {
    // 武器匠 Level 3 出售附魔钻石剑
    registrar.registerTrade(VillagerTradeBuilder
        .create("mymod:weaponsmith/3/sharpness_sword")
        .profession(VillagerTrades.WEAPONSMITH)
        .level(VillagerTrades.LEVEL_3)
        .asItem(Items.DIAMOND_SWORD)
        .withEnchantments("mymod:sharpness_plus")
        .emeralds(20)
        .noAdditionalItem()
        .maxUses(3)
        .xp(10)
        .priceMultiplier(0.05F));

    // 图书管理员 Level 5 出售稀有附魔书(价格翻倍)
    registrar.registerTrade(VillagerTradeBuilder
        .create("mymod:librarian/5/ice_aspect_book")
        .profession(VillagerTrades.LIBRARIAN)
        .level(VillagerTrades.LEVEL_5)
        .asBook()
        .withEnchantments("mymod:ice_aspect")
        .emeralds(10)
        .additionalItem(Items.BOOK, 1)
        .maxUses(5)
        .xp(30)
        .priceMultiplier(0.2F)
        .doublePrice(true));        // 对 #minecraft:double_trade_price 翻倍
}
```

### 8.3 VillagerTradeBuilder 全部方法

| 方法 | 说明 |
|------|------|
| `static create(String tradeId)` | 创建构建器,ID 如 "mymod:librarian/1/fire_aspect_book" |
| `static create(String namespace, String path)` | 创建构建器,指定命名空间和路径 |
| `profession(String)` | 设置职业(参见 VillagerTrades 常量) |
| `level(int)` | 设置层级 1-5 |
| `asBook()` | 物品形式为附魔书(默认) |
| `asItem(Item)` / `asItem(ItemLike)` | 物品形式为已附魔物品 |
| `emeralds(int)` | 主成本(emerald 数量,>=0) |
| `additionalItem(Item, int)` / `additionalItem(ItemLike, int)` | 附加成本物品(默认 1 本书) |
| `noAdditionalItem()` | 移除附加成本 |
| `withEnchantments(String...)` / `withEnchantments(Set<String>)` | 添加候选附魔 |
| `maxUses(int)` | 最大使用次数(默认 12,>=1) |
| `xp(int)` | 经验值(默认 1,>=0) |
| `priceMultiplier(float)` | 价格倍率 0.0~1.0(默认 0.2) |
| `doublePrice(boolean)` | 是否对 `#minecraft:double_trade_price` 标签中的附魔翻倍价格(默认 false) |
| `build()` | 构建不可变 `VillagerTradeInjection` |
| `getTradeId()` / `getProfession()` / `getLevel()` | getter |

### 8.4 VillagerTrades 常量

`com.enchantlib.api.VillagerTrades`:

**等级常量**:`LEVEL_1`(1) / `LEVEL_2`(2) / `LEVEL_3`(3) / `LEVEL_4`(4) / `LEVEL_5`(5)

**职业常量**:

| 常量 | 值 | 说明 |
|------|----|----|
| `FARMER` | "farmer" | 农民 |
| `FISHERMAN` | "fisherman" | 渔夫 |
| `SHEPHERD` | "shepherd" | 牧羊人 |
| `FLETCHER` | "fletcher" | 制箭师 |
| `LIBRARIAN` | "librarian" | 图书管理员(可自动出售附魔书) |
| `CARTOGRAPHER` | "cartographer" | 制图师 |
| `CLERIC` | "cleric" | 牧师 |
| `COMMON_SMITH` | "common_smith" | 通用 smith(未细化分支) |
| `ARMORER` | "armorer" | 盔甲匠 |
| `WEAPONSMITH` | "weaponsmith" | 武器匠 |
| `TOOLSMITH` | "toolsmith" | 工具匠 |
| `BUTCHER` | "butcher" | 屠夫 |
| `LEATHERWORKER` | "leatherworker" | 皮匠 |
| `MASON` | "mason" | 石匠 |
| `WANDERING_TRADER` | "wandering_trader" | 流浪商人(特殊,无层级,使用 buying/uncommon/common 子分类) |

**辅助方法**:

- `isValidProfession(String)` - 校验职业字符串是否合法
- `isValidLevel(int)` - 校验等级是否合法(1-5)

### 8.5 VillagerTradeRegistrar

| 方法 | 说明 |
|------|------|
| `registerTradeableEnchantments(TradeableEnchantmentsBuilder)` | 加入 `#minecraft:tradeable` 标签(自动 build) |
| `addTradeableEnchantments(Set<String>)` | 直接添加(内部使用) |
| `registerTrade(VillagerTradeBuilder)` | 注册自定义交易(自动 build,重复 trade ID 抛异常) |
| `registerTrade(VillagerTradeInjection)` | 直接注册已构建的交易 |
| `getTradeableEnchantments()` | 不可修改的可交易附魔 ID 集合 |
| `getTrades()` | 不可修改的交易列表 |
| `tradeableEnchantmentsCount()` / `tradesCount()` | 数量统计 |
| `isEmpty()` | 是否为空 |

### 8.6 TradeableEnchantmentsBuilder

| 方法 | 说明 |
|------|------|
| `static create()` | 创建构建器 |
| `addEnchantments(String...)` / `addEnchantments(Set<String>)` | 添加可交易附魔 |
| `build()` | 构建不可变 `Set<String>`(为空抛异常) |
| `static toTagJsonBytes(Set<String>)` | 生成 `data/minecraft/tags/enchantment/tradeable.json` 字节数组 |

**受全局开关控制**:`villager_trade_enabled = false` 时所有交易不生效。

---

## 9. 事件系统

EnchantLib 提供**两套独立的事件系统**,适用场景不同,不能混用。

### 9.1 两套事件系统对比

| 维度 | BuiltInEvents | EnchantLibEvents |
|------|---------------|------------------|
| **包路径** | `com.enchantlib.event.BuiltInEvents` | `com.enchantlib.event.EnchantLibEvents` |
| **事件数** | 11 种 per-enchantment 事件 | 2 个全局事件 |
| **注册方式** | `EnchantmentEventRegistrar.register(holder, type, callback)` | `EnchantLibEvents.<EVENT>.register(callback)` |
| **回调签名** | `(event, ctx) -> void`(ctx 含附魔 Holder/level/itemStack/slot) | `(event) -> void`(无 ctx,不绑定附魔) |
| **是否扫装备附魔** | 是,自动扫描实体装备附魔并按附魔聚合分发 | 否,直接对每个事件触发一次 |
| **触发条件** | 实体装备上有该附魔 + 事件触发 | 事件触发(对 LIVING_ENTITY_TICK 还需先调用 `enableLivingEntityTick()`) |
| **适用场景** | per-enchantment 效果(吸血、击退、自动烧炼等) | 不依赖具体附魔物品的全局逻辑(焚心持续伤害、自然回血压制等) |
| **TriggerPolicy** | 对充能事件(POST_ATTACK/PROJECTILE_HIT)生效 | 不适用(无充能概念) |
| **异常隔离** | 单附魔异常不影响其他附魔 | Fabric Event 标准行为(单回调异常会中断后续回调,实现模组需自行 try-catch) |
| **位掩码短路** | 是,Mixin 热路径检查 `hasCallbacks(type)` | LIVING_ENTITY_TICK 通过懒挂载实现零开销(无回调时不订阅 ServerTickEvents) |

**典型组合用法**:

- 附魔 A 注册 `BuiltInEvents.POST_ATTACK` 回调,在攻击时给目标打标记(用 `EntityCounter.set`)
- 同时注册 `EnchantLibEvents.LIVING_ENTITY_TICK` 回调,每 tick 检查所有实体的标记并造成持续伤害(如焚心)

### 9.2 BuiltInEvents:基于装备附魔扫描的事件

`com.enchantlib.event.BuiltInEvents` 提供 11 种 per-enchantment 事件类型常量与事件 record 类。回调通过 `EnchantmentEventRegistrar` 注册,触发时由 `EnchantmentEventDispatcher` 扫描实体装备附魔,按附魔聚合分发。

#### 9.2.1 内置事件类型一览

| 事件类型 | 触发时机 | 扫描对象 | 实现 | 典型用途 |
|---------|---------|---------|------|---------|
| `POST_ATTACK` | 玩家攻击实体后 | 攻击者装备(主手+副手) | Mixin (PlayerAttackMixin) | 吸血、击退、点燃 |
| `ENTITY_TICK` | 每 tick 对 LivingEntity(节流) | 实体所有装备 | Mixin (受配置节流) | 周期性效果(回调须轻量) |
| `PROJECTILE_HIT` | 弹射物命中实体 | 发射者装备 | Mixin (ProjectileWeaponMixin) | 弓附魔命中效果 |
| `MODIFY_DAMAGE` | 伤害应用前 | 攻击者装备 | Mixin (LivingEntityHurtMixin) | 自定义伤害加成 |
| `POST_HURT` | 实体受击后 | 目标装备(护甲+副手) | Fabric API (AFTER_DAMAGE) | 防御类附魔、反伤 |
| `POST_KILL` | 实体被击杀后 | 击杀者装备 | Fabric API (AFTER_DEATH) | 击杀奖励、回血 |
| `MODIFY_BLOCK_DROPS` | 方块掉落物修改 | 玩家装备(主手+副手) | Mixin (BlockDropResourcesMixin) | 自动烧炼、掉落物加倍 |
| `POST_BLOCK_BREAK` | 方块破坏后(纯通知) | 玩家装备(主手+副手) | Fabric API (AFTER) | 破坏方块回蓝、连锁触发 |
| `ITEM_USE` | 玩家右键使用物品 | 触发手装备 | Fabric API (UseItemCallback) | 右键释放技能类附魔 |
| `BLOCK_USE` | 玩家右键方块 | 触发手装备 | Fabric API (UseBlockCallback) | 右键方块转换、耕地 |
| `ENTITY_USE` | 玩家右键实体 | 触发手装备 | Fabric API (UseEntityCallback) | 右键实体标记、驯服增强 |

> **槽位说明**:`ATTACK_SLOTS = {MAINHAND, OFFHAND}`(攻击类事件),`DEFENSE_SLOTS = {HEAD, CHEST, LEGS, FEET, OFFHAND}`(POST_HURT),`ENTITY_TICK` 扫描所有槽位。**重要**:`POST_HURT` 不扫描主手,因此主手剑附魔若需在受击时触发,实现模组需自行注册 `ServerLivingEntityEvents.AFTER_DAMAGE`(Fabric 原生事件)并手动检查主手附魔。

#### 9.2.2 注册事件回调

```java
@Override
public void onRegisterEventCallbacks(EnchantmentEventRegistrar registrar,
                                      HolderLookup.Provider registries) {
    Holder<Enchantment> leech = resolveEnchantment(registries, "mymod:leech");

    if (leech != null) {
        // POST_ATTACK:攻击后吸血(默认 IGNORE 策略)
        registrar.register(leech, BuiltInEvents.POST_ATTACK, (event, ctx) -> {
            float healAmount = ctx.level() * 2.0F;
            event.attacker().heal(healAmount);
        });

        // MODIFY_DAMAGE:修改伤害值
        registrar.register(leech, BuiltInEvents.MODIFY_DAMAGE, (event, ctx) -> {
            event.damage().add(ctx.level() * 1.5f);  // 每级 +1.5 伤害
        });

        // POST_ATTACK 携带触发策略(充能 >= 0.7 才触发,且按充能缩放等级)
        registrar.register(leech, BuiltInEvents.POST_ATTACK,
            (event, ctx) -> {
                event.attacker().heal(ctx.level() * 2.0F);
            },
            new TriggerPolicy(TriggerPolicy.Mode.THRESHOLD_SCALED, 0.7f));
    }
}

// 辅助方法:通过 ID 解析 Holder<Enchantment>
private static Holder<Enchantment> resolveEnchantment(
        HolderLookup.Provider registries, String id) {
    return registries.lookup(Registries.ENCHANTMENT)
        .flatMap(reg -> reg.get(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(id))))
        .orElse(null);
}
```

#### 9.2.3 各事件对象字段

**PostAttackEvent**(实现 `ChargeableEvent`):
- `level`(ServerLevel) / `attacker`(LivingEntity) / `target`(LivingEntity)
- `charge`(float, 0.0~1.0) - 攻击充能比例
- `isCritical`(boolean) - 是否为暴击
- `isSweep`(boolean) - 是否为横扫攻击

**EntityTickEvent**:
- `level` / `entity`(LivingEntity) / `tickCount`(int)
- 注意:此事件每 tick 触发(受 `entity_tick_interval` 节流,默认 20 tick),回调须轻量

**ProjectileHitEvent**(实现 `ChargeableEvent`):
- `level` / `attacker`(LivingEntity) / `target`(LivingEntity) / `weapon`(ItemStack)
- `isCritArrow`(boolean) - 是否为暴击箭
- `drawStrength`(float, 0.0~1.0) - 拉弓程度,由 `BowItemMixin` 捕获,非弓类默认 1.0f

**ModifyDamageEvent**:
- `level` / `attacker`(LivingEntity) / `target`(LivingEntity) / `source`(DamageSource)
- `originalDamage`(float, 不可变参考)
- `damage`(MutableFloat,可修改) - 通过 `event.damage().add(...)` 或 `multiply(...)` 修改

**PostHurtEvent**:
- `level` / `target`(LivingEntity) / `attacker`(LivingEntity,可能 null) / `source`
- `amount`(float,实际伤害) / `blockedDamage`(float) / `blocked`(boolean)
- 所有伤害类型均触发(包括环境伤害)

**PostKillEvent**:
- `level` / `killer`(LivingEntity) / `victim`(LivingEntity) / `source`(DamageSource)
- 仅当 `DamageSource.getEntity()` 为 LivingEntity 时触发

**ModifyBlockDropsEvent**:
- `level` / `player`(ServerPlayer) / `pos`(BlockPos) / `blockState`(BlockState)
- `tool`(ItemStack,可能为 EMPTY)
- `drops`(List<ItemStack>,可修改) - loot table 求值结果(含时运加成)
- `bonusXp`(MutableInt,可累积) - 通过 `addBonusXp(int)` 累加
- 方法:
  - `addBonusXp(int amount)` - 累加额外经验(>=0)
  - `transformDrops(UnaryOperator<ItemStack> transformer)` - 对所有掉落物应用转换,返回 EMPTY 表示删除

**PostBlockBreakEvent**(纯通知):
- `level` / `player` / `pos` / `blockState` / `tool`
- 注意:连锁挖矿类实现需自带递归标记,在回调里再破坏方块会重入本事件

**ItemUseEvent**(实现 `InteractionEnchantmentEvent`):
- `level` / `player`(ServerPlayer) / `hand`(InteractionHand) / `itemStack`(ItemStack)
- `resultHolder`(Mutable<InteractionResult>)
- 方法:`result()` / `setResult(InteractionResult)` - 设置非 PASS 中断原版行为

**BlockUseEvent**(实现 `InteractionEnchantmentEvent`):
- `level` / `player` / `hand` / `hitResult`(BlockHitResult) / `resultHolder`
- 方法:`result()` / `setResult(InteractionResult)`
- 注意:原版主副手会各触发一次

**EntityUseEvent**(实现 `InteractionEnchantmentEvent`):
- `level` / `player` / `hand` / `entity`(Entity) / `hitResult`(EntityHitResult) / `resultHolder`
- 方法:`result()` / `setResult(InteractionResult)`

#### 9.2.4 EnchantmentContext 上下文

每个 BuiltInEvents 回调收到 `(event, ctx)`,`com.enchantlib.event.EnchantmentContext` 是 record:

| 字段/方法 | 说明 |
|----------|------|
| `enchantment`(Holder<Enchantment>) | 附魔 Holder |
| `level`(int) | 当前附魔等级(>=1) |
| `itemStack`(ItemStack) | 携带该附魔的物品(不可变快照,修改需 `.copy()`) |
| `slot`(EquipmentSlot) | 物品所在装备槽位 |
| `enchantmentId()` | 返回附魔 ID 字符串,形如 "modid:name" |

#### 9.2.5 EnchantmentEventType 类型安全键

`com.enchantlib.event.EnchantmentEventType<E>`:

| 方法 | 说明 |
|------|------|
| `static create(Identifier id, Class<E> eventClass)` | 创建并注册事件类型 |
| `static create(Identifier id)` | 创建并注册(自动推断事件类) |
| `id()` | 事件类型标识 |
| `eventClass()` | 事件类 |
| `bit()` | 位掩码位(1 << index),用于全局短路 |
| `relevantSlots()` | 关注的装备槽位集合(默认所有) |
| `setRelevantSlots(EnumSet<EquipmentSlot>)` | 设置关注槽位(静态初始化阶段调用) |

每个事件类型在创建时分配唯一 bit 位(最多支持 32 个事件类型),用于 `EnchantmentEventDispatcher.hasCallbacks()` 全局位掩码短路。Mixins 在热路径入口检查此方法,若无任何回调则跳过昂贵的扫描,实现"未安装实现模组时零开销"。

#### 9.2.6 EnchantmentEventRegistrar 注册器

| 方法 | 说明 |
|------|------|
| `register(Holder<Enchantment>, EnchantmentEventType<E>, EnchantmentEventCallback<E>)` | 注册回调(默认 `TriggerPolicy.IGNORE` 策略) |
| `register(Holder<Enchantment>, EnchantmentEventType<E>, EnchantmentEventCallback<E>, TriggerPolicy)` | 注册回调(携带触发策略) |
| `registeredCount()` | 已注册回调总数 |

**误配检测**:对非 `ChargeableEvent` 事件类型传递非 `IGNORE` 策略将抛出 `IllegalArgumentException`,避免实现模组误以为策略会生效。非充能事件(ENTITY_TICK、POST_HURT、POST_KILL、MODIFY_DAMAGE、MODIFY_BLOCK_DROPS、POST_BLOCK_BREAK、ITEM_USE、BLOCK_USE、ENTITY_USE)无充能概念,策略无意义。

#### 9.2.7 ChargeableEvent 接口

`com.enchantlib.event.ChargeableEvent` 继承 `EnchantmentEvent`,标记可充能事件:

| 方法 | 说明 |
|------|------|
| `float charge()` | 返回充能比例(0.0~1.0,1.0 表示满充能) |

实现类:`PostAttackEvent`(charge 字段)、`ProjectileHitEvent`(drawStrength 字段)。供 `TriggerPolicy` 进行阈值门控和等级缩放。未实现此接口的事件类型在分发时忽略 `TriggerPolicy`,始终触发回调。

#### 9.2.8 InteractionEnchantmentEvent 接口

`com.enchantlib.event.InteractionEnchantmentEvent` 继承 `EnchantmentEvent`,标记交互类事件:

| 方法 | 说明 |
|------|------|
| `InteractionResult result()` | 获取当前交互结果(初始为 PASS) |
| `setResult(InteractionResult)` | 设置交互结果(非 PASS 中断后续分发) |

实现类:`ItemUseEvent`、`BlockUseEvent`、`EntityUseEvent`。

**取消语义**:多附魔回调时按注册顺序依次调用,第一个设置非 PASS 结果的回调生效并停止后续分发。若所有回调都返回 PASS,则事件整体返回 PASS(不干预原版行为)。

```java
registrar.register(enchantment, BuiltInEvents.ITEM_USE, (event, ctx) -> {
    if (ctx.level() >= 1) {
        // 释放技能
        event.setResult(InteractionResult.SUCCESS);  // 中断原版右键行为
    }
});
```

#### 9.2.9 异常隔离保证

单个附魔的事件回调抛出异常**不会影响**:

- 其他附魔的回调执行
- 原版逻辑的正常进行

异常会被 EnchantLib 捕获并记录到日志(开启调试模式可见详细信息)。

#### 9.2.10 SmeltingLookup 熔炼查询工具

`com.enchantlib.util.SmeltingLookup` 提供熔炼配方缓存查询,常配合 `MODIFY_BLOCK_DROPS` 实现自动烧炼:

| 方法 | 说明 |
|------|------|
| `static initialize(MinecraftServer)` | 在 SERVER_STARTED 时初始化,绑定 RecipeManager |
| `static invalidate()` | 清空缓存(数据包重载时调用) |
| `static smelt(ItemStack)` | 返回 `Optional<ItemStack>` 熔炼结果(数量始终为 1) |
| `static smeltOrOriginal(ItemStack)` | 返回熔炼结果或原物品(保留输入数量) |
| `static cacheSize()` | 缓存大小(调试用) |

自动烧炼示例:

```java
registrar.register(autoSmelt, BuiltInEvents.MODIFY_BLOCK_DROPS, (event, ctx) -> {
    event.transformDrops(SmeltingLookup::smeltOrOriginal);
    // 补充熔炼经验(可选)
    event.addBonusXp(ctx.level());
});
```

#### 9.2.11 EnchantmentScanner

`com.enchantlib.event.EnchantmentScanner` 是内部工具类,负责查询实体装备附魔,事件分发器内部使用,开发者通常无需直接调用。

### 9.3 EnchantLibEvents:全局事件

`com.enchantlib.event.EnchantLibEvents` 提供 2 个**全局事件**,不基于装备附魔扫描,适用于在任意实体上触发效果(无需实体装备特定附魔)或拦截原生机制。

#### 9.3.1 事件列表

| 事件 | 触发时机 | 回调签名 | 启用方式 |
|------|---------|---------|---------|
| `LIVING_ENTITY_TICK` | 每服务端 tick 对所有加载的 LivingEntity | `(LivingEntityTickEvent) -> void` | 必须**先调用** `EnchantLibEvents.enableLivingEntityTick()` 才会订阅 `ServerTickEvents` |
| `FOOD_REGEN` | 玩家自然回血前(`FoodData.tick` 内部 heal 调用前) | `(FoodRegenEvent) -> void` | 默认启用(由 `FoodDataMixin` + `LivingEntityHealMixin` 桥接) |

#### 9.3.2 LIVING_ENTITY_TICK

**事件对象**:`com.enchantlib.event.LivingEntityTickEvent`(record):

| 字段 | 说明 |
|------|------|
| `level`(ServerLevel) | 服务端世界 |
| `entity`(LivingEntity) | 触发 tick 的实体 |
| `tickCount`(int) | 服务端 tick 计数(`server.getTickCount()`) |

**懒挂载机制**:

```java
// 在 ModInitializer.onInitialize() 或 onRegisterEventCallbacks() 中
EnchantLibEvents.enableLivingEntityTick();  // 必须!否则回调不会触发

EnchantLibEvents.LIVING_ENTITY_TICK.register(event -> {
    LivingEntity entity = event.entity();
    // 检查 EntityCounter 标记,造成伤害等
});
```

- 默认 `livingEntityTickEnabled = false`,EnchantLib 不订阅 `ServerTickEvents.END_SERVER_TICK`,实现零开销
- 实现模组在注册回调前必须调用 `EnchantLibEvents.enableLivingEntityTick()`,EnchantLib 主类据此在 SERVER_STARTED 阶段订阅 ServerTickEvents
- 若未调用 `enableLivingEntityTick()` 而直接 `register()`,回调被加入 Fabric Event 回调列表但永远不会被调用

**性能注意**:

- 此事件每 tick 对所有加载的 LivingEntity 触发(实体多时开销大)
- 回调应保持轻量,建议用 `EntityCounter.get()` 等机制快速过滤无关实体(无标记立即 return)
- 实现模组负责自己的过滤逻辑,EnchantLib 不做装备附魔扫描

**典型用途**:

- 焚心类持续伤害附魔:玩家攻击时用 `EntityCounter.set()` 在目标身上设置标记,此事件每 tick 检查标记并造成伤害
- 视觉效果更新:跟踪目标身上的展示实体并更新位置/旋转
- 全局伪 tick 逻辑:不依赖具体装备附魔的周期性检查

**实体迭代安全**:

EnchantLib 在分发前对 `level.getAllEntities()` 做快照(`List<Entity> snapshot = new ArrayList<>()`),
并在迭代时检查 `entity.isRemoved()`,避免回调中 `addFreshEntity`(如掉落物)或实体移除导致的并发问题。
但实现模组仍应避免在回调中做结构性修改,优先用 `EntityCounter` 等惰性机制。

#### 9.3.3 FOOD_REGEN

**事件对象**:`com.enchantlib.event.FoodRegenEvent`(可变对象,非 record):

| 字段/方法 | 说明 |
|----------|------|
| `player()` | 触发自然回血的 ServerPlayer |
| `originalAmount()` | 原始回血量(MC 26.2 默认 1/80 = 0.0125 HP/tick,约每 4 秒 1 HP) |
| `setCancelled(boolean)` | 取消本次自然回血(默认 false) |
| `isCancelled()` | 是否取消 |

**触发条件**:

- 玩家饱食度足够(foodLevel >= 18)且可自然回血
- MC 原版 `FoodData.tick` 内部调用 `Player.heal(float)` 前
- 仅在服务端触发(自然回血只在服务端计算)

**实现机制**:

由于 Loom 1.17 + MC 26.2 运行时无 refmap,无法用 `@WrapOperation` 包裹 `FoodData.tick` 内部的 heal 调用。EnchantLib 采用**线程局部标志 + heal HEAD 注入**方案:

1. `FoodDataMixin` 在 `FoodData.tick` HEAD/RETURN 设置/清除 `FoodTickTracker` 线程局部标志
2. `LivingEntityHealMixin` 在 `LivingEntity.heal` HEAD 检查 `FoodTickTracker.isInFoodTick()`,仅当当前线程在 FoodData.tick 内时才触发 `FOOD_REGEN` 事件
3. 事件被取消则 `ci.cancel()` 跳过 heal 调用

**关键设计要点**:

- **仅取消自然回血**:药水治疗、金苹果、信标等主动恢复手段的 `heal` 调用不在 `FoodData.tick` 内,`FoodTickTracker.isInFoodTick()` 返回 false,不受影响
- **饱食度仍正常消耗**:`ci.cancel()` 仅跳过 heal 调用,`FoodData.tick` 的其他逻辑(饱食度消耗、饥饿效果等)正常执行
- **仅服务端**:自然回血只在服务端计算,客户端无需处理

**已知限制**(P2,低优先级):

`@At("RETURN")` 仅在正常返回时触发,若 `FoodData.tick` 抛出异常则 `exit()` 不执行,
`FoodTickTracker` 标志卡死为 true,直到下一次 tick 的 `enter()/exit()` 自愈。期间同线程的非自然回血 heal
可能被误触发 FOOD_REGEN。`FoodData.tick` 在原版极少抛异常,实际影响近零。彻底修复需先解决无 refmap
问题或升级编译期 MixinExtras 至 0.3.0+ 以使用 `@WrapMethod` try-finally。

**典型用途**:静默契约类附魔(压制自然回血,迫使玩家通过击杀回血):

```java
// 在 onRegisterEventCallbacks 中注册
EnchantLibEvents.FOOD_REGEN.register(event -> {
    ServerPlayer player = event.player();
    ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
    if (chest.getEnchantments().getLevel(silentPactHolder) > 0) {
        event.setCancelled(true);  // 胸甲持 silent_pact 时取消自然回血
    }
});
```

#### 9.3.4 EnchantLibEvents API 完整列表

| 成员 | 类型 | 说明 |
|------|------|------|
| `LIVING_ENTITY_TICK` | `Event<LivingEntityTickCallback>` | 全局 LivingEntity tick 事件 |
| `FOOD_REGEN` | `Event<FoodRegenCallback>` | 玩家自然回血事件 |
| `enableLivingEntityTick()` | `static void` | 启用 LIVING_ENTITY_TICK 遍历分发(必须调用) |
| `isLivingEntityTickEnabled()` | `static boolean` | 查询 LIVING_ENTITY_TICK 是否已启用 |

**回调接口**:

```java
@FunctionalInterface
public interface LivingEntityTickCallback {
    void onTick(LivingEntityTickEvent event);
}

@FunctionalInterface
public interface FoodRegenCallback {
    void onRegen(FoodRegenEvent event);
}
```

#### 9.3.5 FoodTickTracker 工具类

`com.enchantlib.event.FoodTickTracker` 是内部线程局部标志工具,配合 `FoodDataMixin` + `LivingEntityHealMixin` 实现"仅在自然回血时触发 FOOD_REGEN"。开发者通常无需直接调用,但理解其机制有助于排查问题:

| 方法 | 说明 |
|------|------|
| `enter()` | 标记当前线程进入 `FoodData.tick`(由 `FoodDataMixin` HEAD 调用) |
| `exit()` | 标记当前线程退出 `FoodData.tick`(由 `FoodDataMixin` RETURN 调用) |
| `isInFoodTick()` | 检查当前线程是否正在执行 `FoodData.tick`(由 `LivingEntityHealMixin` heal HEAD 调用) |

**为何独立为工具类**:Mixin 不允许非 private static 方法(会被注入到目标类 `FoodData`,而 `FoodData` 不应有 `isInFoodTick` 方法),因此独立为 `FoodTickTracker` 工具类。

#### 9.3.6 异常隔离说明

与 BuiltInEvents 不同,EnchantLibEvents 基于 Fabric API `EventFactory.createArrayBacked`,**单回调异常会中断后续回调**。实现模组应在回调内自行 try-catch,避免异常影响其他模组的回调:

```java
EnchantLibEvents.LIVING_ENTITY_TICK.register(event -> {
    try {
        // 自身逻辑
    } catch (Throwable t) {
        LOGGER.error("MyMod LIVING_ENTITY_TICK 回调异常", t);
    }
});
```

---

## 10. 触发策略 TriggerPolicy

`com.enchantlib.event.TriggerPolicy` 控制附魔回调在充能事件(`POST_ATTACK`、`PROJECTILE_HIT`)中是否触发,以及效果强度是否随充能比例缩放。用于防止低充能刷刀全额触发附魔效果,平衡生存服体验。

### 10.1 四种模式

| 模式 | 说明 |
|------|------|
| `IGNORE` | 忽略充能,任何充能都全额触发(默认行为,向后兼容) |
| `THRESHOLD` | 充能阈值门控,`charge < threshold` 时不触发 |
| `SCALED` | 按充能比例缩放效果等级,`scaledLevel = max(1, round(level * charge))` |
| `THRESHOLD_SCALED` | 阈值门控 + 缩放(先过阈值再缩放) |

### 10.2 TriggerPolicy API

| 方法/常量 | 说明 |
|----------|------|
| `IGNORE` | 静态常量,默认策略 |
| `TriggerPolicy(Mode mode, float threshold)` | 构造,threshold 0.0~1.0,仅 THRESHOLD/THRESHOLD_SCALED 生效 |
| `mode()` | 获取模式 |
| `threshold()` | 获取阈值(0.0~1.0) |
| `shouldTrigger(float charge)` | 判断当前充能是否应触发回调 |
| `scaleLevel(int level, float charge)` | 按策略缩放附魔等级,仅 SCALED/THRESHOLD_SCALED 生效,返回值至少为 1 |

### 10.3 使用示例

```java
// 注册时携带触发策略
registrar.register(leech, BuiltInEvents.POST_ATTACK,
    (event, ctx) -> {
        // ctx.level() 已被策略缩放(若使用 SCALED/THRESHOLD_SCALED)
        event.attacker().heal(ctx.level() * 2.0F);
    },
    new TriggerPolicy(TriggerPolicy.Mode.THRESHOLD_SCALED, 0.7f));
```

### 10.4 作用范围

仅对实现 `ChargeableEvent` 接口的 BuiltInEvents 生效(`POST_ATTACK`、`PROJECTILE_HIT`)。其他 BuiltInEvents(ENTITY_TICK、POST_HURT、POST_KILL、MODIFY_DAMAGE、MODIFY_BLOCK_DROPS、POST_BLOCK_BREAK、ITEM_USE、BLOCK_USE、ENTITY_USE)忽略策略,始终触发。EnchantLibEvents 全局事件无充能概念,不适用 TriggerPolicy。

### 10.5 配置覆盖

注册时携带默认 policy,`config/enchantlib/trigger.toml` 可按附魔 ID 覆盖此处设置的策略(见第 14 节)。

---

## 11. EntityCategory 玩家分类 API

`com.enchantlib.api.EntityCategory` 允许将玩家标记为某种生物分类(亡灵/节肢动物/灾厄村民/水生生物),使服务端在 Mob 目标选择等场景中将玩家视为该分类。

### 11.1 设计理念

- **不覆盖 `getType()`**,只在 `MobMixin` 拦截 `setTarget` 时查询分类标签
- 不影响存档、掉落物、统计、成就等逻辑
- 非玩家实体使用原版 `EntityTypeTags` 标签查询,自动支持其他模组通过标签注册的同类生物
- 玩家离线时由 `EntityTickHandler.onPlayerLeave` 自动清理

### 11.2 分类枚举

`EntityCategory.Category`:

| 分类 | 对应原版标签 | 典型生物 |
|------|-------------|---------|
| `UNDEAD` | `EntityTypeTags.UNDEAD` | 僵尸、骷髅、凋灵、幻翼 |
| `ARTHROPOD` | `EntityTypeTags.ARTHROPOD` | 蜘蛛、洞穴蜘蛛、蠹虫、蜜蜂 |
| `ILLAGER` | `EntityTypeTags.ILLAGER` | 掠夺者、唤魔者、卫道士、女巫 |
| `AQUATIC` | `EntityTypeTags.AQUATIC` | 守卫者、鱿鱼、海豚 |

每个分类的 `tag()` 方法返回对应的 `TagKey<EntityType<?>>`。

### 11.3 API 列表

| 方法 | 说明 |
|------|------|
| `set(ServerPlayer, Category)` | 覆盖设置单个分类 |
| `set(ServerPlayer, Category...)` | 覆盖设置多个分类 |
| `add(ServerPlayer, Category)` | 添加分类(不影响其他) |
| `remove(ServerPlayer, Category)` | 移除分类(空时自动清除) |
| `clear(ServerPlayer)` | 清除所有分类 |
| `has(ServerPlayer, Category)` | 查询玩家是否被标记 |
| `get(ServerPlayer)` | 返回 `Set<Category>`(不可变) |
| `isCategory(Entity, Category)` | 通用查询(玩家查标记,非玩家查标签) |
| `isUndead(Entity)` / `isArthropod(Entity)` / `isIllager(Entity)` / `isAquatic(Entity)` | 便捷查询 |

### 11.4 使用示例

```java
import com.enchantlib.api.EntityCategory;

// 在 LIVING_ENTITY_TICK 回调中将玩家标记为亡灵(伪装类附魔)
EnchantLibEvents.LIVING_ENTITY_TICK.register(event -> {
    if (!(event.entity() instanceof ServerPlayer player)) {
        return;
    }
    ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
    if (helmet.getEnchantments().getLevel(undeadDisguiseHolder) > 0) {
        // 标记为亡灵:亡灵生物不再锁定玩家(MobMixin 从源头拦截 setTarget)
        EntityCategory.set(player, EntityCategory.Category.UNDEAD);
    } else {
        EntityCategory.clear(player);
    }
});

// 通用查询:任意实体(包括玩家)是否属于某分类
if (EntityCategory.isUndead(target)) {
    event.damage().setValue(0);  // 亡灵契约:不伤害亡灵
}
```

> **注意**:玩家卸下附魔装备或离线时,应调用 `clear(player)` 清理标记。EnchantLib 的 `EntityTickHandler` 已在玩家离线时自动清理。

> **为何用 LIVING_ENTITY_TICK 而非 BuiltInEvents.ENTITY_TICK**:`ENTITY_TICK` 扫描实体装备附魔并按附魔触发回调,但伪装附魔需要每 tick 检查头盔并维护分类标记(包括"未戴伪装头盔时清除分类"),逻辑上不绑定到具体附魔触发,适合用全局事件实现。

---

## 12. EntityCounter 实体计数器 API

`com.enchantlib.api.EntityCounter` 提供线程安全的、命名空间隔离的计数器存储,供子 Mod 在事件回调中使用。

### 12.1 设计要点

- **命名空间隔离**:用 `Identifier` 作 key,建议用 Mod ID 作命名空间,避免冲突
- **线程安全**:`ConcurrentHashMap` + `AtomicInteger`,可在多线程事件回调中直接使用
- **懒加载**:首次操作自动创建计数器,无需预注册
- **自动清理**:玩家离线时 `EntityTickHandler.onPlayerLeave` 自动调用 `clear(entity)`
- **非持久化**:服务器重启后计数器重置(不写入存档)

### 12.2 API 列表

| 方法 | 说明 |
|------|------|
| `get(LivingEntity, Identifier)` | 获取当前值,未初始化为 0 |
| `set(LivingEntity, Identifier, int)` | 设置值 |
| `increment(LivingEntity, Identifier)` | 自增 1,返回新值 |
| `addAndGet(LivingEntity, Identifier, int delta)` | 增加指定值,返回新值(delta 可为负) |
| `reset(LivingEntity, Identifier)` | 重置为 0 |
| `checkAndReset(LivingEntity, Identifier, int threshold)` | 达到阈值则重置返回 true(常用于"每 N 次触发一次") |
| `clear(LivingEntity)` | 清除所有计数器(玩家离线自动调用) |
| `keys(LivingEntity)` | 返回 `Set<Identifier>`(不可变,调试用) |

### 12.3 使用示例

```java
import com.enchantlib.api.EntityCounter;
import net.minecraft.resources.Identifier;

// 定义计数器 key(用 Mod ID 作为命名空间)
private static final Identifier ATTACK_COUNT = Identifier.of("mymod", "attack_count");
private static final Identifier DAMAGE_DEALT = Identifier.of("mymod", "damage_dealt");

// POST_ATTACK 回调中累计攻击次数和伤害
public static void onAttack(BuiltInEvents.PostAttackEvent event, EnchantmentContext ctx) {
    LivingEntity attacker = event.attacker();
    EntityCounter.increment(attacker, ATTACK_COUNT);
    EntityCounter.addAndGet(attacker, DAMAGE_DEALT, (int) event.charge() * 10);

    // 每 5 次攻击触发特效
    if (EntityCounter.checkAndReset(attacker, ATTACK_COUNT, 5)) {
        attacker.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 100, 0));
    }
}
```

### 12.4 与 LIVING_ENTITY_TICK 的典型配合

`EntityCounter` 常用于在 BuiltInEvents 回调中设置标记,在 LIVING_ENTITY_TICK 回调中查询并消费:

```java
// POST_ATTACK 中给目标设置焚心标记(存结束 tick + 等级)
registrar.register(heartburn, BuiltInEvents.POST_ATTACK, (event, ctx) -> {
    LivingEntity target = event.target();
    int currentTick = event.level().getServer().getTickCount();
    EntityCounter.set(target, HEARTBURN_END_TICK, currentTick + 40 + 20 * ctx.level());
    EntityCounter.set(target, HEARTBURN_LEVEL, ctx.level());
});

// LIVING_ENTITY_TICK 中每 tick 检查标记,每 20 tick 造成一次伤害
EnchantLibEvents.LIVING_ENTITY_TICK.register(event -> {
    LivingEntity entity = event.entity();
    int endTick = EntityCounter.get(entity, HEARTBURN_END_TICK);
    if (endTick <= 0) {
        return;  // 快速过滤无标记的实体
    }
    int currentTick = event.tickCount();
    if (currentTick >= endTick) {
        EntityCounter.set(entity, HEARTBURN_END_TICK, 0);
        // 清除其他相关计数器...
        return;
    }
    // 造成伤害...
});
```

### 12.5 支持的计数场景

- 攻击计数(POST_ATTACK 中 increment)
- 受击计数(POST_HURT 中 increment)
- 累计造成伤害(MODIFY_DAMAGE/POST_ATTACK 中 add)
- 累计受到伤害(POST_HURT 中 add)
- 击杀计数(POST_KILL 中 increment)
- 持续效果标记(LIVING_ENTITY_TICK 中查询/消费)
- 自定义计数(任何事件中都可使用)

---

## 13. 配置文件定义附魔

除代码注册外,管理员可通过 TOML 配置文件定义附魔(无需改代码)。`com.enchantlib.config.ConfigLoader` 扫描 `config/enchantlib/enchantments/` 目录,每个文件定义一个附魔。

### 13.1 配置文件格式

```toml
# 必填字段
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

# 可选字段
primary_items = "#minecraft:enchantable/melee_weapon"
exclusive_set = "#minecraft:exclusive_set/damage"

# 条件加载(可选)
[condition]
mod_loaded = "sophisticatedbackpacks"    # 仅当该 mod 加载时
# mod_not_loaded = "jei"                  # 仅当该 mod 未加载时

# 获取途径开关(可选,仅配置文件定义的附魔生效)
[acquisition]
loot = true    # 自动注册战利品注入
trade = true   # 自动加入 #minecraft:tradeable 标签

# 效果(可选,便捷格式)
# effect_key 用下划线替换冒号:minecraft_damage → minecraft:damage
# 自动生成 minecraft:add + minecraft:linear 结构
[[effects.minecraft_damage]]
base = 1.0
per_level_above_first = 0.5
```

### 13.2 字段说明

| 字段 | 必填 | 说明 |
|------|------|------|
| `id` | 是 | 附魔 ID,格式 "modid:name" |
| `description` | 是 | 兜底显示文本 |
| `supported_items` | 是 | 支持物品(标签或物品 ID) |
| `weight` | 是 | 权重 1~15 |
| `max_level` | 是 | 最大等级 |
| `anvil_cost` | 是 | 铁砧成本 |
| `[min_cost]` | 是 | 最小成本(base + per_level_above_first) |
| `[max_cost]` | 是 | 最大成本 |
| `slots` | 是 | 生效槽位数组 |
| `primary_items` | 否 | 主要物品 |
| `exclusive_set` | 否 | 互斥组标签引用 |
| `[condition]` | 否 | 条件加载(mod_loaded / mod_not_loaded) |
| `[acquisition]` | 否 | 获取途径开关(loot / trade) |
| `[[effects.<effect_key>]]` | 否 | 效果,下划线替换冒号 |

### 13.3 支持的便捷 effect 类型

- `minecraft_damage` → `minecraft:damage`
- `minecraft_knockback` → `minecraft:knockback`
- `minecraft_damage_protection` → `minecraft:damage_protection`
- `minecraft_post_attack_ignite` → `minecraft:post_attack` + `minecraft:ignite`

---

## 14. 触发策略配置 trigger.toml

`config/enchantlib/trigger.toml` 由 `com.enchantlib.config.TriggerPolicyConfig` 加载,可按附魔 ID 覆盖代码注册的 `TriggerPolicy`。

### 14.1 配置格式

```toml
# 全局默认阈值(THRESHOLD/THRESHOLD_SCALED 模式未配置附魔时使用)
# 范围 0.0~1.0
force_threshold_min = 0.0

# 附魔级覆盖:键为附魔 ID(modid:name),含冒号必须用引号包裹
["mymod:my_enchant"]
mode = "THRESHOLD"      # IGNORE / THRESHOLD / SCALED / THRESHOLD_SCALED
threshold = 0.7
```

### 14.2 字段说明

| 字段 | 说明 |
|------|------|
| `force_threshold_min` | 全局默认阈值,THRESHOLD/THRESHOLD_SCALED 模式未配置附魔时使用 |
| `["<enchantment_id>"]` | 附魔级覆盖节,键为附魔 ID(含冒号必须用引号包裹) |
| `mode` | 触发模式:`IGNORE` / `THRESHOLD` / `SCALED` / `THRESHOLD_SCALED` |
| `threshold` | 阈值 0.0~1.0 |

### 14.3 优先级

代码注册的 policy → `trigger.toml` 附魔级覆盖 → `trigger.toml` 全局默认阈值。配置文件优先级最高,可在不改代码的情况下调整附魔触发行为。

### 14.4 acquisition.toml 配置

`config/enchantlib/acquisition.toml` 由 `com.enchantlib.config.AcquisitionConfig` 加载:

```toml
loot_injection_enabled = true        # 战利品注入总开关
villager_trade_enabled = true        # 村民交易总开关
resource_distribution_enabled = true # 资源分发总开关
http_server_port = 8765              # 本地监听端口(服务端绑定用,不进对外 URL)
http_server_host = ""                # 对外完整网址(可含端口;留空自动探测,仅局域网可用)
debug_enabled = false                # 调试开关
entity_tick_interval = 20            # ENTITY_TICK 触发间隔(默认 20 tick = 1 秒)
```

`entity_tick_interval` 控制 `BuiltInEvents.ENTITY_TICK` 事件的触发频率。由于此事件每 tick 对每个 LivingEntity 触发,默认 20 tick 一次以避免性能问题。EnchantLib 使用实体 ID 相位偏移避免所有实体在同一 tick 触发的尖峰。

> 注:此项**仅控制** `BuiltInEvents.ENTITY_TICK`(基于装备附魔扫描)。`EnchantLibEvents.LIVING_ENTITY_TICK` 每 tick 触发,不受此配置控制(其懒挂载特性保证无回调时零开销)。

---

## 15. 客户端资源分发

EnchantLib 是纯服务端模组,但附魔的本地化名称需要客户端资源包支持。

### 15.1 提供客户端资源

在你的模组 `assets/<modid>/enchant_sync/` 目录下放置需要分发的客户端资源:

```
assets/mymod/enchant_sync/
├── lang/                    # 语言文件(跨模组合并)
│   ├── en_us.json
│   └── zh_cn.json
├── textures/                # 纹理(保留模组命名空间)
│   └── item/custom_book.png
├── particles/               # 粒子定义
│   └── custom_effect.json
└── sounds/                  # 音效文件
    └── enchant_trigger.ogg
```

#### 语言文件

格式与原版 `lang/*.json` 一致:

```json
{
  "enchantment.mymod.leech": "Leech",
  "enchantment.mymod.fire_aspect": "Fire Aspect"
}
```

语言文件会被**跨模组合并**到 `assets/minecraft/lang/<code>.json`,所有模组的翻译键合并在同一文件中。

#### 其他资源(纹理/粒子/声音等)

其他资源**保留模组命名空间**,路径映射规则:

| 源路径 | 资源包内路径 |
|--------|------------|
| `assets/mymod/enchant_sync/textures/item/custom.png` | `assets/mymod/textures/item/custom.png` |
| `assets/mymod/enchant_sync/particles/effect.json` | `assets/mymod/particles/effect.json` |
| `assets/mymod/enchant_sync/sounds/trigger.ogg` | `assets/mymod/sounds/trigger.ogg` |

即:`enchant_sync/` 前缀被去除,其余路径保持不变。

### 15.2 自动合并与推送工作流程

EnchantLib 在服务端启动时:

1. `EnchantSyncScanner` 扫描所有模组的 `assets/<modid>/enchant_sync/` 目录
2. `LanguageMerger` 按语言代码合并所有语言文件
3. `ClientResourcePackBuilder` 构建运行时资源包(ZIP)
4. `ResourcePackHttpServer` 启动内置 HTTP 服务器(默认端口 8765)
5. 玩家加入时通过 `ClientboundResourcePackPushPacket` 自动推送资源包

**全局开关**:`resource_distribution_enabled = false` 可禁用整个资源分发系统。

### 15.3 对外 URL 配置(公网部署关键)

HTTP 服务器监听 `0.0.0.0:http_server_port`(所有网卡的**本地端口**),但**推送给客户端的下载 URL**由 `http_server_host` 决定。两者分离:

- `http_server_port`(默认 8765)= **本地监听端口**,仅服务端绑定用,**不进对外 URL**
- `http_server_host` = **对外完整网址**,可含端口;对外端口由此处 host 决定

常见配置方式:

- **走 80/反代**:`http_server_host = "play.example.com"`(不带端口,URL 为 `http://play.example.com/enchantlib-resourcepack.zip`)
- **直连非 80 端口**:`http_server_host = "play.example.com:8080"`(端口写在 host 里)
- **配置公网 IP**:`http_server_host = "203.0.113.5"` 或 `"203.0.113.5:8080"`
- **留空**:自动探测本机 IP,URL 拼本地端口(仅局域网可用)

> 公网部署时**必须**配置 `http_server_host`,否则客户端收到的下载 URL 指向局域网地址,无法下载资源包。修改后需**重启服务端**(`reload` 不重启 HTTP 服务器)。

---

## 16. 调试

### 16.1 DebugLogger API

`com.enchantlib.debug.DebugLogger` 提供统一的调试日志输出口:

| 方法 | 说明 |
|------|------|
| `setEnabled(boolean)` | 设置调试开关(由 EnchantLib.onInitialize 和 `/enchantlib debug toggle` 调用) |
| `isEnabled()` | 查询调试开关状态 |
| `log(String format, Object... args)` | 输出调试日志(SLF4J 占位符格式 `{}`) |
| `log(String message)` | 输出调试日志(纯文本) |

输出前缀:`[EnchantLib-Debug]`。仅当调试开关开启时输出,否则为空操作。

> 注:`DebugLogger` 仅覆盖 EnchantLib 自身的 BuiltInEvents 分发路径。EnchantLibEvents 全局事件的启用状态由 EnchantLib 主类在启动日志中输出(不通过 DebugLogger)。

### 16.2 开启调试模式

三种方式:

- 配置文件:`config/enchantlib/acquisition.toml` 中 `debug_enabled = true`
- 运行时指令:`/enchantlib debug toggle`(不持久化,重启后回到配置值)
- 重载配置:`/enchantlib reload`

### 16.3 调试输出内容

开启后,关键路径输出详细日志:

- 附魔注册(ID、等级、权重、互斥组)
- 事件分发(事件类型、回调数、异常隔离)— 仅 BuiltInEvents
- 战利品注入(规则数、目标表)
- 村民交易(交易 ID、职业、层级)

### 16.4 调试指令

```bash
/enchantlib debug status              # 查看调试状态与系统统计
/enchantlib debug toggle              # 切换运行时调试开关
/enchantlib debug info <enchantment>  # 查看指定附魔的详细信息
```

### 16.5 EnchantmentValidator 启动校验

`com.enchantlib.validation.EnchantmentValidator` 在附魔和互斥组收集完成后、数据包注入前执行全局校验。采用 **fail-fast** 策略:检测到任何异常将抛出 `IllegalStateException`,使服务端启动失败。

校验项:

- 必填字段检查(description/supportedItems/weight/maxLevel/anvilCost/slots)
- 范围校验(weight 1-15,maxLevel >=1,anvilCost >=1,minCost/maxCost base>=1 perLevel>=1)
- 互斥组引用校验(自定义互斥组必须存在)
- ID 重复检测
- 互斥组成员冲突(同一附魔不能在多个互斥组)

所有校验错误会先通过 `LOGGER.error` 输出详细日志,再抛出异常。

---

## 17. API 速查

### 17.1 核心类(`com.enchantlib.api`)

| 类 | 用途 |
|----|------|
| `EnchantmentEntrypoint` | 入口接口,5 个方法注册附魔/互斥组/战利品/交易/事件回调 |
| `EnchantmentBuilder` | 链式构建附魔定义 |
| `EnchantmentEffectsBuilder` | 构建附魔效果 |
| `EnchantmentRegistrar` | 附魔注册器 |
| `ExclusiveGroupBuilder` | 构建互斥组 |
| `ExclusiveGroupRegistrar` | 互斥组注册器 |
| `ExclusiveSets` | 原版互斥组常量(7 组) |
| `LootInjectionBuilder` | 构建战利品注入规则 |
| `LootInjection` | 不可变的战利品注入规则 |
| `LootInjectionRegistrar` | 战利品注入注册器 |
| `LootTables` | 原版战利品表 ID 常量 |
| `VillagerTradeBuilder` | 构建村民交易 |
| `VillagerTradeInjection` | 不可变的村民交易规则 |
| `VillagerTradeRegistrar` | 村民交易注册器 |
| `VillagerTrades` | 原版村民职业常量(15 个职业 + 5 个等级) |
| `TradeableEnchantmentsBuilder` | 声明可交易附魔 |
| `EntityCategory` | 玩家生物分类 API(亡灵/节肢/灾厄/水生) |
| `EntityCounter` | 实体计数器 API(命名空间隔离、线程安全) |

### 17.2 事件类(`com.enchantlib.event`)

#### BuiltInEvents 子系统(基于装备附魔扫描)

| 类 | 用途 |
|----|------|
| `BuiltInEvents` | 内置事件类型常量(11 种)与事件 record 类 |
| `EnchantmentEvent` | 事件接口(基类) |
| `EnchantmentContext` | BuiltInEvents 事件上下文(enchantment/level/itemStack/slot) |
| `EnchantmentEventType<E>` | 事件类型(类型安全键,位掩码短路) |
| `EnchantmentEventCallback<E>` | 事件回调函数式接口 |
| `EnchantmentEventRegistrar` | BuiltInEvents 事件回调注册器 |
| `EnchantmentEventDispatcher` | 事件分发器(内部使用) |
| `EnchantmentScanner` | 装备附魔扫描器(内部使用) |
| `ChargeableEvent` | 可充能事件标记接口(`charge()`),供 TriggerPolicy 使用 |
| `InteractionEnchantmentEvent` | 交互类事件标记接口(`result()`/`setResult()`) |
| `TriggerPolicy` | 触发策略(4 种模式,仅对 ChargeableEvent 生效) |
| `EntityTickHandler` | BuiltInEvents.ENTITY_TICK 节流与玩家离线清理(内部使用) |
| `DrawStrengthHolder` | 拉弓程度捕获(内部使用,供 PROJECTILE_HIT) |

#### EnchantLibEvents 子系统(全局事件)

| 类 | 用途 |
|----|------|
| `EnchantLibEvents` | 全局事件入口(`LIVING_ENTITY_TICK` / `FOOD_REGEN` + `enableLivingEntityTick()`) |
| `LivingEntityTickEvent` | LIVING_ENTITY_TICK 事件 record(level/entity/tickCount) |
| `FoodRegenEvent` | FOOD_REGEN 事件(player/originalAmount/setCancelled) |
| `FoodTickTracker` | FoodData.tick 线程局部标志(内部使用,FoodDataMixin + LivingEntityHealMixin 桥接) |

### 17.3 工具类

| 类 | 包 | 用途 |
|----|----|------|
| `SmeltingLookup` | `com.enchantlib.util` | 熔炼配方缓存查询 |
| `DebugLogger` | `com.enchantlib.debug` | 调试日志器 |
| `EnchantmentValidator` | `com.enchantlib.validation` | 启动校验管线(fail-fast) |

### 17.4 配置类(`com.enchantlib.config`)

| 类 | 用途 |
|----|------|
| `AcquisitionConfig` | 加载 `acquisition.toml`(全局开关) |
| `TriggerPolicyConfig` | 加载 `trigger.toml`(触发策略覆盖) |
| `ConfigLoader` | 加载 `enchantments/*.toml`(配置文件定义附魔) |

### 17.5 包路径总览

```
com.enchantlib.api            # 对外 API(开发者使用)
com.enchantlib.event          # 事件系统(BuiltInEvents 11 种 + EnchantLibEvents 2 种 + TriggerPolicy)
com.enchantlib.command        # 指令系统(/enchantlib)
com.enchantlib.config         # 配置系统(acquisition/trigger/enchantments)
com.enchantlib.debug          # 调试工具(DebugLogger)
com.enchantlib.resources      # 资源分发(扫描/合并/HTTP 推送)
com.enchantlib.datapack       # 运行时数据包注入
com.enchantlib.validation     # 校验管线(fail-fast)
com.enchantlib.loot           # 战利品注入处理
com.enchantlib.mixin          # Mixin 注入(10 个 Mixin)
com.enchantlib.util           # 工具类(SmeltingLookup)
```

### 17.6 Mixin 列表

| Mixin | 注入点 | 关联事件/功能 |
|-------|-------|---------|
| `PlayerAttackMixin` | `Player.attack` RETURN | BuiltInEvents.POST_ATTACK |
| `LivingEntityHurtMixin` | `LivingEntity.hurtServer` HEAD | BuiltInEvents.MODIFY_DAMAGE |
| `ProjectileWeaponMixin` | `Projectile.onHitEntity` RETURN | BuiltInEvents.PROJECTILE_HIT |
| `BowItemMixin` | 拉弓程度捕获 | BuiltInEvents.PROJECTILE_HIT (drawStrength) |
| `BlockDropResourcesMixin` | `Block.dropResources` | BuiltInEvents.MODIFY_BLOCK_DROPS |
| `MobMixin` | `Mob.setTarget` 拦截 | EntityCategory 玩家分类 |
| `ProjectileMixin` | 弹射物辅助 | BuiltInEvents.PROJECTILE_HIT |
| `BuiltInPackSourceMixin` | 运行时数据包注入 | - |
| `FoodDataMixin` | `FoodData.tick` HEAD/RETURN | EnchantLibEvents.FOOD_REGEN(设置 FoodTickTracker 标志) |
| `LivingEntityHealMixin` | `LivingEntity.heal` HEAD | EnchantLibEvents.FOOD_REGEN(检查标志并触发事件) |

> 完整 Javadoc 见源码 `src/main/java/com/enchantlib/` 目录下各类。
