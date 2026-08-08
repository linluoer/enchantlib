package com.enchantlib.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 玩家自然回血事件（全局事件，由 FoodData.tick 的 heal 调用拦截触发）。
 *
 * <p>当玩家饱食度足够（foodLevel >= 18）且可自然回血时，MC 原版每 tick 调用
 * {@code Player.heal(1.0F / 80.0F)} 进行自然回血。此事件在该 heal 调用前触发，
 * 允许附魔取消自然回血（典型用途：静默契约类附魔压制自然回血，迫使玩家通过击杀回血）。</p>
 *
 * <h2>注册方式</h2>
 * <p>全局事件，通过 {@link EnchantLibEvents#FOOD_REGEN} 注册：</p>
 * <pre>{@code
 * EnchantLibEvents.FOOD_REGEN.register(event -> {
 *     if (playerHasSilentPact(event.player())) {
 *         event.setCancelled(true); // 取消自然回血
 *     }
 * });
 * }</pre>
 *
 * <h2>设计要点</h2>
 * <ul>
 *   <li><b>仅取消自然回血</b>：药水治疗、金苹果、信标等主动恢复手段不受影响</li>
 *   <li><b>饥饿消耗照常</b>：取消 heal 调用不会跳过 FoodData.tick 的其他逻辑，饱食度仍正常消耗</li>
 *   <li><b>仅服务端</b>：自然回血只在服务端计算，客户端无需处理</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class FoodRegenEvent implements EnchantmentEvent {

	private final ServerPlayer player;
	private final float originalAmount;
	private boolean cancelled;

	public FoodRegenEvent(ServerPlayer player, float originalAmount) {
		this.player = player;
		this.originalAmount = originalAmount;
	}

	/**
	 * 获取触发自然回血的玩家。
	 *
	 * @return 玩家
	 */
	public ServerPlayer player() {
		return player;
	}

	/**
	 * 获取原始回血量（MC 26.2 默认 1/80 = 0.0125 HP/tick，约每 4 秒 1 HP）。
	 *
	 * @return 回血量
	 */
	public float originalAmount() {
		return originalAmount;
	}

	/**
	 * 取消本次自然回血。
	 */
	public void setCancelled(boolean cancelled) {
		this.cancelled = cancelled;
	}

	/**
	 * 是否取消本次自然回血。
	 *
	 * @return true 表示取消
	 */
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public LivingEntity getEntity() {
		return player;
	}

	@Override
	public ServerLevel getLevel() {
		return (ServerLevel) player.level();
	}
}
