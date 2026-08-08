package com.enchantlib.event;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 附魔事件上下文。
 *
 * <p>描述"某个实体身上某个装备槽位某个附魔的某等级"这一状态，作为事件分发的粒度单位。</p>
 *
 * <p>每个 {@link EnchantmentEventCallback} 在被分发时都会收到一个事件对象和一个
 * EnchantmentContext，开发者可从 context 中获取附魔 Holder、等级、物品、槽位信息。</p>
 *
 * @param enchantment 附魔 Holder（可用于获取附魔定义/效果）
 * @param level       附魔等级（>= 1）
 * @param itemStack   携带该附魔的物品（不可变快照，调用方应使用 {@code itemStack.copy()} 修改）
 * @param slot        物品所在的装备槽位
 *
 * @since 0.1.0
 */
public record EnchantmentContext(
	Holder<Enchantment> enchantment,
	int level,
	ItemStack itemStack,
	EquipmentSlot slot
) {

	/**
	 * 获取附魔 ID 字符串（形如 "modid:name"）。
	 *
	 * @return 附魔 ID，若 Holder 未绑定 ResourceKey 则返回 "unbound"
	 */
	public String enchantmentId() {
		return enchantment.unwrapKey()
			.map(key -> key.identifier().toString())
			.orElse("unbound");
	}
}
