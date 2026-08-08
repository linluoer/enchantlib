package com.enchantlib.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * 互斥组构建器。
 *
 * <p>链式构建附魔互斥组，生成 MC 26.2 原生的附魔标签 JSON（enchantment tag），
 * 路径为 {@code data/<namespace>/tags/enchantment/exclusive_set/<group>.json}。</p>
 *
 * <p>同组中的附魔互相排斥（不可共存），由 MC 原生的 {@code exclusive_set} 机制处理。
 * 在附魔定义中通过 {@code exclusive_set = "#<namespace>:exclusive_set/<group>"} 引用。</p>
 *
 * <p>典型用法：</p>
 * <pre>{@code
 * ExclusiveGroupBuilder.create("my_group")           // 标签 ID = enchantlib:exclusive_set/my_group
 *     .add("enchantlib:fire_aspect")
 *     .add("enchantlib:ice_aspect")
 *     .build();                                       // 生成标签 JSON
 *
 * // 指定命名空间
 * ExclusiveGroupBuilder.create("mymod", "my_group")  // 标签 ID = mymod:exclusive_set/my_group
 *     .add("mymod:enchant1")
 *     .add("mymod:enchant2");
 *
 * // 在附魔定义中引用
 * EnchantmentBuilder.create("enchantlib:fire_aspect")
 *     .exclusiveSet("#enchantlib:exclusive_set/my_group")
 *     ...
 * }</pre>
 *
 * @since 0.1.0
 */
public class ExclusiveGroupBuilder {

	/** 互斥组标签的固定路径前缀 */
	private static final String TAG_PREFIX = "exclusive_set/";

	private final Identifier tagId;
	private final Set<String> enchantments = new LinkedHashSet<>();

	private ExclusiveGroupBuilder(Identifier tagId) {
		this.tagId = tagId;
	}

	/**
	 * 创建互斥组构建器，使用默认命名空间 {@code enchantlib}。
	 *
	 * @param groupId 互斥组名称（不含命名空间，如 "my_group"）
	 * @return 构建器实例
	 */
	public static ExclusiveGroupBuilder create(String groupId) {
		return create("enchantlib", groupId);
	}

	/**
	 * 创建互斥组构建器，指定命名空间。
	 *
	 * @param namespace 命名空间（如 "mymod"）
	 * @param groupId   互斥组名称（如 "my_group"）
	 * @return 构建器实例
	 */
	public static ExclusiveGroupBuilder create(String namespace, String groupId) {
		Identifier tagId = Identifier.fromNamespaceAndPath(namespace, TAG_PREFIX + groupId);
		return new ExclusiveGroupBuilder(tagId);
	}

	/**
	 * 添加一个互斥的附魔。
	 *
	 * @param enchantmentId 附魔 ID（如 "enchantlib:fire_aspect"）
	 * @return this
	 */
	public ExclusiveGroupBuilder add(String enchantmentId) {
		this.enchantments.add(enchantmentId);
		return this;
	}

	/**
	 * 批量添加互斥的附魔。
	 *
	 * @param enchantmentIds 附魔 ID 列表
	 * @return this
	 */
	public ExclusiveGroupBuilder addAll(List<String> enchantmentIds) {
		this.enchantments.addAll(enchantmentIds);
		return this;
	}

	/**
	 * 获取互斥组的标签 ID。
	 *
	 * <p>标签 ID 格式为 {@code <namespace>:exclusive_set/<group>}，
	 * 在附魔 JSON 中通过 {@code "#<标签 ID>"} 引用。</p>
	 *
	 * @return 标签 ID
	 */
	public Identifier getTagId() {
		return this.tagId;
	}

	/**
	 * 获取互斥组的标签引用字符串。
	 *
	 * <p>例如 {@code "#enchantlib:exclusive_set/my_group"}，可直接传入
	 * {@link EnchantmentBuilder#exclusiveSet(String)}。</p>
	 *
	 * @return 标签引用字符串（以 # 开头）
	 */
	public String getTagReference() {
		return "#" + this.tagId;
	}

	/**
	 * 获取互斥组在数据包中的资源 ID。
	 *
	 * <p>资源路径格式为 {@code <namespace>:tags/enchantment/exclusive_set/<group>.json}。</p>
	 *
	 * @return 资源 ID
	 */
	public Identifier getResourceId() {
		return Identifier.fromNamespaceAndPath(
			this.tagId.getNamespace(),
			"tags/enchantment/" + this.tagId.getPath() + ".json"
		);
	}

	/**
	 * 获取互斥的附魔 ID 列表（不可修改）。
	 *
	 * @return 附魔 ID 列表
	 */
	public List<String> getEnchantments() {
		return Collections.unmodifiableList(new ArrayList<>(this.enchantments));
	}

	/**
	 * 构建标签 JSON 字符串。
	 *
	 * <p>格式与 MC 原生附魔标签一致：
	 * <pre>{@code
	 * {
	 *   "replace": false,
	 *   "values": ["modid:enchant1", "modid:enchant2"]
	 * }
	 * }</pre></p>
	 *
	 * @return 标签 JSON 字符串
	 */
	public String toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("replace", false);

		JsonArray values = new JsonArray();
		for (String enchantment : this.enchantments) {
			values.add(enchantment);
		}
		root.add("values", values);

		return root.toString();
	}

	/**
	 * 构建标签 JSON 字节数组（UTF-8 编码）。
	 *
	 * @return 标签 JSON 字节数组
	 */
	public byte[] toBytes() {
		return toJson().getBytes(StandardCharsets.UTF_8);
	}
}
