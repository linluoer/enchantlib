package com.enchantlib.event;

import com.enchantlib.EnchantLib;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * 附魔事件类型（类型安全键）。
 *
 * <p>每个事件类型由唯一 {@link Identifier} 标识，并绑定一个事件类 {@code E}。</p>
 *
 * <p>类型安全设计：注册回调和分发事件都必须使用同一 {@code EnchantmentEventType<E>}，
 * 编译期即可防止类型不匹配。</p>
 *
 * <h2>位掩码短路</h2>
 * <p>每个事件类型在创建时分配一个唯一的 bit 位（{@link #bit()}），用于
 * {@link EnchantmentEventDispatcher} 的全局位掩码短路：Mixins 在热路径入口
 * 检查 {@code EnchantmentEventDispatcher.hasCallbacks(type)}，若无任何回调
 * 则跳过昂贵的快照计算和扫描，实现"未安装实现模组时零开销"。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 1. 定义事件类
 * public record PostAttackEvent(ServerLevel level, LivingEntity attacker,
 *                               LivingEntity target, DamageSource source) implements EnchantmentEvent {
 *     @Override public LivingEntity getEntity() { return attacker; }
 *     @Override public ServerLevel getLevel() { return level; }
 * }
 *
 * // 2. 声明事件类型
 * public static final EnchantmentEventType<PostAttackEvent> POST_ATTACK =
 *     EnchantmentEventType.create(Identifier.of("enchantlib", "post_attack"));
 *
 * // 3. 注册回调
 * registrar.register(enchantmentHolder, POST_ATTACK, (event, ctx) -> {
 *     // 处理逻辑
 * });
 *
 * // 4. 分发事件
 * EnchantmentEventDispatcher.dispatch(POST_ATTACK, event, attacker);
 * }</pre>
 *
 * @param <E> 事件类
 *
 * @since 0.1.0
 */
public final class EnchantmentEventType<E extends EnchantmentEvent> {

	private static final Map<Identifier, EnchantmentEventType<?>> REGISTERED = new HashMap<>();

	/** bit 位分配计数器，从 0 递增（最多支持 32 个事件类型） */
	private static final AtomicInteger NEXT_BIT_INDEX = new AtomicInteger(0);

	private final Identifier id;
	private final Class<E> eventClass;
	/** 该事件类型对应的位掩码位（1 << index） */
	private final int bit;
	/**
	 * 该事件类型关注的装备槽位集合（O5 槽位声明优化）。
	 *
	 * <p>扫描器只遍历这些槽位，避免扫描无关槽位。默认为所有槽位（{@link EquipmentSlot#VALUES}），
	 * 可通过 {@link #setRelevantSlots(EnumSet)} 收窄。</p>
	 *
	 * <p>存储为不可变视图，防止调用方通过 {@link #relevantSlots()} 返回值意外修改内部状态。</p>
	 */
	private Set<EquipmentSlot> relevantSlots = Collections.unmodifiableSet(EnumSet.allOf(EquipmentSlot.class));

	private EnchantmentEventType(Identifier id, Class<E> eventClass) {
		this.id = id;
		this.eventClass = eventClass;
		int index = NEXT_BIT_INDEX.getAndIncrement();
		if (index >= 32) {
			throw new IllegalStateException("事件类型数量超过 32 个位掩码上限");
		}
		this.bit = 1 << index;
	}

	/**
	 * 创建并注册一个事件类型。
	 *
	 * @param id         事件类型唯一标识
	 * @param eventClass 事件类
	 * @param <E>        事件类型
	 * @return 事件类型实例
	 * @throws IllegalStateException 若 id 已被注册
	 */
	public static <E extends EnchantmentEvent> EnchantmentEventType<E> create(
		Identifier id, Class<E> eventClass) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(eventClass, "eventClass");
		synchronized (REGISTERED) {
			if (REGISTERED.containsKey(id)) {
				throw new IllegalStateException("事件类型已注册: " + id);
			}
			EnchantmentEventType<E> type = new EnchantmentEventType<>(id, eventClass);
			REGISTERED.put(id, type);
			EnchantLib.LOGGER.debug("[EnchantLib] 注册事件类型: {} -> {}", id, eventClass.getSimpleName());
			return type;
		}
	}

	/**
	 * 创建并注册一个事件类型（自动推断事件类）。
	 *
	 * <p>由于泛型擦除，事件类信息在运行时不可用，仅用于调试日志。如需精确类型检查，
	 * 请使用 {@link #create(Identifier, Class)}。</p>
	 *
	 * @param id 事件类型唯一标识
	 * @param <E> 事件类型
	 * @return 事件类型实例
	 */
	@SuppressWarnings("unchecked")
	public static <E extends EnchantmentEvent> EnchantmentEventType<E> create(Identifier id) {
		return create(id, (Class<E>) EnchantmentEvent.class);
	}

	/**
	 * 获取事件类型标识。
	 *
	 * @return 标识
	 */
	public Identifier id() {
		return id;
	}

	/**
	 * 获取事件类。
	 *
	 * @return 事件类
	 */
	public Class<E> eventClass() {
		return eventClass;
	}

	/**
	 * 获取该事件类型的位掩码位（1 << index）。
	 *
	 * <p>用于 {@link EnchantmentEventDispatcher#hasCallbacks(EnchantmentEventType)}
	 * 全局位掩码短路，在 Mixin 热路径入口快速判断是否有任何回调注册。</p>
	 *
	 * @return 位掩码位
	 */
	public int bit() {
		return bit;
	}

	/**
	 * 获取该事件类型关注的装备槽位集合（O5 槽位声明优化）。
	 *
	 * <p>扫描器只遍历这些槽位，避免扫描无关槽位（如 POST_ATTACK 只需扫描主手/副手，
	 * POST_HURT 只需扫描护甲 + 副手）。默认为所有槽位。</p>
	 *
	 * @return 装备槽位集合（不可变视图，调用方不应尝试修改）
	 */
	public Set<EquipmentSlot> relevantSlots() {
		return relevantSlots;
	}

	/**
	 * 设置该事件类型关注的装备槽位集合。
	 *
	 * <p>应在事件类型声明后立即调用（静态初始化阶段），不要在运行时修改。
	 * 传入 null 视为重置为所有槽位。内部会存储不可变副本，调用方后续修改传入集合不影响本对象。</p>
	 *
	 * @param slots 装备槽位集合
	 */
	public void setRelevantSlots(EnumSet<EquipmentSlot> slots) {
		this.relevantSlots = slots != null
			? Collections.unmodifiableSet(EnumSet.copyOf(slots))
			: Collections.unmodifiableSet(EnumSet.allOf(EquipmentSlot.class));
	}

	@Override
	public String toString() {
		return "EnchantmentEventType[" + id + "]";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof EnchantmentEventType<?> other)) return false;
		return id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}
}
