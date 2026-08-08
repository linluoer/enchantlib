package com.enchantlib.event;

import net.minecraft.world.InteractionResult;

/**
 * 交互类附魔事件标记接口。
 *
 * <p>实现此接口的事件（{@link BuiltInEvents.ItemUseEvent}、{@link BuiltInEvents.BlockUseEvent}、
 * {@link BuiltInEvents.EntityUseEvent}）携带可变的 {@link InteractionResult}，
 * 允许回调通过 {@link #setResult(InteractionResult)} 中断原版行为。</p>
 *
 * <h2>取消语义</h2>
 * <p>多附魔回调时，{@link EnchantmentEventDispatcher#dispatchInteraction} 按注册顺序依次调用回调。
 * 第一个设置非 {@link InteractionResult#PASS} 结果的回调生效并停止后续分发。
 * 若所有回调都返回 PASS，则事件整体返回 PASS（不干预原版行为）。</p>
 *
 * <p>典型用法（右键释放技能类附魔）：</p>
 * <pre>{@code
 * registrar.register(enchantment, BuiltInEvents.ITEM_USE, (event, ctx) -> {
 *     if (ctx.level() >= 1) {
 *         // 释放技能
 *         event.setResult(InteractionResult.SUCCESS);  // 中断原版右键行为
 *     }
 * });
 * }</pre>
 *
 * @since 0.1.0
 */
public interface InteractionEnchantmentEvent extends EnchantmentEvent {

	/**
	 * 获取当前交互结果。
	 *
	 * @return 当前结果（初始为 {@link InteractionResult#PASS}）
	 */
	InteractionResult result();

	/**
	 * 设置交互结果。
	 *
	 * <p>设置为非 {@link InteractionResult#PASS} 的值将中断后续回调分发，
	 * 并将该结果返回给 Fabric API 回调，从而干预原版行为。</p>
	 *
	 * @param result 新的交互结果
	 */
	void setResult(InteractionResult result);
}
