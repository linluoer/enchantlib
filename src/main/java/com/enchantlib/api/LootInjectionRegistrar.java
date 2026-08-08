package com.enchantlib.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 战利品注入规则注册器。
 *
 * <p>在 {@link EnchantmentEntrypoint#onRegisterLootInjections(LootInjectionRegistrar)} 中使用，
 * 通过 {@link #register(LootInjectionBuilder)} 注册自定义战利品注入规则。</p>
 *
 * <p>注册器会自动调用 {@link LootInjectionBuilder#build()} 构建不可变规则，
 * 并按目标战利品表索引存储，供 {@code LootInjectionHandler} 在战利品表加载时查询。</p>
 *
 * @since 0.1.0
 * @see EnchantmentEntrypoint#onRegisterLootInjections(LootInjectionRegistrar)
 */
public final class LootInjectionRegistrar {

	private final List<LootInjection> injections = new ArrayList<>();

	/**
	 * 注册一条战利品注入规则。
	 *
	 * <p>构建器会自动调用 {@link LootInjectionBuilder#build()} 构建不可变规则。
	 * 若构建失败（缺少必填字段）将抛出 {@link IllegalStateException}。</p>
	 *
	 * @param builder 注入规则构建器
	 * @return this
	 * @throws IllegalStateException 如果构建失败
	 */
	public LootInjectionRegistrar register(LootInjectionBuilder builder) {
		LootInjection injection = builder.build();
		this.injections.add(injection);
		return this;
	}

	/**
	 * 注册一条已构建的战利品注入规则。
	 *
	 * @param injection 注入规则实例
	 * @return this
	 */
	public LootInjectionRegistrar register(LootInjection injection) {
		this.injections.add(injection);
		return this;
	}

	/**
	 * 获取已注册的注入规则列表。
	 *
	 * @return 不可修改的注入规则列表
	 */
	public List<LootInjection> getInjections() {
		return Collections.unmodifiableList(new ArrayList<>(this.injections));
	}

	/**
	 * 获取已注册的注入规则数量。
	 *
	 * @return 数量
	 */
	public int size() {
		return this.injections.size();
	}
}
