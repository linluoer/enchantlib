package com.enchantlib.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * 装备槽位扫描器。
 *
 * <p>扫描实体的装备槽位，聚合所有附魔及其上下文（物品、槽位、等级）。
 * 事件分发器使用此扫描结果对每个附魔独立触发回调。</p>
 *
 * <h2>性能优化（O2/O3/O5）</h2>
 * <ul>
 *   <li><b>O5 槽位声明</b>：按事件类型的 {@link EnchantmentEventType#relevantSlots()}
 *       只遍历关注槽位（如 POST_ATTACK 只扫主手/副手，POST_HURT 只扫护甲+副手），
 *       避免扫描无关槽位</li>
 *   <li><b>O2 REGISTRY 交集</b>：只为已注册回调的附魔构造 {@link EnchantmentContext}，
 *       跳过原版附魔（如锋利、保护）等无回调附魔，避免无意义堆分配</li>
 *   <li><b>O3 延迟分配</b>：{@code ArrayList} 在首次命中时才分配，空结果返回
 *       {@link List#of()}，实现"无附魔装备玩家零堆分配"</li>
 * </ul>
 *
 * <h2>扫描策略</h2>
 * <ul>
 *   <li>遍历 {@link EnchantmentEventType#relevantSlots()} 声明的槽位</li>
 *   <li>使用 {@link LivingEntity#canUseSlot(EquipmentSlot)} 过滤（如羊驼无 MAINHAND）</li>
 *   <li>使用 {@link LivingEntity#getItemBySlot(EquipmentSlot)} 获取物品</li>
 *   <li>使用 {@link ItemStack#getEnchantments()} 获取附魔列表</li>
 *   <li>跳过空物品和无附魔物品</li>
 *   <li>跳过未注册任何回调的附魔（O2）</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class EnchantmentScanner {

	private EnchantmentScanner() {
	}

	/**
	 * 扫描实体的装备槽位，返回所有有回调的附魔上下文列表。
	 *
	 * <p>应用 O2/O3/O5 三项优化：槽位过滤、REGISTRY 交集、延迟分配。</p>
	 *
	 * @param entity 目标实体
	 * @param type   事件类型（用于确定关注槽位）
	 * @return 附魔上下文列表（可能为空，不会为 null）
	 */
	public static List<EnchantmentContext> scan(LivingEntity entity, EnchantmentEventType<?> type) {
		// O5：按事件类型声明的槽位遍历
		Set<EquipmentSlot> slots = type.relevantSlots();

		// O3：延迟分配 ArrayList，空结果返回 List.of()
		List<EnchantmentContext> result = null;

		for (EquipmentSlot slot : slots) {
			if (!entity.canUseSlot(slot)) {
				continue;
			}
			ItemStack item = entity.getItemBySlot(slot);
			if (item.isEmpty()) {
				continue;
			}
			ItemEnchantments enchantments = item.getEnchantments();
			if (enchantments.isEmpty()) {
				continue;
			}
			// 遍历该物品的所有附魔，只为有回调的附魔创建 context（O2）
			for (var entry : enchantments.entrySet()) {
				var enchantmentHolder = entry.getKey();
				int level = entry.getIntValue();
				if (level <= 0) {
					continue;
				}
				// O2：跳过未注册任何回调的附魔（如原版锋利、保护）
				if (!EnchantmentEventDispatcher.hasEnchantmentCallbacks(enchantmentHolder)) {
					continue;
				}
				if (result == null) {
					result = new ArrayList<>(4);
				}
				result.add(new EnchantmentContext(enchantmentHolder, level, item, slot));
			}
		}

		return result != null ? result : List.of();
	}

	/**
	 * 扫描实体的所有装备槽位（忽略事件类型槽位声明）。
	 *
	 * <p>用于需要扫描所有槽位的场景（如 ENTITY_TICK 默认扫描所有槽位）。
	 * 仍应用 O2 REGISTRY 交集和 O3 延迟分配优化。</p>
	 *
	 * @param entity 目标实体
	 * @return 附魔上下文列表（可能为空，不会为 null）
	 */
	public static List<EnchantmentContext> scanAllSlots(LivingEntity entity) {
		// O3：延迟分配 ArrayList，空结果返回 List.of()
		List<EnchantmentContext> result = null;

		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			if (!entity.canUseSlot(slot)) {
				continue;
			}
			ItemStack item = entity.getItemBySlot(slot);
			if (item.isEmpty()) {
				continue;
			}
			ItemEnchantments enchantments = item.getEnchantments();
			if (enchantments.isEmpty()) {
				continue;
			}
			for (var entry : enchantments.entrySet()) {
				var enchantmentHolder = entry.getKey();
				int level = entry.getIntValue();
				if (level <= 0) {
					continue;
				}
				// O2：跳过未注册任何回调的附魔
				if (!EnchantmentEventDispatcher.hasEnchantmentCallbacks(enchantmentHolder)) {
					continue;
				}
				if (result == null) {
					result = new ArrayList<>(4);
				}
				result.add(new EnchantmentContext(enchantmentHolder, level, item, slot));
			}
		}

		return result != null ? result : List.of();
	}

	/**
	 * 扫描实体的指定装备槽位，返回该槽位上有回调的附魔上下文列表。
	 *
	 * <p>用于交互事件（ITEM_USE/BLOCK_USE/ENTITY_USE）：只需扫描触发手（MAINHAND 或 OFFHAND），
	 * 避免扫描无关槽位。仍应用 O2 REGISTRY 交集和 O3 延迟分配优化。</p>
	 *
	 * @param entity 目标实体
	 * @param slot   要扫描的槽位
	 * @return 附魔上下文列表（可能为空，不会为 null）
	 */
	public static List<EnchantmentContext> scanSlot(LivingEntity entity, EquipmentSlot slot) {
		if (!entity.canUseSlot(slot)) {
			return List.of();
		}
		ItemStack item = entity.getItemBySlot(slot);
		if (item.isEmpty()) {
			return List.of();
		}
		ItemEnchantments enchantments = item.getEnchantments();
		if (enchantments.isEmpty()) {
			return List.of();
		}

		// O3：延迟分配 ArrayList，空结果返回 List.of()
		List<EnchantmentContext> result = null;

		for (var entry : enchantments.entrySet()) {
			var enchantmentHolder = entry.getKey();
			int level = entry.getIntValue();
			if (level <= 0) {
				continue;
			}
			// O2：跳过未注册任何回调的附魔
			if (!EnchantmentEventDispatcher.hasEnchantmentCallbacks(enchantmentHolder)) {
				continue;
			}
			if (result == null) {
				result = new ArrayList<>(4);
			}
			result.add(new EnchantmentContext(enchantmentHolder, level, item, slot));
		}

		return result != null ? result : List.of();
	}
}
