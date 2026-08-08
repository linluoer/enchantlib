package com.enchantlib.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * 附魔注册器。
 *
 * <p>在 {@link EnchantmentEntrypoint#onRegisterEnchantments(EnchantmentRegistrar)} 中使用，
 * 通过 {@link #register(EnchantmentBuilder)} 注册自定义附魔。</p>
 *
 * <p>注册器会自动检测重复 ID，同一 ID 重复注册将抛出异常。</p>
 *
 * @since 0.1.0
 */
public class EnchantmentRegistrar {

	private final Map<Identifier, EnchantmentBuilder> builders = new LinkedHashMap<>();

	/**
	 * 注册一个附魔。
	 *
	 * @param builder 附魔构建器
	 * @return this
	 * @throws IllegalStateException 如果该 ID 已被注册
	 */
	public EnchantmentRegistrar register(EnchantmentBuilder builder) {
		Identifier id = builder.getId();
		if (this.builders.containsKey(id)) {
			throw new IllegalStateException("附魔 ID 已被注册: " + id);
		}
		this.builders.put(id, builder);
		return this;
	}

	/**
	 * 获取已注册的附魔列表。
	 *
	 * @return 不可修改的附魔构建器列表
	 */
	public List<EnchantmentBuilder> getBuilders() {
		return Collections.unmodifiableList(new ArrayList<>(this.builders.values()));
	}

	/**
	 * 获取已注册的附魔数量。
	 *
	 * @return 数量
	 */
	public int size() {
		return this.builders.size();
	}

	/**
	 * 判断是否已注册指定 ID 的附魔。
	 *
	 * @param id 附魔 ID
	 * @return 是否已注册
	 */
	public boolean contains(Identifier id) {
		return this.builders.containsKey(id);
	}
}
