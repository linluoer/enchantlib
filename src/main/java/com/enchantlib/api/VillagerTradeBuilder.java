package com.enchantlib.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * 村民交易构建器。
 *
 * <p>链式构建一条 {@link VillagerTradeInjection} 规则，将自定义附魔以附魔书或已附魔物品形式
 * 通过村民交易系统提供给玩家。</p>
 *
 * <h2>底层机制</h2>
 * <p>MC 26.2 将村民交易完全注册表化+Codec 化+Tag 化。本构建器生成以下资源并注入运行时数据包：</p>
 * <ul>
 *   <li>{@code data/<namespace>/villager_trade/<path>.json} - 交易定义（Codec: VillagerTrade.CODEC）</li>
 *   <li>{@code data/minecraft/tags/villager_trade/<profession>/level_<n>.json} - 加入职业层级 tag</li>
 * </ul>
 *
 * <h2>价格机制</h2>
 * <ul>
 *   <li>主成本：emerald 数量（{@link #emeralds(int)}）</li>
 *   <li>附加成本：默认 1 本书（{@link #additionalItem(Item, int)} 可自定义）</li>
 *   <li>结果物品：附魔书或指定物品</li>
 *   <li>附魔选择：从候选列表中随机选一个，等级在 minLevel~maxLevel 范围内随机</li>
 *   <li>{@link #doublePrice(boolean)} 控制是否对稀有附魔翻倍价格</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 示例1：图书管理员 Level 1 出售附魔书（5 emerald + 1 book）
 * VillagerTradeBuilder.create("mymod:librarian/1/fire_aspect_book")
 *     .profession(VillagerTrades.LIBRARIAN)
 *     .level(VillagerTrades.LEVEL_1)
 *     .asBook()
 *     .withEnchantments("mymod:fire_aspect", "mymod:ice_aspect")
 *     .emeralds(5)
 *     .maxUses(12)
 *     .xp(1)
 *     .priceMultiplier(0.2F);
 *
 * // 示例2：武器匠 Level 3 出售附魔钻石剑（20 emerald）
 * VillagerTradeBuilder.create("mymod:weaponsmith/3/sharpness_sword")
 *     .profession(VillagerTrades.WEAPONSMITH)
 *     .level(VillagerTrades.LEVEL_3)
 *     .asItem(Items.DIAMOND_SWORD)
 *     .withEnchantments("mymod:sharpness_plus")
 *     .emeralds(20)
 *     .maxUses(3);
 * }</pre>
 *
 * @since 0.1.0
 */
public final class VillagerTradeBuilder {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Identifier tradeId;
	private String profession;
	private int level = 1;
	private VillagerTradeInjection.Form form = VillagerTradeInjection.Form.BOOK;
	private Item item = null;
	private int emeralds = 1;
	private Item additionalItem = Items.BOOK;
	private int additionalItemCount = 1;
	private final Set<String> enchantments = new LinkedHashSet<>();
	private int maxUses = 12;
	private int xp = 1;
	private float priceMultiplier = 0.2F;
	private boolean doublePrice = false;

	private VillagerTradeBuilder(Identifier tradeId) {
		this.tradeId = tradeId;
	}

	/**
	 * 创建构建器实例。
	 *
	 * @param tradeId 交易 ID（如 "mymod:librarian/1/fire_aspect_book"）
	 * @return 构建器实例
	 */
	public static VillagerTradeBuilder create(String tradeId) {
		return new VillagerTradeBuilder(Identifier.parse(tradeId));
	}

	/**
	 * 创建构建器实例。
	 *
	 * @param namespace 命名空间（如 "mymod"）
	 * @param path 路径（如 "librarian/1/fire_aspect_book"）
	 * @return 构建器实例
	 */
	public static VillagerTradeBuilder create(String namespace, String path) {
		return new VillagerTradeBuilder(Identifier.fromNamespaceAndPath(namespace, path));
	}

	/**
	 * 设置职业（参见 {@link VillagerTrades}）。
	 *
	 * @param profession 职业字符串（如 {@link VillagerTrades#LIBRARIAN}）
	 * @return this
	 */
	public VillagerTradeBuilder profession(String profession) {
		this.profession = profession;
		return this;
	}

	/**
	 * 设置层级（1-5）。
	 *
	 * @param level 等级（参见 {@link VillagerTrades#LEVEL_1} 等）
	 * @return this
	 */
	public VillagerTradeBuilder level(int level) {
		this.level = level;
		return this;
	}

	/**
	 * 设置物品形式为附魔书（默认）。
	 *
	 * @return this
	 */
	public VillagerTradeBuilder asBook() {
		this.form = VillagerTradeInjection.Form.BOOK;
		this.item = null;
		return this;
	}

	/**
	 * 设置物品形式为已附魔物品。
	 *
	 * @param item 目标物品（如 Items.DIAMOND_SWORD）
	 * @return this
	 */
	public VillagerTradeBuilder asItem(Item item) {
		this.form = VillagerTradeInjection.Form.ITEM;
		this.item = item;
		return this;
	}

	/**
	 * 设置物品形式为已附魔物品。
	 *
	 * @param item 目标物品
	 * @return this
	 */
	public VillagerTradeBuilder asItem(net.minecraft.world.level.ItemLike item) {
		return asItem(item.asItem());
	}

	/**
	 * 设置主成本（emerald 数量）。
	 *
	 * @param emeralds emerald 数量（>=0）
	 * @return this
	 */
	public VillagerTradeBuilder emeralds(int emeralds) {
		if (emeralds < 0) {
			throw new IllegalArgumentException("emerald 数量必须 >= 0: " + emeralds);
		}
		this.emeralds = emeralds;
		return this;
	}

	/**
	 * 设置附加成本物品（默认 1 本书）。
	 *
	 * @param item 附加物品
	 * @param count 数量（>=0，0 表示无附加成本）
	 * @return this
	 */
	public VillagerTradeBuilder additionalItem(Item item, int count) {
		if (count < 0) {
			throw new IllegalArgumentException("附加物品数量必须 >= 0: " + count);
		}
		this.additionalItem = item;
		this.additionalItemCount = count;
		return this;
	}

	/**
	 * 设置附加成本物品。
	 *
	 * @param item 附加物品
	 * @param count 数量
	 * @return this
	 */
	public VillagerTradeBuilder additionalItem(net.minecraft.world.level.ItemLike item, int count) {
		return additionalItem(item.asItem(), count);
	}

	/**
	 * 移除附加成本物品。
	 *
	 * @return this
	 */
	public VillagerTradeBuilder noAdditionalItem() {
		this.additionalItem = null;
		this.additionalItemCount = 0;
		return this;
	}

	/**
	 * 添加候选附魔。
	 *
	 * @param enchantmentIds 附魔 ID 列表（如 "mymod:fire_aspect"）
	 * @return this
	 */
	public VillagerTradeBuilder withEnchantments(String... enchantmentIds) {
		for (String id : enchantmentIds) {
			this.enchantments.add(id);
		}
		return this;
	}

	/**
	 * 添加候选附魔。
	 *
	 * @param enchantmentIds 附魔 ID 集合
	 * @return this
	 */
	public VillagerTradeBuilder withEnchantments(Set<String> enchantmentIds) {
		this.enchantments.addAll(enchantmentIds);
		return this;
	}

	/**
	 * 设置最大使用次数（默认 12）。
	 *
	 * @param maxUses 最大次数（>=1）
	 * @return this
	 */
	public VillagerTradeBuilder maxUses(int maxUses) {
		if (maxUses < 1) {
			throw new IllegalArgumentException("maxUses 必须 >= 1: " + maxUses);
		}
		this.maxUses = maxUses;
		return this;
	}

	/**
	 * 设置经验值（默认 1）。
	 *
	 * @param xp 经验值（>=0）
	 * @return this
	 */
	public VillagerTradeBuilder xp(int xp) {
		if (xp < 0) {
			throw new IllegalArgumentException("xp 必须 >= 0: " + xp);
		}
		this.xp = xp;
		return this;
	}

	/**
	 * 设置价格倍率（默认 0.2）。
	 *
	 * @param multiplier 倍率（0.0~1.0）
	 * @return this
	 */
	public VillagerTradeBuilder priceMultiplier(float multiplier) {
		if (multiplier < 0.0F || multiplier > 1.0F) {
			throw new IllegalArgumentException("priceMultiplier 必须在 0.0~1.0 之间: " + multiplier);
		}
		this.priceMultiplier = multiplier;
		return this;
	}

	/**
	 * 设置是否对 {@code #minecraft:double_trade_price} 标签中的附魔翻倍价格。
	 *
	 * <p>开启后，原版会自动检查结果物品的 STORED_ENCHANTMENTS 或 ENCHANTMENTS 组件，
	 * 若包含该标签中的附魔则将价格翻倍。</p>
	 *
	 * @param doublePrice 是否翻倍（默认 false）
	 * @return this
	 */
	public VillagerTradeBuilder doublePrice(boolean doublePrice) {
		this.doublePrice = doublePrice;
		return this;
	}

	/**
	 * 构建不可变的 {@link VillagerTradeInjection} 实例。
	 *
	 * <p>构建时会预生成交易定义 JSON 和层级 tag JSON 字节数组，存储在 injection 中
	 * 供后续数据包注入使用。</p>
	 *
	 * @return 注入规则实例
	 * @throws IllegalStateException 如果缺少必填字段或字段值无效
	 */
	public VillagerTradeInjection build() {
		validate();
		return new VillagerTradeInjection(
			tradeId, profession, level, form, item,
			emeralds, additionalItem, additionalItemCount,
			enchantments, maxUses, xp, priceMultiplier, doublePrice,
			toTradeJsonBytes(), toTagJsonBytes(),
			getTradeResourceId(), getTagResourceId(), getTagIdString()
		);
	}

	/**
	 * 生成 {@code data/<namespace>/villager_trade/<path>.json} 的字节数组。
	 *
	 * <p>按原版 {@code VillagerTrade.CODEC} 格式构建 JSON，包含：</p>
	 * <ul>
	 *   <li>{@code wants}: 主成本（emerald + 数量）</li>
	 *   <li>{@code additional_wants}: 附加成本（可选，如 1 本书）</li>
	 *   <li>{@code gives}: 结果物品（ENCHANTED_BOOK 或指定物品）</li>
	 *   <li>{@code max_uses}, {@code xp}, {@code reputation_discount}</li>
	 *   <li>{@code given_item_modifiers}: 附魔函数（EnchantRandomlyFunction + FilteredFunction）</li>
	 *   <li>{@code double_trade_price_enchantments}: 可选，引用 #minecraft:double_trade_price</li>
	 * </ul>
	 *
	 * @return JSON 字节数组
	 */
	public byte[] toTradeJsonBytes() {
		JsonObject root = new JsonObject();

		// wants: 主成本
		JsonObject wants = new JsonObject();
		wants.addProperty("id", "minecraft:emerald");
		wants.addProperty("count", emeralds);
		root.add("wants", wants);

		// additional_wants: 附加成本（可选）
		if (additionalItem != null && additionalItemCount > 0) {
			JsonObject additionalWants = new JsonObject();
			additionalWants.addProperty("id", getRawItemId(additionalItem));
			additionalWants.addProperty("count", additionalItemCount);
			root.add("additional_wants", additionalWants);
		}

		// gives: 结果物品
		JsonObject gives = new JsonObject();
		if (form == VillagerTradeInjection.Form.BOOK) {
			gives.addProperty("id", "minecraft:enchanted_book");
		} else {
			gives.addProperty("id", getRawItemId(item));
		}
		root.add("gives", gives);

		// 基础参数
		root.addProperty("max_uses", maxUses);
		root.addProperty("xp", xp);
		root.addProperty("reputation_discount", priceMultiplier);

		// given_item_modifiers: 附魔函数列表
		JsonArray modifiers = new JsonArray();

		// 1. EnchantRandomlyFunction（或 EnchantWithLevelsFunction 用于 ITEM 形式）
		// 注意：不设置 include_additional_cost_component（默认 false），价格固定为 emeralds 指定的值
		// 如需按附魔成本动态定价，可在 API 中扩展（emeralds=0 + include_additional_cost_component=true）
		if (form == VillagerTradeInjection.Form.BOOK) {
			// BOOK 形式：使用 EnchantRandomlyFunction + STORED_ENCHANTMENTS
			JsonObject enchantFn = new JsonObject();
			enchantFn.addProperty("function", "minecraft:enchant_randomly");
			JsonArray options = new JsonArray();
			for (String id : enchantments) {
				options.add(id);
			}
			enchantFn.add("options", options);
			// only_compatible: false 允许不兼容附魔（原版字段名是 only_compatible，不是 allow_incompatible_enchantments）
			enchantFn.addProperty("only_compatible", false);
			modifiers.add(enchantFn);
		} else {
			// ITEM 形式：使用 EnchantWithLevelsFunction + ENCHANTMENTS
			JsonObject enchantFn = new JsonObject();
			enchantFn.addProperty("function", "minecraft:enchant_with_levels");
			JsonArray options = new JsonArray();
			for (String id : enchantments) {
				options.add(id);
			}
			enchantFn.add("options", options);
			// levels: NumberProvider (uniform generator)，格式 {"min": 5, "max": 19}
			JsonObject levels = new JsonObject();
			levels.addProperty("min", 5);
			levels.addProperty("max", 19);
			enchantFn.add("levels", levels);
			modifiers.add(enchantFn);
		}

		// 2. FilteredFunction（验证附魔是否成功应用）
		JsonObject filtered = new JsonObject();
		filtered.addProperty("function", "minecraft:filtered");
		JsonObject predicate = new JsonObject();
		predicate.addProperty("items", form == VillagerTradeInjection.Form.BOOK
			? "minecraft:enchanted_book" : getRawItemId(item));
		// 使用 predicates 字段（DataComponentMatchers.partial），存储 EnchantmentsPredicate 数组
		JsonObject predicates = new JsonObject();
		String predicateKey = form == VillagerTradeInjection.Form.BOOK
			? "stored_enchantments" : "enchantments";
		// EnchantmentsPredicate 是 EnchantmentPredicate 列表，空对象 {} 表示任意附魔任意等级
		JsonArray enchantArray = new JsonArray();
		enchantArray.add(new JsonObject());
		predicates.add(predicateKey, enchantArray);
		predicate.add("predicates", predicates);
		filtered.add("item_filter", predicate);
		JsonObject onFail = new JsonObject();
		onFail.addProperty("function", "minecraft:discard");
		filtered.add("on_fail", onFail);
		modifiers.add(filtered);

		root.add("given_item_modifiers", modifiers);

		// double_trade_price_enchantments: 可选
		if (doublePrice) {
			root.addProperty("double_trade_price_enchantments", "#minecraft:double_trade_price");
		}

		return GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * 生成 {@code data/minecraft/tags/villager_trade/<profession>/level_<n>.json} 的字节数组。
	 *
	 * <p>将本交易 ID 加入对应职业层级的 tag，{@code replace: false} 允许其他数据包追加。</p>
	 *
	 * @return JSON 字节数组
	 */
	public byte[] toTagJsonBytes() {
		JsonObject root = new JsonObject();
		root.addProperty("replace", false);
		JsonArray values = new JsonArray();
		values.add(tradeId.toString());
		root.add("values", values);
		return GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * 获取交易资源 ID：{@code <namespace>:villager_trade/<path>.json}
	 *
	 * @return 资源 ID
	 */
	public Identifier getTradeResourceId() {
		return Identifier.fromNamespaceAndPath(
			tradeId.getNamespace(), "villager_trade/" + tradeId.getPath() + ".json");
	}

	/**
	 * 获取层级 tag 资源 ID：{@code minecraft:tags/villager_trade/<profession>/level_<n>.json}
	 *
	 * @return 资源 ID
	 */
	public Identifier getTagResourceId() {
		return Identifier.fromNamespaceAndPath(
			"minecraft", "tags/villager_trade/" + profession + "/level_" + level + ".json");
	}

	/**
	 * 获取层级 tag ID：{@code minecraft:<profession>/level_<n>}
	 *
	 * @return tag ID
	 */
	public String getTagIdString() {
		return "minecraft:" + profession + "/level_" + level;
	}

	/**
	 * 获取交易 ID。
	 *
	 * @return 交易 ID
	 */
	public Identifier getTradeId() {
		return tradeId;
	}

	/**
	 * 获取职业字符串。
	 *
	 * @return 职业字符串（如 "librarian"）
	 */
	public String getProfession() {
		return profession;
	}

	/**
	 * 获取层级。
	 *
	 * @return 层级（1-5）
	 */
	public int getLevel() {
		return level;
	}

	private void validate() {
		if (profession == null || profession.isEmpty()) {
			throw new IllegalStateException("村民交易规则 " + tradeId + " 缺少 profession（请调用 profession()）");
		}
		if (!VillagerTrades.isValidProfession(profession)) {
			throw new IllegalStateException("村民交易规则 " + tradeId + " 的 profession 无效: " + profession
				+ "（参见 com.enchantlib.api.VillagerTrades 常量）");
		}
		if (!VillagerTrades.isValidLevel(level)) {
			throw new IllegalStateException("村民交易规则 " + tradeId + " 的 level 无效: " + level + "（必须在 1-5 之间）");
		}
		if (form == VillagerTradeInjection.Form.ITEM && item == null) {
			throw new IllegalStateException("村民交易规则 " + tradeId + " 为 ITEM 形式但未指定物品（请调用 asItem()）");
		}
		if (enchantments.isEmpty()) {
			throw new IllegalStateException("村民交易规则 " + tradeId + " 缺少候选附魔（请调用 withEnchantments()）");
		}
		if (additionalItemCount > 0 && additionalItem == null) {
			throw new IllegalStateException("村民交易规则 " + tradeId + " 的 additionalItemCount > 0 但 additionalItem 为 null");
		}
	}

	/** 获取物品的注册表 ID 字符串（如 "minecraft:book"） */
	private static String getRawItemId(Item item) {
		return item.builtInRegistryHolder().key().identifier().toString();
	}
}
