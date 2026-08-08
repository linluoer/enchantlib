package com.enchantlib.api;

import com.enchantlib.event.EnchantmentEventRegistrar;
import net.minecraft.core.HolderLookup;

/**
 * 附魔注册入口接口。
 *
 * <p>其他模组通过实现此接口，在 fabric.mod.json 中声明 entrypoint {@code enchantlib:enchantments}，
 * 即可注册自定义附魔。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * public class MyModEnchantments implements EnchantmentEntrypoint {
 *     @Override
 *     public void onRegisterEnchantments(EnchantmentRegistrar registrar) {
 *         registrar.register(EnchantmentBuilder.create("mymod:leech")
 *             .description("Leech")
 *             .supportedItems("#minecraft:enchantable/sharp_weapon")
 *             .weight(5).maxLevel(3)
 *             .minCost(5, 8).maxCost(20, 8).anvilCost(2)
 *             .slots("mainhand"));
 *     }
 *
 *     @Override
 *     public void onRegisterExclusiveGroups(ExclusiveGroupRegistrar registrar) {
 *         registrar.register(ExclusiveGroupBuilder.create("mymod", "elemental")
 *             .add("mymod:fire_aspect")
 *             .add("mymod:ice_aspect"));
 *     }
 *
 *     @Override
 *     public void onRegisterLootInjections(LootInjectionRegistrar registrar) {
 *         // 将自定义附魔以附魔书形式注入到地下城箱子
 *         registrar.register(LootInjectionBuilder.create()
 *             .toTables(LootTables.SIMPLE_DUNGEON, LootTables.ABANDONED_MINESHAFT)
 *             .asBook()
 *             .withEnchantments("mymod:fire_aspect", "mymod:ice_aspect")
 *             .chance(0.5F));
 *     }
 *
 *     @Override
 *     public void onRegisterVillagerTrades(VillagerTradeRegistrar registrar) {
 *         // 让自定义附魔可被图书管理员自动出售
 *         registrar.registerTradeableEnchantments(
 *             TradeableEnchantmentsBuilder.create()
 *                 .addEnchantments("mymod:fire_aspect", "mymod:ice_aspect")
 *         );
 *
 *         // 注册自定义交易：武器匠 Level 3 出售附魔钻石剑
 *         registrar.registerTrade(VillagerTradeBuilder
 *             .create("mymod:weaponsmith/3/sharpness_sword")
 *             .profession(VillagerTrades.WEAPONSMITH)
 *             .level(VillagerTrades.LEVEL_3)
 *             .asItem(Items.DIAMOND_SWORD)
 *             .withEnchantments("mymod:sharpness_plus")
 *             .emeralds(20));
 *     }
 * }
 * }</pre>
 *
 * <p>fabric.mod.json 声明：</p>
 * <pre>{@code
 * "entrypoints": {
 *   "enchantlib:enchantments": ["com.mymod.MyModEnchantments"]
 * }
 * }</pre>
 *
 * @since 0.1.0
 */
public interface EnchantmentEntrypoint {

	/**
	 * 注册附魔。
	 *
	 * @param registrar 附魔注册器，调用 {@link EnchantmentRegistrar#register(EnchantmentBuilder)} 注册附魔
	 */
	void onRegisterEnchantments(EnchantmentRegistrar registrar);

	/**
	 * 注册互斥组。
	 *
	 * <p>默认空实现，向后兼容。需要定义自定义互斥组的模组可覆盖此方法。</p>
	 *
	 * @param registrar 互斥组注册器，调用 {@link ExclusiveGroupRegistrar#register(ExclusiveGroupBuilder)} 注册互斥组
	 */
	default void onRegisterExclusiveGroups(ExclusiveGroupRegistrar registrar) {
		// 默认空实现，向后兼容
	}

	/**
	 * 注册战利品注入规则。
	 *
	 * <p>默认空实现，向后兼容。需要向原版战利品表注入自定义附魔的模组可覆盖此方法。</p>
	 *
	 * <p>注入规则通过 {@link LootInjectionBuilder} 链式构建，支持：</p>
	 * <ul>
	 *   <li>指定多个目标战利品表（如 {@link LootTables#SIMPLE_DUNGEON}）</li>
	 *   <li>选择物品形式：附魔书（{@link LootInjectionBuilder#asBook()}）或已附魔物品（{@link LootInjectionBuilder#asItem(net.minecraft.world.item.Item)}）</li>
	 *   <li>从候选附魔列表中随机选择，等级在附魔的 minLevel~maxLevel 范围内随机生成</li>
	 *   <li>配置注入概率（{@link LootInjectionBuilder#chance(float)}）</li>
	 * </ul>
	 *
	 * @param registrar 战利品注入注册器，调用 {@link LootInjectionRegistrar#register(LootInjectionBuilder)} 注册规则
	 */
	default void onRegisterLootInjections(LootInjectionRegistrar registrar) {
		// 默认空实现，向后兼容
	}

	/**
	 * 注册村民交易。
	 *
	 * <p>默认空实现，向后兼容。需要让自定义附魔通过村民交易获取的模组可覆盖此方法。</p>
	 *
	 * <p>支持两类注册：</p>
	 * <ul>
	 *   <li>{@link TradeableEnchantmentsBuilder}：声明附魔可被原版图书管理员自动出售
	 *       （注入 {@code #minecraft:tradeable} 附魔标签，原版图书管理员 Level 1~4 会自动出售）</li>
	 *   <li>{@link VillagerTradeBuilder}：注册完全自定义的村民交易
	 *       （指定职业/层级/价格/物品/候选附魔，注入 {@code villager_trade/*.json} 和层级 tag）</li>
	 * </ul>
	 *
	 * @param registrar 村民交易注册器
	 */
	default void onRegisterVillagerTrades(VillagerTradeRegistrar registrar) {
		// 默认空实现，向后兼容
	}

	/**
	 * 注册附魔事件回调。
	 *
	 * <p>默认空实现，向后兼容。需要为自定义附魔注册事件回调的模组可覆盖此方法。</p>
	 *
	 * <p>事件回调通过 {@link EnchantmentEventRegistrar#register} 注册，
	 * 由 {@link com.enchantlib.event.EnchantmentEventDispatcher} 在事件触发时分发。</p>
	 *
	 * <p><b>异常隔离保证</b>：单个回调抛出异常不会影响其他附魔的回调或原版逻辑。
	 * 详见 {@link com.enchantlib.event.EnchantmentEventDispatcher}。</p>
	 *
	 * <p>使用示例：</p>
	 * <pre>{@code
	 * @Override
	 * public void onRegisterEventCallbacks(EnchantmentEventRegistrar registrar,
	 *                                      HolderLookup.Provider registries) {
	 *     Holder<Enchantment> leech = resolveEnchantment(registries, "mymod:leech");
	 *     registrar.register(leech, BuiltInEvents.POST_ATTACK, (event, ctx) -> {
	 *         event.attacker().heal(ctx.level() * 2.0F);
	 *     });
	 * }
	 * }</pre>
	 *
	 * @param registrar 事件回调注册器
	 * @param registries 注册表访问器（用于解析附魔 Holder）
	 */
	default void onRegisterEventCallbacks(EnchantmentEventRegistrar registrar,
										  HolderLookup.Provider registries) {
		// 默认空实现，向后兼容
	}
}
