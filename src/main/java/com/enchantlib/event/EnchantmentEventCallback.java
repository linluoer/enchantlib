package com.enchantlib.event;

/**
 * 附魔事件回调。
 *
 * <p>开发者通过 {@link EnchantmentEventRegistrar#register(Holder, EnchantmentEventType, EnchantmentEventCallback)}
 * 注册回调，事件分发时由 {@link EnchantmentEventDispatcher} 调用。</p>
 *
 * <p>回调内抛出的异常会被分发器捕获并记录 ERROR 日志，不会影响其他附魔的回调或原版逻辑
 * （异常隔离原则，详见验收标准）。</p>
 *
 * @param <E> 事件类型
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface EnchantmentEventCallback<E extends EnchantmentEvent> {

	/**
	 * 事件被触发时调用。
	 *
	 * @param event   事件对象（同一分发过程中所有回调共享同一实例）
	 * @param context 当前附魔的上下文（每个附魔独立一份）
	 */
	void onEvent(E event, EnchantmentContext context);
}
