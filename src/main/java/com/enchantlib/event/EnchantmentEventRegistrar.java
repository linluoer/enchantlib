package com.enchantlib.event;

import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 附魔事件回调注册器。
 *
 * <p>由 EnchantLib 主类在 entrypoint 收集阶段创建并传递给模组，
 * 模组通过 {@link #register(Holder, EnchantmentEventType, EnchantmentEventCallback)} 注册回调。</p>
 *
 * <p>注册的回调由 {@link EnchantmentEventDispatcher} 统一管理，事件触发时按附魔聚合分发。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * public class MyModEnchantments implements EnchantmentEntrypoint {
 *     @Override
 *     public void onRegisterEventCallbacks(EnchantmentEventRegistrar registrar,
 *                                          HolderLookup.Provider registries) {
 *         Holder<Enchantment> leech = lookupEnchantment(registries, "mymod:leech");
 *
 *         // 默认策略（IGNORE，任何充能都触发）
 *         registrar.register(leech, BuiltInEvents.POST_ATTACK, (event, ctx) -> {
 *             event.attacker().heal(ctx.level() * 2.0F);
 *         });
 *
 *         // 携带触发策略（充能 >= 0.7 才触发，且按充能缩放等级）
 *         registrar.register(leech, BuiltInEvents.POST_ATTACK,
 *             (event, ctx) -> {
 *                 event.attacker().heal(ctx.level() * 2.0F);
 *             },
 *             new TriggerPolicy(TriggerPolicy.Mode.THRESHOLD_SCALED, 0.7f));
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 */
public final class EnchantmentEventRegistrar {

	private int registeredCount = 0;

	public EnchantmentEventRegistrar() {
	}

	/**
	 * 注册一个事件回调（默认 {@link TriggerPolicy#IGNORE} 策略，向后兼容）。
	 *
	 * <p>同一附魔 + 同一事件类型可注册多个回调，按注册顺序执行。
	 * 回调内抛出的异常会被分发器捕获，不影响其他回调或原版逻辑。</p>
	 *
	 * @param enchantment 目标附魔 Holder（必须已注册到附魔注册表）
	 * @param type        事件类型
	 * @param callback    回调
	 * @param <E>         事件类型
	 * @return this（链式调用）
	 */
	public <E extends EnchantmentEvent> EnchantmentEventRegistrar register(
		Holder<Enchantment> enchantment,
		EnchantmentEventType<E> type,
		EnchantmentEventCallback<E> callback) {
		return register(enchantment, type, callback, TriggerPolicy.IGNORE);
	}

	/**
	 * 注册一个事件回调（携带触发策略）。
	 *
	 * <p>策略仅对 {@link ChargeableEvent} 事件（如 {@link BuiltInEvents.PostAttackEvent}）生效，
	 * 其他事件类型忽略策略。配置文件 {@code trigger.toml} 可按附魔 ID 覆盖此处设置的策略。</p>
	 *
	 * <p><b>误配禁止</b>：对非 {@link ChargeableEvent} 事件类型传递非 {@link TriggerPolicy#IGNORE}
	 * 策略将抛出 {@link IllegalArgumentException}，避免实现模组误以为策略会生效。
	 * 非充能事件（ENTITY_TICK、POST_HURT、POST_KILL、MODIFY_DAMAGE、MODIFY_BLOCK_DROPS、
	 * POST_BLOCK_BREAK、ITEM_USE、BLOCK_USE、ENTITY_USE）无充能概念，策略无意义。</p>
	 *
	 * @param enchantment 目标附魔 Holder（必须已注册到附魔注册表）
	 * @param type        事件类型
	 * @param callback    回调
	 * @param policy      触发策略（null 视为 {@link TriggerPolicy#IGNORE}）
	 * @param <E>         事件类型
	 * @return this（链式调用）
	 * @throws IllegalArgumentException 若对非 ChargeableEvent 事件类型传递非 IGNORE 策略
	 */
	public <E extends EnchantmentEvent> EnchantmentEventRegistrar register(
		Holder<Enchantment> enchantment,
		EnchantmentEventType<E> type,
		EnchantmentEventCallback<E> callback,
		TriggerPolicy policy) {
		Objects.requireNonNull(enchantment, "enchantment");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(callback, "callback");
		TriggerPolicy effectivePolicy = (policy == null) ? TriggerPolicy.IGNORE : policy;
		// 策略有效性校验：非 ChargeableEvent 事件只能使用 IGNORE 策略
		if (effectivePolicy != TriggerPolicy.IGNORE
			&& !ChargeableEvent.class.isAssignableFrom(type.eventClass())) {
			throw new IllegalArgumentException(String.format(
				"TriggerPolicy %s 不适用于事件类型 %s（事件类 %s 未实现 ChargeableEvent）。"
					+ "非充能事件无充能概念，请使用 TriggerPolicy.IGNORE。",
				effectivePolicy, type.id(), type.eventClass().getSimpleName()));
		}
		EnchantmentEventDispatcher.register(enchantment, type, callback, effectivePolicy);
		registeredCount++;
		return this;
	}

	/**
	 * 获取已注册的回调总数。
	 *
	 * @return 回调数
	 */
	public int registeredCount() {
		return registeredCount;
	}
}

