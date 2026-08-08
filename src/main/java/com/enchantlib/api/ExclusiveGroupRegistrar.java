package com.enchantlib.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * 互斥组注册器。
 *
 * <p>在 {@link EnchantmentEntrypoint#onRegisterExclusiveGroups(ExclusiveGroupRegistrar)} 中使用，
 * 通过 {@link #register(ExclusiveGroupBuilder)} 注册自定义互斥组。</p>
 *
 * <p>注册器会自动检测重复的标签 ID，同一标签 ID 重复注册将抛出异常。</p>
 *
 * @since 0.1.0
 */
public class ExclusiveGroupRegistrar {

	private final Map<Identifier, ExclusiveGroupBuilder> builders = new LinkedHashMap<>();

	/**
	 * 注册一个互斥组。
	 *
	 * @param builder 互斥组构建器
	 * @return this
	 * @throws IllegalStateException 如果该标签 ID 已被注册
	 */
	public ExclusiveGroupRegistrar register(ExclusiveGroupBuilder builder) {
		Identifier tagId = builder.getTagId();
		if (this.builders.containsKey(tagId)) {
			throw new IllegalStateException("互斥组标签 ID 已被注册: " + tagId);
		}
		this.builders.put(tagId, builder);
		return this;
	}

	/**
	 * 获取已注册的互斥组列表。
	 *
	 * @return 不可修改的互斥组构建器列表
	 */
	public List<ExclusiveGroupBuilder> getBuilders() {
		return Collections.unmodifiableList(new ArrayList<>(this.builders.values()));
	}

	/**
	 * 获取已注册的互斥组数量。
	 *
	 * @return 数量
	 */
	public int size() {
		return this.builders.size();
	}

	/**
	 * 判断是否已注册指定标签 ID 的互斥组。
	 *
	 * @param tagId 标签 ID
	 * @return 是否已注册
	 */
	public boolean contains(Identifier tagId) {
		return this.builders.containsKey(tagId);
	}
}
