package com.enchantlib.event;

/**
 * FoodData.tick 执行期间标志,配合静默契约的自然回血压制。
 *
 * <p>由 {@link com.enchantlib.mixin.FoodDataMixin} 在 tick HEAD/RETURN 设置/清除,
 * {@link com.enchantlib.mixin.LivingEntityHealMixin} 在 heal HEAD 读取,
 * 实现"仅在自然回血时触发 FOOD_REGEN 事件"。</p>
 *
 * <p>独立为工具类而非 Mixin 内部静态字段,因为 Mixin 不允许非 private static 方法
 * (会被注入到目标类 FoodData,而 FoodData 不应有 isInFoodTick 方法)。</p>
 *
 * @since 0.2.0
 */
public final class FoodTickTracker {

	private FoodTickTracker() {
	}

	/**
	 * 线程局部标志:当前线程是否正在执行 FoodData.tick。
	 *
	 * <p>仅在同一线程内的 heal 调用才会被 LivingEntityHealMixin 检查,
	 * 避免影响其他线程的 heal 调用(如药水效果线程)。</p>
	 */
	private static final ThreadLocal<Boolean> IN_FOOD_TICK = ThreadLocal.withInitial(() -> false);

	/**
	 * 标记当前线程进入 FoodData.tick。
	 */
	public static void enter() {
		IN_FOOD_TICK.set(true);
	}

	/**
	 * 标记当前线程退出 FoodData.tick。
	 */
	public static void exit() {
		IN_FOOD_TICK.remove();
	}

	/**
	 * 检查当前线程是否正在执行 FoodData.tick。
	 *
	 * @return true 表示当前线程在 FoodData.tick 内(即自然回血流程中)
	 */
	public static boolean isInFoodTick() {
		return IN_FOOD_TICK.get();
	}
}
