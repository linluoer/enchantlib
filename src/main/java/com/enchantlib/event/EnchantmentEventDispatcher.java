package com.enchantlib.event;

import com.enchantlib.EnchantLib;
import com.enchantlib.debug.DebugLogger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 附魔事件分发器。
 *
 * <p>核心职责：</p>
 * <ol>
 *   <li><b>注册中心</b>：按附魔 + 事件类型注册回调（携带 {@link TriggerPolicy}）</li>
 *   <li><b>聚合分发</b>：扫描实体所有装备槽位的附魔，对每个附魔独立触发回调</li>
 *   <li><b>触发策略</b>：在 dispatch 入口层判定 {@link TriggerPolicy}，不满足阈值的附魔在分发前被跳过</li>
 *   <li><b>异常隔离</b>：单个回调抛异常不会影响其他附魔的回调或原版逻辑（验收标准）</li>
 * </ol>
 *
 * <h2>分发流程</h2>
 * <pre>{@code
 * EnchantmentEventDispatcher.dispatch(POST_ATTACK, event, attacker);
 *   └─ scan(attacker) → List<EnchantmentContext>   // 扫描所有装备附魔
 *   └─ for each context:
 *        └─ 查找 (context.enchantment, POST_ATTACK) 的 CallbackEntry 列表
 *        └─ for each entry:
 *             └─ 若 event 是 ChargeableEvent：
 *                  └─ charge = event.charge()
 *                  └─ if (!entry.policy.shouldTrigger(charge)) continue;   // 阈值门控
 *                  └─ scaledLevel = entry.policy.scaleLevel(ctx.level(), charge)  // 等级缩放
 *                  └─ 构造新的 EnchantmentContext（携带 scaledLevel）
 *             └─ try { callback.onEvent(event, scaledCtx) }
 *                catch (Throwable t) { LOGGER.error(...); }   // 异常隔离
 * }</pre>
 *
 * <h2>触发策略作用范围</h2>
 * <ul>
 *   <li>仅对实现了 {@link ChargeableEvent} 的事件（如 {@link BuiltInEvents.PostAttackEvent}）生效</li>
 *   <li>其他事件类型（ENTITY_TICK、POST_HURT、POST_KILL、MODIFY_DAMAGE、PROJECTILE_HIT）忽略策略，始终触发</li>
 *   <li>策略可通过配置文件覆盖，详见 {@link TriggerPolicy}</li>
 * </ul>
 *
 * <h2>异常隔离保证</h2>
 * <ul>
 *   <li>单个附魔的回调异常不会影响其他附魔的回调</li>
 *   <li>单个回调异常不会影响同一附魔的其他回调</li>
 *   <li>异常被记录为 ERROR 日志（含附魔 ID、事件类型、异常栈），不传播到调用方</li>
 *   <li>原版逻辑不受任何影响（dispatch 方法本身不抛异常）</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class EnchantmentEventDispatcher {

	/**
	 * 回调注册项：回调 + 触发策略。
	 *
	 * @param callback 回调
	 * @param policy   触发策略（null 表示 {@link TriggerPolicy#IGNORE}）
	 * @param <E>      事件类型
	 */
	public record CallbackEntry<E extends EnchantmentEvent>(
		EnchantmentEventCallback<E> callback,
		TriggerPolicy policy) {
	}

	/**
	 * 注册表：附魔 Holder + 事件类型 → 回调条目列表。
	 *
	 * <p>使用 ConcurrentHashMap + CopyOnWriteArrayList 保证线程安全。
	 * 回调在启动时通过 entrypoint 注册，运行时只读，但仍用并发容器以防意外修改。</p>
	 */
	private static final Map<Holder<Enchantment>, Map<EnchantmentEventType<?>, List<CallbackEntry<?>>>> REGISTRY =
		new ConcurrentHashMap<>();

	/**
	 * 全局事件类型位掩码：记录哪些事件类型已注册至少一个回调。
	 *
	 * <p>每个 {@link EnchantmentEventType} 占一位（{@code 1 << index}）。
	 * 注册回调时通过位或操作累加；Mixins 在热路径入口通过
	 * {@link #hasCallbacks(EnchantmentEventType)} 快速判断是否需要执行
	 * 昂贵的快照计算和扫描，实现"未安装实现模组时零开销"。</p>
	 *
	 * <p>使用 volatile 保证可见性：注册阶段在主线程，运行时读取在服务器线程。</p>
	 */
	private static volatile int activeEventTypeMask = 0;

	private EnchantmentEventDispatcher() {
	}

	/**
	 * 注册一个事件回调（默认 {@link TriggerPolicy#IGNORE} 策略，向后兼容）。
	 *
	 * @param enchantment 目标附魔 Holder
	 * @param type        事件类型
	 * @param callback    回调
	 * @param <E>         事件类型
	 */
	public static <E extends EnchantmentEvent> void register(
		Holder<Enchantment> enchantment,
		EnchantmentEventType<E> type,
		EnchantmentEventCallback<E> callback) {
		register(enchantment, type, callback, TriggerPolicy.IGNORE);
	}

	/**
	 * 注册一个事件回调（携带触发策略）。
	 *
	 * <p>应在启动阶段（entrypoint 收集时）调用。同一附魔 + 同一事件类型可注册多个回调，
	 * 按注册顺序执行。每个回调独立携带策略，配置覆盖时按附魔 ID 整体覆盖（影响该附魔的所有回调）。</p>
	 *
	 * @param enchantment 目标附魔 Holder
	 * @param type        事件类型
	 * @param callback    回调
	 * @param policy      触发策略（null 视为 {@link TriggerPolicy#IGNORE}）
	 * @param <E>         事件类型
	 */
	public static <E extends EnchantmentEvent> void register(
		Holder<Enchantment> enchantment,
		EnchantmentEventType<E> type,
		EnchantmentEventCallback<E> callback,
		TriggerPolicy policy) {
		if (enchantment == null || type == null || callback == null) {
			EnchantLib.LOGGER.warn("[EnchantLib] 注册事件回调失败：参数为 null");
			return;
		}
		TriggerPolicy effectivePolicy = policy != null ? policy : TriggerPolicy.IGNORE;
		REGISTRY
			.computeIfAbsent(enchantment, k -> new ConcurrentHashMap<>())
			.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>())
			.add(new CallbackEntry<>(callback, effectivePolicy));
		// 更新全局事件类型位掩码（位或操作，幂等）
		activeEventTypeMask |= type.bit();
	}

	/**
	 * 快速判断指定事件类型是否已注册任何回调。
	 *
	 * <p>用于 Mixin 热路径入口短路：若返回 false，可跳过昂贵的快照计算、
	 * 扫描和事件构造。仅一次 volatile int 读取 + 位与运算，开销极低。</p>
	 *
	 * @param type 事件类型
	 * @return true 若至少有一个回调已注册
	 */
	public static boolean hasCallbacks(EnchantmentEventType<?> type) {
		return (activeEventTypeMask & type.bit()) != 0;
	}

	/**
	 * 快速判断指定附魔是否注册了任何事件回调（O2 REGISTRY 交集优化）。
	 *
	 * <p>用于 {@link EnchantmentScanner#scan} 中跳过无回调的附魔（如原版锋利、保护），
	 * 避免为无回调附魔构造 {@link EnchantmentContext}。</p>
	 *
	 * @param enchantment 附魔 Holder
	 * @return true 若该附魔至少有一个事件回调
	 */
	public static boolean hasEnchantmentCallbacks(Holder<Enchantment> enchantment) {
		return REGISTRY.containsKey(enchantment);
	}

	/**
	 * 分发事件。
	 *
	 * <p>扫描实体所有装备槽位的附魔，对每个附魔查找其注册的回调并依次触发。
	 * 对 {@link ChargeableEvent} 事件，应用 {@link TriggerPolicy} 进行阈值门控和等级缩放。
	 * 任何回调抛出的异常都会被捕获并记录，不影响其他回调或调用方。</p>
	 *
	 * @param type   事件类型
	 * @param event  事件对象
	 * @param entity 要扫描装备的实体
	 * @param <E>    事件类型
	 */
	public static <E extends EnchantmentEvent> void dispatch(
		EnchantmentEventType<E> type,
		E event,
		LivingEntity entity) {
		if (type == null || event == null || entity == null) {
			return;
		}

		// O1 位掩码短路：若该事件类型无任何回调，跳过 scan() 和后续处理
		// 这是一次 volatile int 读取 + 位与运算，开销极低
		if (!hasCallbacks(type)) {
			return;
		}

		// 顶层 try-catch：确保 dispatch 永不向调用方（Mixin/原版逻辑）传播异常
		// 正常情况下 scan() 和基础设施代码不会抛异常，此 try-catch 是防御性兜底
		try {
			dispatchInternal(type, event, entity);
		} catch (Throwable t) {
			EnchantLib.LOGGER.error(
				"[EnchantLib] 事件分发基础设施异常（非回调异常）| 事件: {} | 实体: {}",
				type.id(),
				entity.getId(),
				t);
		}
	}

	/**
	 * dispatch 的内部实现，由 {@link #dispatch} 的顶层 try-catch 包裹。
	 *
	 * <p>分离目的是让顶层 try-catch 只捕获基础设施异常（scan/迭代），
	 * 回调异常由内部的 per-callback try-catch 独立处理。</p>
	 */
	@SuppressWarnings("unchecked")
	private static <E extends EnchantmentEvent> void dispatchInternal(
		EnchantmentEventType<E> type,
		E event,
		LivingEntity entity) {
		// 1. 扫描实体装备附魔（O2/O3/O5 优化：槽位过滤 + REGISTRY 交集 + 延迟分配）
		List<EnchantmentContext> contexts = EnchantmentScanner.scan(entity, type);

		// 诊断日志：ENTITY_TICK 每 200 tick 输出扫描结果
		if (type == BuiltInEvents.ENTITY_TICK && entity.tickCount % 200 == 0) {
			EnchantLib.LOGGER.info("[EnchantLib-Diag] ENTITY_TICK dispatch: 实体={} 附魔上下文数={}",
				entity.getName().getString(), contexts.size());
		}

		// 诊断日志：POST_ATTACK/MODIFY_DAMAGE/MODIFY_BLOCK_DROPS 每 100 tick 节流输出
		// 用于确认事件已到达 dispatch 阶段，以及 scan() 是否找到附魔上下文
		if ((type == BuiltInEvents.POST_ATTACK
				|| type == BuiltInEvents.MODIFY_DAMAGE
				|| type == BuiltInEvents.MODIFY_BLOCK_DROPS)
			&& entity.tickCount % 100 == 0) {
			DebugLogger.log("dispatchInternal 节流诊断: 事件={} 实体={} 附魔上下文数={}",
				type.id(), entity.getName().getString(), contexts.size());
		}

		if (contexts.isEmpty()) {
			return;
		}

		// 预提取充能比例（仅 ChargeableEvent 事件有效）
		final float charge;
		if (event instanceof ChargeableEvent ce) {
			charge = ce.charge();
		} else {
			charge = 1.0f; // 非 ChargeableEvent 事件，策略不生效
		}
		final boolean isChargeable = event instanceof ChargeableEvent;

		// 2. 对每个附魔上下文独立触发回调
		for (EnchantmentContext context : contexts) {
			Map<EnchantmentEventType<?>, List<CallbackEntry<?>>> enchantmentCallbacks =
				REGISTRY.get(context.enchantment());
			if (enchantmentCallbacks == null) {
				continue;
			}
			List<CallbackEntry<?>> entries = enchantmentCallbacks.get(type);
			if (entries == null || entries.isEmpty()) {
				continue;
			}

			// 3. 依次调用回调，每个回调独立 try-catch（Q3 异常隔离）
			for (CallbackEntry<?> entry : entries) {
				TriggerPolicy policy = entry.policy();

				// 触发策略判定（仅 ChargeableEvent 事件）
				if (isChargeable && !policy.shouldTrigger(charge)) {
					continue;
				}

				// 等级缩放（若策略启用 SCALED）
				EnchantmentContext effectiveContext = context;
				if (isChargeable) {
					int scaledLevel = policy.scaleLevel(context.level(), charge);
					if (scaledLevel != context.level()) {
						effectiveContext = new EnchantmentContext(
							context.enchantment(),
							scaledLevel,
							context.itemStack(),
							context.slot());
					}
				}

				try {
					((CallbackEntry<E>) entry).callback().onEvent(event, effectiveContext);
				} catch (Throwable t) {
					// Q3 异常隔离：单个回调异常不影响其他回调或原版逻辑
					// 日志包含：附魔 ID（含来源模组 namespace）、事件类型、槽位、回调类名
					String enchantmentId = context.enchantmentId();
					String sourceMod = extractNamespace(enchantmentId);
					EnchantLib.LOGGER.error(
						"[EnchantLib] 附魔事件回调异常 | 来源模组: {} | 附魔: {} | 事件: {} | 槽位: {} | 回调: {}",
						sourceMod,
						enchantmentId,
						type.id(),
						context.slot().getName(),
						entry.callback().getClass().getName(),
						t);
				}
			}
		}
	}

	/**
	 * 分发交互类事件（ITEM_USE/BLOCK_USE/ENTITY_USE）。
	 *
	 * <p>与 {@link #dispatch} 的区别：</p>
	 * <ol>
	 *   <li><b>按指定槽位扫描</b>：仅扫描触发手（MAINHAND 或 OFFHAND），不扫描所有 relevantSlots</li>
	 *   <li><b>取消语义</b>：回调可通过 {@link InteractionEnchantmentEvent#setResult(InteractionResult)}
	 *       设置非 PASS 结果，第一个非 PASS 结果生效并停止后续分发</li>
	 *   <li><b>返回结果</b>：返回最终 InteractionResult，调用方据此决定是否干预原版行为</li>
	 * </ol>
	 *
	 * <p>交互事件不实现 {@link ChargeableEvent}，TriggerPolicy 不生效，所有回调始终触发。
	 * 异常隔离与 {@link #dispatch} 一致（per-callback try-catch + 顶层 try-catch）。</p>
	 *
	 * @param type   事件类型
	 * @param event  事件对象（必须实现 {@link InteractionEnchantmentEvent}）
	 * @param entity 要扫描装备的实体
	 * @param slot   要扫描的装备槽位（触发手对应的槽位）
	 * @param <E>    事件类型
	 * @return 最终交互结果（PASS 表示不干预原版行为）
	 */
	@SuppressWarnings("unchecked")
	public static <E extends InteractionEnchantmentEvent> InteractionResult dispatchInteraction(
		EnchantmentEventType<E> type,
		E event,
		LivingEntity entity,
		EquipmentSlot slot) {
		if (type == null || event == null || entity == null || slot == null) {
			return event != null ? event.result() : InteractionResult.PASS;
		}

		// O1 位掩码短路
		if (!hasCallbacks(type)) {
			return event.result();
		}

		try {
			// 仅扫描指定槽位（O2/O3 优化）
			List<EnchantmentContext> contexts = EnchantmentScanner.scanSlot(entity, slot);
			if (contexts.isEmpty()) {
				return event.result();
			}

			for (EnchantmentContext context : contexts) {
				Map<EnchantmentEventType<?>, List<CallbackEntry<?>>> enchantmentCallbacks =
					REGISTRY.get(context.enchantment());
				if (enchantmentCallbacks == null) {
					continue;
				}
				List<CallbackEntry<?>> entries = enchantmentCallbacks.get(type);
				if (entries == null || entries.isEmpty()) {
					continue;
				}

				for (CallbackEntry<?> entry : entries) {
					// 交互事件不是 ChargeableEvent，跳过 TriggerPolicy 判定
					try {
						((CallbackEntry<E>) entry).callback().onEvent(event, context);
					} catch (Throwable t) {
						// Q3 异常隔离
						String enchantmentId = context.enchantmentId();
						String sourceMod = extractNamespace(enchantmentId);
						EnchantLib.LOGGER.error(
							"[EnchantLib] 交互事件回调异常 | 来源模组: {} | 附魔: {} | 事件: {} | 槽位: {} | 回调: {}",
							sourceMod,
							enchantmentId,
							type.id(),
							context.slot().getName(),
							entry.callback().getClass().getName(),
							t);
					}

					// 取消语义：第一个非 PASS 结果生效并停止后续分发
					if (event.result() != InteractionResult.PASS) {
						return event.result();
					}
				}
			}
		} catch (Throwable t) {
			EnchantLib.LOGGER.error(
				"[EnchantLib] 交互事件分发基础设施异常 | 事件: {} | 实体: {}",
				type.id(),
				entity.getId(),
				t);
		}

		return event.result();
	}

	/**
	 * 从附魔 ID 提取来源模组 namespace（冒号前的部分）。
	 *
	 * @param enchantmentId 附魔 ID（形如 "modid:name"）
	 * @return namespace，若格式异常返回 "unknown"
	 */
	private static String extractNamespace(String enchantmentId) {
		int colon = enchantmentId.indexOf(':');
		return colon > 0 ? enchantmentId.substring(0, colon) : "unknown";
	}

	/**
	 * 获取已注册的附魔数量（用于调试/统计）。
	 *
	 * @return 已注册回调的附魔数量
	 */
	public static int registeredEnchantments() {
		return REGISTRY.size();
	}

	/**
	 * 获取指定附魔已注册的回调总数（所有事件类型）。
	 *
	 * @param enchantment 附魔 Holder
	 * @return 回调总数
	 */
	public static int callbackCount(Holder<Enchantment> enchantment) {
		Map<EnchantmentEventType<?>, List<CallbackEntry<?>>> map = REGISTRY.get(enchantment);
		if (map == null) return 0;
		int count = 0;
		for (List<CallbackEntry<?>> entries : map.values()) {
			count += entries.size();
		}
		return count;
	}

	/**
	 * 获取指定事件类型已注册的回调总数（所有附魔）。
	 *
	 * @param type 事件类型
	 * @return 回调总数
	 */
	public static int callbackCount(EnchantmentEventType<?> type) {
		int count = 0;
		for (Map<EnchantmentEventType<?>, List<CallbackEntry<?>>> map : REGISTRY.values()) {
			List<CallbackEntry<?>> entries = map.get(type);
			if (entries != null) {
				count += entries.size();
			}
		}
		return count;
	}

	/**
	 * 获取所有已注册的附魔 Holder（不可变视图）。
	 *
	 * @return 附魔 Holder 列表
	 */
	public static List<Holder<Enchantment>> registeredEnchantmentHolders() {
		return Collections.unmodifiableList(new ArrayList<>(REGISTRY.keySet()));
	}

	/**
	 * 对指定附魔的所有回调应用新的触发策略（配置覆盖用）。
	 *
	 * <p>遍历该附魔在所有事件类型下注册的 CallbackEntry，替换其 policy。
	 * 用于在 entrypoint 收集完成后，从 {@code trigger.toml} 加载配置覆盖。</p>
	 *
	 * <p>由于 {@link CallbackEntry} 是 record（不可变），此方法会重建列表。
	 * 应仅在启动阶段调用，不要在运行时频繁调用。</p>
	 *
	 * @param enchantment 目标附魔 Holder
	 * @param newPolicy   新策略
	 */
	@SuppressWarnings("unchecked")
	public static void applyPolicyOverride(Holder<Enchantment> enchantment, TriggerPolicy newPolicy) {
		Map<EnchantmentEventType<?>, List<CallbackEntry<?>>> map = REGISTRY.get(enchantment);
		if (map == null) {
			return;
		}
		TriggerPolicy effectivePolicy = newPolicy != null ? newPolicy : TriggerPolicy.IGNORE;
		for (Map.Entry<EnchantmentEventType<?>, List<CallbackEntry<?>>> entry : map.entrySet()) {
			List<CallbackEntry<?>> oldList = entry.getValue();
			List<CallbackEntry<?>> newList = new ArrayList<>(oldList.size());
			for (CallbackEntry<?> ce : oldList) {
				newList.add(new CallbackEntry<>((EnchantmentEventCallback<?>) ce.callback(), effectivePolicy));
			}
			// CopyOnWriteArrayList 写时复制，安全替换
			entry.setValue(new CopyOnWriteArrayList<>(newList));
		}
	}

	/**
	 * 获取指定附魔在指定事件类型下的策略（取第一个回调的策略，配置覆盖后所有回调策略一致）。
	 *
	 * <p>用于调试输出和指令查询。</p>
	 *
	 * @param enchantment 附魔 Holder
	 * @param type        事件类型
	 * @return 策略，若无回调返回 null
	 */
	public static TriggerPolicy getPolicy(Holder<Enchantment> enchantment, EnchantmentEventType<?> type) {
		Map<EnchantmentEventType<?>, List<CallbackEntry<?>>> map = REGISTRY.get(enchantment);
		if (map == null) return null;
		List<CallbackEntry<?>> entries = map.get(type);
		if (entries == null || entries.isEmpty()) return null;
		return entries.get(0).policy();
	}
}
