package com.enchantlib.api;

/**
 * 原版村民职业常量。
 *
 * <p>用于 {@link VillagerTradeBuilder#profession(String)} 指定交易所属的职业层级 tag。
 * 这些值对应原版 {@code VillagerTradeTags} 中的职业路径前缀。</p>
 *
 * <p>示例：</p>
 * <pre>{@code
 * VillagerTradeBuilder.create("mymod:librarian/1/fire_aspect_book")
 *     .profession(VillagerTrades.LIBRARIAN)
 *     .level(VillagerTrades.LEVEL_1)
 *     .asBook()
 *     .withEnchantments("mymod:fire_aspect")
 *     .emeralds(5);
 * }</pre>
 *
 * @since 0.1.0
 */
public final class VillagerTrades {

	/** 等级 1（新手） */
	public static final int LEVEL_1 = 1;
	/** 等级 2（学徒） */
	public static final int LEVEL_2 = 2;
	/** 等级 3（老手） */
	public static final int LEVEL_3 = 3;
	/** 等级 4（专家） */
	public static final int LEVEL_4 = 4;
	/** 等级 5（大师） */
	public static final int LEVEL_5 = 5;

	/** 农民 */
	public static final String FARMER = "farmer";
	/** 渔夫 */
	public static final String FISHERMAN = "fisherman";
	/** 牧羊人 */
	public static final String SHEPHERD = "shepherd";
	/** 制箭师 */
	public static final String FLETCHER = "fletcher";
	/** 图书管理员（可自动出售附魔书） */
	public static final String LIBRARIAN = "librarian";
	/** 制图师 */
	public static final String CARTOGRAPHER = "cartographer";
	/** 牧师 */
	public static final String CLERIC = "cleric";
	/** 通用 smith（未细化分支） */
	public static final String COMMON_SMITH = "common_smith";
	/** 盔甲匠 */
	public static final String ARMORER = "armorer";
	/** 武器匠 */
	public static final String WEAPONSMITH = "weaponsmith";
	/** 工具匠 */
	public static final String TOOLSMITH = "toolsmith";
	/** 屠夫 */
	public static final String BUTCHER = "butcher";
	/** 皮匠 */
	public static final String LEATHERWORKER = "leatherworker";
	/** 石匠 */
	public static final String MASON = "mason";

	/** 流浪商人（特殊，无层级，使用 buying/uncommon/common 子分类） */
	public static final String WANDERING_TRADER = "wandering_trader";

	private VillagerTrades() {
	}

	/**
	 * 校验职业字符串是否合法。
	 *
	 * @param profession 职业字符串
	 * @return true 表示合法
	 */
	public static boolean isValidProfession(String profession) {
		if (profession == null || profession.isEmpty()) {
			return false;
		}
		return switch (profession) {
			case FARMER, FISHERMAN, SHEPHERD, FLETCHER, LIBRARIAN, CARTOGRAPHER, CLERIC,
					COMMON_SMITH, ARMORER, WEAPONSMITH, TOOLSMITH, BUTCHER, LEATHERWORKER,
					MASON, WANDERING_TRADER -> true;
			default -> false;
		};
	}

	/**
	 * 校验等级是否合法（1-5）。
	 *
	 * @param level 等级
	 * @return true 表示合法
	 */
	public static boolean isValidLevel(int level) {
		return level >= LEVEL_1 && level <= LEVEL_5;
	}
}
