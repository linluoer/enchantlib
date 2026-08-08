package com.enchantlib.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * EnchantLib 全局事件注册入口。
 *
 * <p>提供不基于装备附魔扫描的全局事件注册 API。这些事件通常用于在任意实体上触发效果
 * （如焚心持续伤害需要每 tick 检查目标实体），或拦截 MC 原生机制（如自然回血）。</p>
 *
 * <p>与 {@link BuiltInEvents} 的区别：</p>
 * <ul>
 *   <li>{@code BuiltInEvents} 中的事件通过 {@code EnchantmentEventRegistrar} 注册，
 *       绑定到具体附魔，分发时扫描实体装备附魔，回调签名 {@code (event, context) -> void}</li>
 *   <li>{@code EnchantLibEvents} 中的事件为全局事件，不绑定到具体附魔，
 *       通过 {@code Event<...>.register(...)} 注册，回调签名 {@code (event) -> void}</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 在 ModInitializer.onInitialize() 中注册
 * EnchantLibEvents.LIVING_ENTITY_TICK.register(event -> {
 *     LivingEntity entity = event.entity();
 *     // 检查焚心标记，造成伤害
 * });
 *
 * EnchantLibEvents.FOOD_REGEN.register(event -> {
 *     if (hasSilentPact(event.player())) {
 *         event.setCancelled(true);
 *     }
 * });
 * }</pre>
 *
 * @since 0.2.0
 */
public final class EnchantLibEvents {

	private EnchantLibEvents() {
	}

	/**
	 * LIVING_ENTITY_TICK 是否已启用（懒挂载标志）。
	 *
	 * <p>默认 false，避免无回调时仍遍历所有实体。example-mod 在注册回调前调用
	 * {@link #enableLivingEntityTick()} 启用，EnchantLib 主类据此决定是否订阅 ServerTickEvents。</p>
	 */
	private static volatile boolean livingEntityTickEnabled = false;

	/**
	 * 启用 LIVING_ENTITY_TICK 事件的遍历分发。
	 *
	 * <p>example-mod 在 {@code onRegisterEventCallbacks} 中注册 LIVING_ENTITY_TICK 回调前应调用此方法，
	 * 否则 EnchantLib 不会订阅 ServerTickEvents.END_SERVER_TICK，回调不会触发。</p>
	 */
	public static void enableLivingEntityTick() {
		livingEntityTickEnabled = true;
	}

	/**
	 * 查询 LIVING_ENTITY_TICK 是否已启用。
	 *
	 * @return true 表示已启用，EnchantLib 会每 tick 遍历所有 LivingEntity 分发事件
	 */
	public static boolean isLivingEntityTickEnabled() {
		return livingEntityTickEnabled;
	}

	/**
	 * 任意 LivingEntity tick 事件回调。
	 */
	@FunctionalInterface
	public interface LivingEntityTickCallback {
		void onTick(LivingEntityTickEvent event);
	}

	/**
	 * 玩家自然回血事件回调。
	 */
	@FunctionalInterface
	public interface FoodRegenCallback {
		void onRegen(FoodRegenEvent event);
	}

	/**
	 * 任意 LivingEntity tick 事件。
	 *
	 * <p>每服务端 tick 对所有加载的 LivingEntity 触发。仅当至少有一个回调注册时才启用遍历。</p>
	 */
	public static final Event<LivingEntityTickCallback> LIVING_ENTITY_TICK =
		EventFactory.createArrayBacked(LivingEntityTickCallback.class, callbacks -> event -> {
			for (LivingEntityTickCallback callback : callbacks) {
				callback.onTick(event);
			}
		});

	/**
	 * 玩家自然回血事件。
	 *
	 * <p>在 FoodData.tick 调用 Player.heal 前触发。回调可通过 {@link FoodRegenEvent#setCancelled(boolean)}
	 * 取消自然回血。</p>
	 */
	public static final Event<FoodRegenCallback> FOOD_REGEN =
		EventFactory.createArrayBacked(FoodRegenCallback.class, callbacks -> event -> {
			for (FoodRegenCallback callback : callbacks) {
				callback.onRegen(event);
			}
		});
}
