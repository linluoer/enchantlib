package com.enchantlib.api;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/**
 * 实体计数器 API。
 *
 * <p>提供线程安全的、命名空间隔离的计数器存储,供子 Mod 在事件回调中使用。</p>
 *
 * <h2>解决的问题</h2>
 * <p>多个 Mod 若各自维护 {@code ConcurrentHashMap<UUID, Integer>} 存储计数器,存在以下问题:</p>
 * <ul>
 *   <li>生命周期管理不统一(玩家离线时谁负责清理?)</li>
 *   <li>内存浪费(每个 Mod 维护自己的 Map)</li>
 *   <li>无法跨 Mod 共享计数(A Mod 想读 B Mod 的累计伤害)</li>
 * </ul>
 *
 * <p>本 API 通过命名空间化的 {@link Identifier} 作为 key,自动隔离不同 Mod 的计数器,
 * 并由 EnchantLib 统一管理生命周期(玩家离线时自动清除所有计数器)。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 定义计数器 key（用 Mod ID 作为命名空间,避免冲突）
 * private static final Identifier ATTACK_COUNT = Identifier.of("mymod", "attack_count");
 * private static final Identifier DAMAGE_DEALT = Identifier.of("mymod", "damage_sold");
 *
 * // POST_ATTACK 回调中累计攻击次数和伤害
 * public static void onAttack(BuiltInEvents.PostAttackEvent event, EnchantmentContext ctx) {
 *     LivingEntity attacker = event.attacker();
 *     EntityCounter.increment(attacker, ATTACK_COUNT);
 *     EntityCounter.addAndGet(attacker, DAMAGE_DEALT, (int) event.amount());
 *
 *     // 每 5 次攻击触发特效
 *     if (EntityCounter.checkAndReset(attacker, ATTACK_COUNT, 5)) {
 *         attacker.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 100, 0));
 *     }
 * }
 * }</pre>
 *
 * <h2>支持的计数场景</h2>
 * <ul>
 *   <li><b>攻击计数</b>:POST_ATTACK 中 increment,达到阈值触发效果</li>
 *   <li><b>受击计数</b>:POST_HURT 中 increment</li>
 *   <li><b>累计造成伤害</b>:MODIFY_DAMAGE/POST_ATTACK 中 add(damage)</li>
 *   <li><b>累计受到伤害</b>:POST_HURT 中 add(amount)</li>
 *   <li><b>击杀计数</b>:POST_KILL 中 increment</li>
 *   <li><b>自定义计数</b>:任何事件中都可使用</li>
 * </ul>
 *
 * <h2>线程安全</h2>
 * <p>使用 {@link ConcurrentHashMap} + {@link AtomicInteger},所有操作线程安全。
 * 可在多线程事件回调中直接使用,无需额外同步。</p>
 *
 * <h2>生命周期</h2>
 * <ul>
 *   <li><b>懒加载</b>:首次操作时自动创建计数器,无需预注册</li>
 *   <li><b>自动清理</b>:玩家离线时 EnchantLib 自动清除该玩家所有计数器</li>
 *   <li><b>非持久化</b>:服务器重启后计数器重置(不写入存档)</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class EntityCounter {

	/** 实体 UUID → (计数器 key → 值) 的二级映射 */
	private static final ConcurrentHashMap<UUID, ConcurrentHashMap<Identifier, AtomicInteger>> COUNTERS = new ConcurrentHashMap<>();

	private EntityCounter() {
	}

	/**
	 * 获取计数器当前值。若计数器不存在返回 0。
	 *
	 * @param entity 目标实体
	 * @param key    计数器 key（建议用 Mod ID 作为命名空间）
	 * @return 当前值,未初始化时为 0
	 */
	public static int get(LivingEntity entity, Identifier key) {
		return getByUuid(entity.getUUID(), key);
	}

	/**
	 * 设置计数器值。
	 *
	 * @param entity 目标实体
	 * @param key    计数器 key
	 * @param value  新值
	 */
	public static void set(LivingEntity entity, Identifier key, int value) {
		setByUuid(entity.getUUID(), key, value);
	}

	/**
	 * 计数器自增 1,返回新值。
	 *
	 * @param entity 目标实体
	 * @param key    计数器 key
	 * @return 自增后的值
	 */
	public static int increment(LivingEntity entity, Identifier key) {
		return addAndGet(entity, key, 1);
	}

	/**
	 * 计数器增加指定值,返回新值。
	 *
	 * @param entity 目标实体
	 * @param key    计数器 key
	 * @param delta  增量(可为负数)
	 * @return 增加后的值
	 */
	public static int addAndGet(LivingEntity entity, Identifier key, int delta) {
		AtomicInteger counter = getOrCreateCounter(entity.getUUID(), key);
		return counter.addAndGet(delta);
	}

	/**
	 * 重置计数器为 0。
	 *
	 * @param entity 目标实体
	 * @param key    计数器 key
	 */
	public static void reset(LivingEntity entity, Identifier key) {
		set(entity, key, 0);
	}

	/**
	 * 检查计数器是否达到阈值,达到则重置为 0 并返回 true。
	 *
	 * <p>常用于"每 N 次触发一次效果"的场景,避免相位偏移问题。</p>
	 *
	 * @param entity    目标实体
	 * @param key       计数器 key
	 * @param threshold 阈值(>= 1)
	 * @return true 若达到阈值并已重置
	 */
	public static boolean checkAndReset(LivingEntity entity, Identifier key, int threshold) {
		if (threshold <= 0) {
			return true;
		}
		int current = get(entity, key);
		if (current >= threshold) {
			reset(entity, key);
			return true;
		}
		return false;
	}

	/**
	 * 清除实体的所有计数器。
	 *
	 * <p>EnchantLib 会在玩家离线时自动调用此方法。子 Mod 通常无需手动调用。</p>
	 *
	 * @param entity 目标实体
	 */
	public static void clear(LivingEntity entity) {
		COUNTERS.remove(entity.getUUID());
	}

	/**
	 * 获取实体已注册的所有计数器 key（用于调试/统计）。
	 *
	 * @param entity 目标实体
	 * @return key 集合的不可变副本,无计数器时返回空集合
	 */
	public static java.util.Set<Identifier> keys(LivingEntity entity) {
		ConcurrentHashMap<Identifier, AtomicInteger> map = COUNTERS.get(entity.getUUID());
		if (map == null) {
			return java.util.Collections.emptySet();
		}
		return java.util.Collections.unmodifiableSet(new java.util.HashSet<>(map.keySet()));
	}

	// ===== 内部方法（通过 UUID 操作,供 Mixin 或跨线程场景使用）=====

	static int getByUuid(UUID uuid, Identifier key) {
		ConcurrentHashMap<Identifier, AtomicInteger> map = COUNTERS.get(uuid);
		if (map == null) {
			return 0;
		}
		AtomicInteger counter = map.get(key);
		return counter == null ? 0 : counter.get();
	}

	static void setByUuid(UUID uuid, Identifier key, int value) {
		getOrCreateCounter(uuid, key).set(value);
	}

	private static AtomicInteger getOrCreateCounter(UUID uuid, Identifier key) {
		return COUNTERS
			.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
			.computeIfAbsent(key, k -> new AtomicInteger(0));
	}
}
