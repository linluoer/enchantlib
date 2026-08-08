package com.enchantlib.event;

/**
 * 附魔触发策略。
 *
 * <p>控制附魔回调在攻击充能事件（如 {@link BuiltInEvents#POST_ATTACK}）中是否触发，
 * 以及效果强度是否随充能比例缩放。用于防止低充能刷刀全额触发附魔效果，平衡生存服体验。</p>
 *
 * <h2>四种模式</h2>
 * <ul>
 *   <li><b>{@link Mode#IGNORE}</b>：忽略充能，任何充能都全额触发（默认行为，向后兼容）</li>
 *   <li><b>{@link Mode#THRESHOLD}</b>：充能阈值门控，{@code charge < threshold} 时不触发</li>
 *   <li><b>{@link Mode#SCALED}</b>：按充能比例缩放效果等级（{@code scaledLevel = max(1, round(level * charge))}）</li>
 *   <li><b>{@link Mode#THRESHOLD_SCALED}</b>：阈值门控 + 缩放（先过阈值再缩放）</li>
 * </ul>
 *
 * <h2>作用范围</h2>
 * <p>仅对实现了 {@link ChargeableEvent} 接口的事件生效（如 {@link BuiltInEvents.PostAttackEvent}）。
 * 其他事件类型（ENTITY_TICK、POST_HURT、POST_KILL、MODIFY_DAMAGE 等）忽略策略，始终触发。</p>
 *
 * <h2>配置覆盖</h2>
 * <p>注册时携带默认 policy，{@code config/enchantlib/trigger.toml} 可按附魔 ID 覆盖：</p>
 * <pre>{@code
 * # 全局默认阈值（THRESHOLD/THRESHOLD_SCALED 模式未配置附魔时使用）
 * force_threshold_min = 0.5
 *
 * [mymod.demo]
 * mode = "THRESHOLD"
 * threshold = 0.7
 * }</pre>
 *
 * @since 0.1.0
 */
public final class TriggerPolicy {

	/**
	 * 触发模式。
	 */
	public enum Mode {
		/** 忽略充能，任何充能都全额触发（默认） */
		IGNORE,
		/** 充能阈值门控，低于阈值不触发 */
		THRESHOLD,
		/** 按充能比例缩放效果等级 */
		SCALED,
		/** 阈值门控 + 缩放 */
		THRESHOLD_SCALED
	}

	/** 默认策略：忽略充能（向后兼容） */
	public static final TriggerPolicy IGNORE = new TriggerPolicy(Mode.IGNORE, 0.0f);

	private final Mode mode;
	private final float threshold;

	/**
	 * 构造触发策略。
	 *
	 * @param mode      模式
	 * @param threshold 阈值（0.0~1.0，仅 THRESHOLD/THRESHOLD_SCALED 模式生效）
	 */
	public TriggerPolicy(Mode mode, float threshold) {
		this.mode = mode;
		this.threshold = Math.max(0.0f, Math.min(1.0f, threshold));
	}

	/**
	 * 获取模式。
	 *
	 * @return 模式
	 */
	public Mode mode() {
		return mode;
	}

	/**
	 * 获取阈值。
	 *
	 * @return 阈值（0.0~1.0）
	 */
	public float threshold() {
		return threshold;
	}

	/**
	 * 判断当前充能比例是否应触发回调。
	 *
	 * <p>对非 {@link ChargeableEvent} 事件，应在外部直接放行（不调用此方法）。</p>
	 *
	 * @param charge 充能比例（0.0~1.0）
	 * @return true 若应触发
	 */
	public boolean shouldTrigger(float charge) {
		return switch (mode) {
			case IGNORE, SCALED -> true;
			case THRESHOLD, THRESHOLD_SCALED -> charge >= threshold;
		};
	}

	/**
	 * 按策略缩放附魔等级。
	 *
	 * <p>仅 SCALED/THRESHOLD_SCALED 模式生效，其他模式原样返回。</p>
	 *
	 * @param level  原始等级
	 * @param charge 充能比例（0.0~1.0）
	 * @return 缩放后的等级（至少为 1）
	 */
	public int scaleLevel(int level, float charge) {
		if (mode == Mode.SCALED || mode == Mode.THRESHOLD_SCALED) {
			return Math.max(1, Math.round(level * charge));
		}
		return level;
	}

	@Override
	public String toString() {
		return "TriggerPolicy{mode=" + mode + ", threshold=" + threshold + "}";
	}
}
