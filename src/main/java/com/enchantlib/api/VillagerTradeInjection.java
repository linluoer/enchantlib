package com.enchantlib.api;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * 不可变的村民交易注入规则。
 *
 * <p>由 {@link VillagerTradeBuilder#build()} 构造，包含完整的交易定义和层级归属信息。
 * 通过运行时数据包注入到原版村民交易系统中。</p>
 *
 * <h2>物品形式</h2>
 * <ul>
 *   <li>{@link Form#BOOK}：注入附魔书（Items.ENCHANTED_BOOK + STORED_ENCHANTMENTS）</li>
 *   <li>{@link Form#ITEM}：注入已附魔的指定物品（如附魔剑、附魔镐）</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class VillagerTradeInjection {

	/** 物品形式 */
	public enum Form { BOOK, ITEM }

	private final Identifier tradeId;
	private final String profession;
	private final int level;
	private final Form form;
	private final Item item;
	private final int emeralds;
	private final Item additionalItem;
	private final int additionalItemCount;
	private final Set<String> enchantments;
	private final int maxUses;
	private final int xp;
	private final float priceMultiplier;
	private final boolean doublePrice;

	/** 预生成的交易定义 JSON 字节数组（data/<ns>/villager_trade/<path>.json） */
	private final byte[] tradeJsonBytes;
	/** 预生成的层级 tag JSON 字节数组（仅含本交易，data/minecraft/tags/villager_trade/<prof>/level_<n>.json） */
	private final byte[] tagJsonBytes;
	/** 交易资源 ID */
	private final Identifier tradeResourceId;
	/** 层级 tag 资源 ID */
	private final Identifier tagResourceId;
	/** 层级 tag ID 字符串 */
	private final String tagIdString;

	VillagerTradeInjection(Identifier tradeId, String profession, int level, Form form, Item item,
							int emeralds, Item additionalItem, int additionalItemCount,
							Set<String> enchantments, int maxUses, int xp,
							float priceMultiplier, boolean doublePrice,
							byte[] tradeJsonBytes, byte[] tagJsonBytes,
							Identifier tradeResourceId, Identifier tagResourceId, String tagIdString) {
		this.tradeId = tradeId;
		this.profession = profession;
		this.level = level;
		this.form = form;
		this.item = item;
		this.emeralds = emeralds;
		this.additionalItem = additionalItem;
		this.additionalItemCount = additionalItemCount;
		this.enchantments = new LinkedHashSet<>(enchantments);
		this.maxUses = maxUses;
		this.xp = xp;
		this.priceMultiplier = priceMultiplier;
		this.doublePrice = doublePrice;
		this.tradeJsonBytes = tradeJsonBytes;
		this.tagJsonBytes = tagJsonBytes;
		this.tradeResourceId = tradeResourceId;
		this.tagResourceId = tagResourceId;
		this.tagIdString = tagIdString;
	}

	/** 交易 ID（如 "mymod:librarian/1/fire_aspect_book"） */
	public Identifier getTradeId() { return tradeId; }

	/** 职业字符串（如 "librarian"，参见 {@link VillagerTrades}） */
	public String getProfession() { return profession; }

	/** 等级（1-5） */
	public int getLevel() { return level; }

	/** 物品形式 */
	public Form getForm() { return form; }

	/** 物品（仅 ITEM 形式有效） */
	public Item getItem() { return item; }

	/** 主成本（emerald 数量） */
	public int getEmeralds() { return emeralds; }

	/** 附加成本物品（如 BOOK） */
	public Item getAdditionalItem() { return additionalItem; }

	/** 附加成本物品数量 */
	public int getAdditionalItemCount() { return additionalItemCount; }

	/** 候选附魔 ID 集合 */
	public Set<String> getEnchantments() { return enchantments; }

	/** 最大使用次数 */
	public int getMaxUses() { return maxUses; }

	/** 经验值 */
	public int getXp() { return xp; }

	/** 价格倍率 */
	public float getPriceMultiplier() { return priceMultiplier; }

	/** 是否对 {@code #minecraft:double_trade_price} 标签中的附魔翻倍价格 */
	public boolean isDoublePrice() { return doublePrice; }

	/** 交易定义 JSON 字节数组 */
	public byte[] getTradeJsonBytes() { return tradeJsonBytes; }

	/** 层级 tag JSON 字节数组（仅含本交易） */
	public byte[] getTagJsonBytes() { return tagJsonBytes; }

	/** 交易资源 ID：{@code <namespace>:villager_trade/<path>.json} */
	public Identifier getTradeResourceId() { return tradeResourceId; }

	/** 层级 tag 资源 ID：{@code minecraft:tags/villager_trade/<profession>/level_<n>.json} */
	public Identifier getTagResourceId() { return tagResourceId; }

	/** 层级 tag ID 字符串：{@code minecraft:<profession>/level_<n>} */
	public String getTagIdString() { return tagIdString; }
}
