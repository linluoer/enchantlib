package com.enchantlib.api;

/**
 * 原版战利品表 ID 常量。
 *
 * <p>提供 MC 26.2 所有原版战利品表的 ID 字符串常量，可直接传入
 * {@link LootInjectionBuilder#toTables(String...)} 指定注入目标。</p>
 *
 * <h2>分类</h2>
 * <ul>
 *   <li><b>箱子战利品</b>（chests/*）：地下城、矿道、要塞、林地府邸等</li>
 *   <li><b>钓鱼战利品</b>（gameplay/fishing/*）：垃圾、宝藏、鱼</li>
 *   <li><b>实体战利品</b>（entities/*）：生物掉落</li>
 *   <li><b>玩法战利品</b>（gameplay/*）：猫的晨礼、村民赠礼等</li>
 *   <li><b>考古战利品</b>（archaeology/*）：古迹废墟、沙漠水井等</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 注入到所有简单地下城箱子
 * LootInjectionBuilder.create()
 *     .toTables(LootTables.SIMPLE_DUNGEON)
 *     .asBook()
 *     .withEnchantments("mymod:my_enchant")
 *     .chance(0.5F);
 *
 * // 注入到多种箱子
 * LootInjectionBuilder.create()
 *     .toTables(LootTables.SIMPLE_DUNGEON, LootTables.ABANDONED_MINESHAFT, LootTables.DESERT_PYRAMID)
 *     .asBook()
 *     .withEnchantments("mymod:my_enchant")
 *     .chance(0.3F);
 * }</pre>
 *
 * <p>常量值来源：{@code net.minecraft.world.level.storage.loot.BuiltInLootTables}</p>
 *
 * @since 0.1.0
 */
public final class LootTables {

	private LootTables() {
	}

	// ===== 箱子战利品（chests/*） =====

	/** 出生奖励箱 */
	public static final String SPAWN_BONUS_CHEST = "minecraft:chests/spawn_bonus_chest";
	/** 末地城宝藏 */
	public static final String END_CITY_TREASURE = "minecraft:chests/end_city_treasure";
	/** 简单地下城 */
	public static final String SIMPLE_DUNGEON = "minecraft:chests/simple_dungeon";
	/** 村庄武器匠 */
	public static final String VILLAGE_WEAPONSMITH = "minecraft:chests/village/village_weaponsmith";
	/** 村庄工具匠 */
	public static final String VILLAGE_TOOLSMITH = "minecraft:chests/village/village_toolsmith";
	/** 村村庄甲匠 */
	public static final String VILLAGE_ARMORER = "minecraft:chests/village/village_armorer";
	/** 村庄制图师 */
	public static final String VILLAGE_CARTOGRAPHER = "minecraft:chests/village/village_cartographer";
	/** 村庄石匠 */
	public static final String VILLAGE_MASON = "minecraft:chests/village/village_mason";
	/** 村庄牧羊人 */
	public static final String VILLAGE_SHEPHERD = "minecraft:chests/village/village_shepherd";
	/** 村庄屠夫 */
	public static final String VILLAGE_BUTCHER = "minecraft:chests/village/village_butcher";
	/** 村庄制箭师 */
	public static final String VILLAGE_FLETCHER = "minecraft:chests/village/village_fletcher";
	/** 村庄渔夫 */
	public static final String VILLAGE_FISHER = "minecraft:chests/village/village_fisher";
	/** 村庄制革师 */
	public static final String VILLAGE_TANNERY = "minecraft:chests/village/village_tannery";
	/** 村庄神庙 */
	public static final String VILLAGE_TEMPLE = "minecraft:chests/village/village_temple";
	/** 沙漠村庄房屋 */
	public static final String VILLAGE_DESERT_HOUSE = "minecraft:chests/village/village_desert_house";
	/** 平原村庄房屋 */
	public static final String VILLAGE_PLAINS_HOUSE = "minecraft:chests/village/village_plains_house";
	/** 针叶林村庄房屋 */
	public static final String VILLAGE_TAIGA_HOUSE = "minecraft:chests/village/village_taiga_house";
	/** 雪原村庄房屋 */
	public static final String VILLAGE_SNOWY_HOUSE = "minecraft:chests/village/village_snowy_house";
	/** 稀树草原村庄房屋 */
	public static final String VILLAGE_SAVANNA_HOUSE = "minecraft:chests/village/village_savanna_house";
	/** 废弃矿道 */
	public static final String ABANDONED_MINESHAFT = "minecraft:chests/abandoned_mineshaft";
	/** 下界要塞 */
	public static final String NETHER_BRIDGE = "minecraft:chests/nether_bridge";
	/** 要塞图书馆 */
	public static final String STRONGHOLD_LIBRARY = "minecraft:chests/stronghold_library";
	/** 要塞十字路口 */
	public static final String STRONGHOLD_CROSSING = "minecraft:chests/stronghold_crossing";
	/** 要塞走廊 */
	public static final String STRONGHOLD_CORRIDOR = "minecraft:chests/stronghold_corridor";
	/** 沙漠神殿 */
	public static final String DESERT_PYRAMID = "minecraft:chests/desert_pyramid";
	/** 丛林神庙 */
	public static final String JUNGLE_TEMPLE = "minecraft:chests/jungle_temple";
	/** 雪屋箱子 */
	public static final String IGLOO_CHEST = "minecraft:chests/igloo_chest";
	/** 林地府邸 */
	public static final String WOODLAND_MANSION = "minecraft:chests/woodland_mansion";
	/** 小型水下废墟 */
	public static final String UNDERWATER_RUIN_SMALL = "minecraft:chests/underwater_ruin_small";
	/** 大型水下废墟 */
	public static final String UNDERWATER_RUIN_BIG = "minecraft:chests/underwater_ruin_big";
	/** 埋藏宝藏 */
	public static final String BURIED_TREASURE = "minecraft:chests/buried_treasure";
	/** 沉船地图 */
	public static final String SHIPWRECK_MAP = "minecraft:chests/shipwreck_map";
	/** 沉船补给 */
	public static final String SHIPWRECK_SUPPLY = "minecraft:chests/shipwreck_supply";
	/** 沉船宝藏 */
	public static final String SHIPWRECK_TREASURE = "minecraft:chests/shipwreck_treasure";
	/** 掠夺者前哨站 */
	public static final String PILLAGER_OUTPOST = "minecraft:chests/pillager_outpost";
	/** 堡垒宝藏 */
	public static final String BASTION_TREASURE = "minecraft:chests/bastion_treasure";
	/** 堡垒其他 */
	public static final String BASTION_OTHER = "minecraft:chests/bastion_other";
	/** 堡垒桥 */
	public static final String BASTION_BRIDGE = "minecraft:chests/bastion_bridge";
	/** 堡垒疣猪兽棚 */
	public static final String BASTION_HOGLIN_STABLE = "minecraft:chests/bastion_hoglin_stable";
	/** 远古城市 */
	public static final String ANCIENT_CITY = "minecraft:chests/ancient_city";
	/** 远古城市冰盒 */
	public static final String ANCIENT_CITY_ICE_BOX = "minecraft:chests/ancient_city_ice_box";
	/** 废弃传送门 */
	public static final String RUINED_PORTAL = "minecraft:chests/ruined_portal";

	// ===== 试炼密室（trial_chambers/*） =====

	/** 试炼密室奖励 */
	public static final String TRIAL_CHAMBERS_REWARD = "minecraft:chests/trial_chambers/reward";
	/** 试炼密室普通奖励 */
	public static final String TRIAL_CHAMBERS_REWARD_COMMON = "minecraft:chests/trial_chambers/reward_common";
	/** 试炼密室稀有奖励 */
	public static final String TRIAL_CHAMBERS_REWARD_RARE = "minecraft:chests/trial_chambers/reward_rare";
	/** 试炼密室独有奖励 */
	public static final String TRIAL_CHAMBERS_REWARD_UNIQUE = "minecraft:chests/trial_chambers/reward_unique";
	/** 试炼密室不祥奖励 */
	public static final String TRIAL_CHAMBERS_REWARD_OMINOUS = "minecraft:chests/trial_chambers/reward_ominous";
	/** 试炼密室不祥普通奖励 */
	public static final String TRIAL_CHAMBERS_REWARD_OMINOUS_COMMON = "minecraft:chests/trial_chambers/reward_ominous_common";
	/** 试炼密室不祥稀有奖励 */
	public static final String TRIAL_CHAMBERS_REWARD_OMINOUS_RARE = "minecraft:chests/trial_chambers/reward_ominous_rare";
	/** 试炼密室不祥独有奖励 */
	public static final String TRIAL_CHAMBERS_REWARD_OMINOUS_UNIQUE = "minecraft:chests/trial_chambers/reward_ominous_unique";
	/** 试炼密室补给 */
	public static final String TRIAL_CHAMBERS_SUPPLY = "minecraft:chests/trial_chambers/supply";
	/** 试炼密室走廊 */
	public static final String TRIAL_CHAMBERS_CORRIDOR = "minecraft:chests/trial_chambers/corridor";
	/** 试炼密室交叉路口 */
	public static final String TRIAL_CHAMBERS_INTERSECTION = "minecraft:chests/trial_chambers/intersection";
	/** 试炼密室交叉路口木桶 */
	public static final String TRIAL_CHAMBERS_INTERSECTION_BARREL = "minecraft:chests/trial_chambers/intersection_barrel";
	/** 试炼密室入口 */
	public static final String TRIAL_CHAMBERS_ENTRANCE = "minecraft:chests/trial_chambers/entrance";

	// ===== 钓鱼战利品（gameplay/fishing/*） =====

	/** 钓鱼总表 */
	public static final String FISHING = "minecraft:gameplay/fishing";
	/** 钓鱼垃圾 */
	public static final String FISHING_JUNK = "minecraft:gameplay/fishing/junk";
	/** 钓鱼宝藏 */
	public static final String FISHING_TREASURE = "minecraft:gameplay/fishing/treasure";
	/** 钓鱼鱼 */
	public static final String FISHING_FISH = "minecraft:gameplay/fishing/fish";

	// ===== 玩法战利品（gameplay/*） =====

	/** 猫的晨礼 */
	public static final String CAT_MORNING_GIFT = "minecraft:gameplay/cat_morning_gift";
	/** 猪灵以物易物 */
	public static final String PIGLIN_BARTERING = "minecraft:gameplay/piglin_bartering";
	/** 侦嗅兽挖掘 */
	public static final String SNIFFER_DIGGING = "minecraft:gameplay/sniffer_digging";

	// ===== 考古战利品（archaeology/*） =====

	/** 沙漠水井考古 */
	public static final String DESERT_WELL_ARCHAEOLOGY = "minecraft:archaeology/desert_well";
	/** 沙漠神殿考古 */
	public static final String DESERT_PYRAMID_ARCHAEOLOGY = "minecraft:archaeology/desert_pyramid";
	/** 古迹废墟普通 */
	public static final String TRAIL_RUINS_ARCHAEOLOGY_COMMON = "minecraft:archaeology/trail_ruins_common";
	/** 古迹废墟稀有 */
	public static final String TRAIL_RUINS_ARCHAEOLOGY_RARE = "minecraft:archaeology/trail_ruins_rare";
	/** 温暖海底废墟考古 */
	public static final String OCEAN_RUIN_WARM_ARCHAEOLOGY = "minecraft:archaeology/ocean_ruin_warm";
	/** 寒冷海底废墟考古 */
	public static final String OCEAN_RUIN_COLD_ARCHAEOLOGY = "minecraft:archaeology/ocean_ruin_cold";
}
