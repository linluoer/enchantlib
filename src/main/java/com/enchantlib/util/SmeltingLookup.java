package com.enchantlib.util;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 熔炼配方缓存查询工具。
 *
 * <p>提供 {@code Item → Optional<ItemStack>} 的缓存查询，避免每次方块破坏时
 * 重复查询 {@link RecipeManager}。自动烧炼附魔可通过本工具类将 50 行代码降到 5 行。</p>
 *
 * <h2>使用方式</h2>
 * <pre>{@code
 * // 在 ModifyBlockDropsEvent 回调中
 * event.transformDrops(SmeltingLookup::smeltOrOriginal);
 * event.addBonusXp(level);  // 补充熔炼经验
 * }</pre>
 *
 * <h2>生命周期</h2>
 * <ol>
 *   <li>{@link #initialize(MinecraftServer)}：在 SERVER_STARTED 时初始化，绑定 RecipeManager</li>
 *   <li>{@link #smelt(ItemStack)}：运行时查询，结果缓存在 ConcurrentHashMap 中</li>
 *   <li>{@link #invalidate()}：数据包重载时清空缓存，防止配方变更后返回过期结果</li>
 * </ol>
 *
 * <h2>线程安全</h2>
 * <p>缓存使用 {@link ConcurrentHashMap}，RecipeManager 引用使用 volatile。
 * 查询操作线程安全，但建议在服务端主线程调用（配方查询本身不是线程安全的）。</p>
 *
 * @since 0.1.0
 */
public final class SmeltingLookup {

	/** 熔炼结果缓存：Item → Optional<ItemStack>（空 Optional 表示该物品无熔炼配方） */
	private static final Map<Item, Optional<ItemStack>> CACHE = new ConcurrentHashMap<>();

	/** RecipeManager 引用，在 SERVER_STARTED 时设置 */
	private static volatile @Nullable RecipeManager recipeManager;

	/** Level 引用（用于 getRecipeFor 的 Level 参数），在 SERVER_STARTED 时设置 */
	private static volatile @Nullable Level level;

	private SmeltingLookup() {
	}

	/**
	 * 初始化 SmeltingLookup，绑定 RecipeManager。
	 *
	 * <p>应在 {@code ServerLifecycleEvents.SERVER_STARTED} 时调用。</p>
	 *
	 * @param server MinecraftServer
	 */
	public static void initialize(MinecraftServer server) {
		recipeManager = server.getRecipeManager();
		level = server.overworld();
		CACHE.clear();
	}

	/**
	 * 查询物品的熔炼结果。
	 *
	 * <p>首次查询会访问 {@link RecipeManager}，后续相同物品的查询直接返回缓存结果。
	 * 返回的 ItemStack 数量始终为 1（熔炼配方的标准输出），调用方需自行根据输入数量调整。</p>
	 *
	 * @param input 输入物品（非空）
	 * @return 熔炼结果（数量为 1），若无熔炼配方返回 {@link Optional#empty()}
	 */
	public static Optional<ItemStack> smelt(ItemStack input) {
		if (input.isEmpty()) {
			return Optional.empty();
		}
		Item item = input.getItem();
		Optional<ItemStack> cached = CACHE.get(item);
		if (cached != null) {
			return cached;
		}
		Optional<ItemStack> result = computeSmelt(input);
		CACHE.put(item, result);
		return result;
	}

	/**
	 * 便捷方法：返回熔炼结果或原物品。
	 *
	 * <p>典型用法（配合 {@link com.enchantlib.event.BuiltInEvents.ModifyBlockDropsEvent#transformDrops}）：</p>
	 * <pre>{@code
	 * event.transformDrops(SmeltingLookup::smeltOrOriginal);
	 * }</pre>
	 *
	 * @param input 输入物品
	 * @return 熔炼结果（保留输入数量），若无熔炼配方返回原物品
	 */
	public static ItemStack smeltOrOriginal(ItemStack input) {
		return smelt(input).map(result -> {
			ItemStack copy = result.copy();
			copy.setCount(input.getCount());
			return copy;
		}).orElse(input);
	}

	/**
	 * 查询熔炼配方并返回结果物品。
	 *
	 * @param input 输入物品
	 * @return 熔炼结果（数量为 1），若无配方返回 empty
	 */
	private static Optional<ItemStack> computeSmelt(ItemStack input) {
		if (recipeManager == null || level == null) {
			return Optional.empty();
		}
		SingleRecipeInput recipeInput = new SingleRecipeInput(input);
		Optional<RecipeHolder<net.minecraft.world.item.crafting.SmeltingRecipe>> recipe =
			recipeManager.getRecipeFor(RecipeType.SMELTING, recipeInput, level);
		return recipe.map(r -> r.value().assemble(recipeInput));
	}

	/**
	 * 清空缓存。
	 *
	 * <p>应在数据包重载时调用，防止配方变更后返回过期结果。
	 * 通过 {@code ServerLifecycleEvents.START_DATA_PACK_RELOAD} 注册。</p>
	 */
	public static void invalidate() {
		CACHE.clear();
	}

	/**
	 * 获取缓存大小（用于调试/统计）。
	 *
	 * @return 缓存的物品数量
	 */
	public static int cacheSize() {
		return CACHE.size();
	}
}
