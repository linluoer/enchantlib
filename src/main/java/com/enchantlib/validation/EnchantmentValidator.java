package com.enchantlib.validation;

import com.enchantlib.EnchantLib;
import com.enchantlib.api.EnchantmentBuilder;
import com.enchantlib.api.ExclusiveGroupBuilder;
import com.enchantlib.api.ExclusiveSets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * 冻结前全局校验管线。
 *
 * <p>在附魔和互斥组收集完成后、数据包注入前执行全局校验。
 * 检测到任何异常将抛出 {@link IllegalStateException}，使服务端启动失败（fail-fast）。</p>
 *
 * <h2>校验项</h2>
 * <ul>
 *   <li>互斥组不能为空（至少包含一个附魔）</li>
 *   <li>互斥组成员 ID 格式有效性（能被 {@link Identifier#parse(String)} 解析）</li>
 *   <li>附魔 {@code exclusive_set} 引用格式有效性（以 {@code #} 开头表示标签引用）</li>
 *   <li>一个附魔不能同时存在于多个互斥组中（含原版组和自定义组）</li>
 * </ul>
 *
 * <h2>设计原则</h2>
 * <p>采用 fail-fast 策略：在启动时立即发现问题并崩溃，避免问题在运行时才暴露。
 * 所有校验错误会先通过 LOGGER.error 输出详细日志，再抛出异常。</p>
 *
 * @since 0.1.0
 */
public final class EnchantmentValidator {

	private EnchantmentValidator() {
	}

	/**
	 * 执行全局校验。
	 *
	 * @param enchantments 待注入的附魔列表
	 * @param groups        待注入的互斥组列表
	 * @throws IllegalStateException 如果检测到任何校验错误
	 */
	public static void validate(List<EnchantmentBuilder> enchantments, List<ExclusiveGroupBuilder> groups) {
		EnchantLib.LOGGER.info("[EnchantLib] 开始全局校验: {} 个附魔, {} 个互斥组", enchantments.size(), groups.size());

		int errorCount = 0;

		// 校验互斥组
		for (ExclusiveGroupBuilder group : groups) {
			errorCount += validateGroup(group);
		}

		// 校验附魔
		for (EnchantmentBuilder enchantment : enchantments) {
			errorCount += validateEnchantment(enchantment);
		}

		// 校验：一个附魔不能同时存在于多个互斥组中
		errorCount += validateNoOverlap(groups);

		if (errorCount > 0) {
			String msg = String.format("全局校验失败: 检测到 %d 个错误，服务端启动中止", errorCount);
			EnchantLib.LOGGER.error("[EnchantLib] {}", msg);
			throw new IllegalStateException(msg);
		}

		EnchantLib.LOGGER.info("[EnchantLib] 全局校验通过: {} 个附魔, {} 个互斥组", enchantments.size(), groups.size());
	}

	/**
	 * 校验互斥组。
	 *
	 * @return 错误数量（0 表示无错误）
	 */
	private static int validateGroup(ExclusiveGroupBuilder group) {
		int errors = 0;
		Identifier tagId = group.getTagId();

		// 校验：互斥组不能为空
		if (group.getEnchantments().isEmpty()) {
			EnchantLib.LOGGER.error("[EnchantLib] 校验失败: 互斥组 {} 不包含任何附魔", tagId);
			errors++;
		}

		// 校验：互斥组成员 ID 格式有效性
		for (String enchantId : group.getEnchantments()) {
			if (!isValidIdentifier(enchantId)) {
				EnchantLib.LOGGER.error("[EnchantLib] 校验失败: 互斥组 {} 包含无效的附魔 ID: {}", tagId, enchantId);
				errors++;
			}
		}

		return errors;
	}

	/**
	 * 校验附魔。
	 *
	 * @return 错误数量（0 表示无错误）
	 */
	private static int validateEnchantment(EnchantmentBuilder enchantment) {
		int errors = 0;
		Identifier id = enchantment.getId();

		// 校验：exclusive_set 引用格式（如果设置了的话）
		String exclusiveSet = enchantment.getExclusiveSet();
		if (exclusiveSet != null && !exclusiveSet.isEmpty()) {
			if (!exclusiveSet.startsWith("#")) {
				EnchantLib.LOGGER.error("[EnchantLib] 校验失败: 附魔 {} 的 exclusive_set 引用格式无效: {}（应以 # 开头表示标签引用）",
					id, exclusiveSet);
				errors++;
			}
		}

		return errors;
	}

	/**
	 * 校验：一个附魔不能同时存在于多个互斥组中。
	 *
	 * <p>检查所有自定义互斥组的成员是否与原版互斥组或其他自定义互斥组冲突。
	 * 一个附魔 ID 只能属于一个互斥组，否则会导致互斥逻辑混乱。</p>
	 *
	 * <p>校验范围：</p>
	 * <ul>
	 *   <li>自定义组之间的成员冲突（附魔 A 同时在自定义组 X 和 Y 中）</li>
	 *   <li>自定义组与原版组的成员冲突（附魔 A 同时在原版组和自定义组中）</li>
	 * </ul>
	 *
	 * @param groups 自定义互斥组列表
	 * @return 错误数量
	 */
	private static int validateNoOverlap(List<ExclusiveGroupBuilder> groups) {
		int errors = 0;

		// 构建附魔 ID → 所属组列表的映射
		Map<String, List<String>> enchantmentToGroups = new HashMap<>();

		for (ExclusiveGroupBuilder group : groups) {
			String groupRef = group.getTagReference();
			for (String enchantId : group.getEnchantments()) {
				enchantmentToGroups.computeIfAbsent(enchantId, k -> new ArrayList<>()).add(groupRef);
			}
		}

		// 检查自定义组之间的冲突
		for (Map.Entry<String, List<String>> entry : enchantmentToGroups.entrySet()) {
			String enchantId = entry.getKey();
			List<String> groupRefs = entry.getValue();
			if (groupRefs.size() > 1) {
				EnchantLib.LOGGER.error("[EnchantLib] 校验失败: 附魔 {} 同时存在于多个互斥组中: {}（一个附魔只能属于一个互斥组）",
					enchantId, groupRefs);
				errors++;
			}
		}

		// 检查自定义组成员是否与原版组成员冲突
		// 原版组的成员是固定的（如 sharpness 在 damage 组中），如果开发者把原版附魔 ID 添加到自定义组中，
		// 会导致原版附魔的互斥行为变化
		Set<String> vanillaEnchants = new HashSet<>();
		for (Set<String> vanillaGroupEnchants : ExclusiveSets.VANILLA_GROUPS.values()) {
			vanillaEnchants.addAll(vanillaGroupEnchants);
		}

		for (ExclusiveGroupBuilder group : groups) {
			String groupRef = group.getTagReference();
			for (String enchantId : group.getEnchantments()) {
				if (vanillaEnchants.contains(enchantId)) {
					// 查找这个附魔在哪个原版组中
					String vanillaGroupName = findVanillaGroupName(enchantId);
					EnchantLib.LOGGER.error("[EnchantLib] 校验失败: 附魔 {} 已属于原版互斥组 {}，不能添加到自定义互斥组 {}",
						enchantId, vanillaGroupName, groupRef);
					errors++;
				}
			}
		}

		return errors;
	}

	/**
	 * 查找原版附魔所属的互斥组名称。
	 *
	 * @param enchantId 附魔 ID（如 "minecraft:sharpness"）
	 * @return 互斥组名称（如 "#minecraft:exclusive_set/damage"），未找到返回 null
	 */
	private static String findVanillaGroupName(String enchantId) {
		for (Map.Entry<String, Set<String>> entry : ExclusiveSets.VANILLA_GROUPS.entrySet()) {
			if (entry.getValue().contains(enchantId)) {
				return entry.getKey();
			}
		}
		return null;
	}

	/**
	 * 检查字符串是否能被解析为有效的 Identifier。
	 *
	 * @param id 字符串 ID
	 * @return true 如果格式有效
	 */
	private static boolean isValidIdentifier(String id) {
		if (id == null || id.isEmpty()) {
			return false;
		}
		try {
			Identifier.parse(id);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}
