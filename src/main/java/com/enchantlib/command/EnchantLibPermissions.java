package com.enchantlib.command;

import net.fabricmc.fabric.api.permission.v1.PermissionNode;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionLevel;

/**
 * EnchantLib 权限节点定义。
 *
 * <p>基于 fabric-permission-api-v1 的上下文模式，将 {@code /enchantlib} 各子指令的
 * 权限检查抽象为独立的 {@link PermissionNode}，便于权限管理插件（如 LuckPerms）
 * 统一配置。</p>
 *
 * <h2>权限节点清单</h2>
 * <ul>
 *   <li>{@code enchantlib.command.list}   - 列出附魔（默认：所有玩家）</li>
 *   <li>{@code enchantlib.command.groups} - 列出互斥组（默认：所有玩家）</li>
 *   <li>{@code enchantlib.command.give}   - 给予附魔书（默认：OP 等级 2 / GAMEMASTERS）</li>
 *   <li>{@code enchantlib.command.dump}   - 导出附魔 JSON（默认：OP 等级 2 / GAMEMASTERS）</li>
 *   <li>{@code enchantlib.command.reload} - 重载配置（默认：OP 等级 2 / GAMEMASTERS）</li>
 * </ul>
 *
 * <h2>默认值语义</h2>
 * <ul>
 *   <li>查询指令（{@code list}/{@code groups}）：默认 {@code true}（所有玩家可用），
 *       通过 {@link PermissionPredicates#require(PermissionNode, boolean)} 指定默认 true</li>
 *   <li>管理指令（{@code give}/{@code dump}/{@code reload}）：默认 OP 等级 2，
 *       通过 {@link PermissionPredicates#require(PermissionNode, PermissionLevel)} 指定 GAMEMASTERS</li>
 * </ul>
 *
 * <p>当权限插件未明确设置时，使用上述默认值；权限插件可覆盖这些默认行为。</p>
 *
 * @since 0.1.0
 */
public final class EnchantLibPermissions {

	/** 查询指令权限：列出附魔（默认所有玩家可用） */
	public static final PermissionNode<Boolean> LIST =
		PermissionNode.of(Identifier.fromNamespaceAndPath("enchantlib", "command/list"));

	/** 查询指令权限：列出互斥组（默认所有玩家可用） */
	public static final PermissionNode<Boolean> GROUPS =
		PermissionNode.of(Identifier.fromNamespaceAndPath("enchantlib", "command/groups"));

	/** 管理指令权限：给予附魔书（默认 OP 等级 2） */
	public static final PermissionNode<Boolean> GIVE =
		PermissionNode.of(Identifier.fromNamespaceAndPath("enchantlib", "command/give"));

	/** 管理指令权限：导出附魔 JSON（默认 OP 等级 2） */
	public static final PermissionNode<Boolean> DUMP =
		PermissionNode.of(Identifier.fromNamespaceAndPath("enchantlib", "command/dump"));

	/** 管理指令权限：重载配置（默认 OP 等级 2） */
	public static final PermissionNode<Boolean> RELOAD =
		PermissionNode.of(Identifier.fromNamespaceAndPath("enchantlib", "command/reload"));

	/** 管理指令权限：调试指令组（默认 OP 等级 2） */
	public static final PermissionNode<Boolean> DEBUG =
		PermissionNode.of(Identifier.fromNamespaceAndPath("enchantlib", "command/debug"));

	private EnchantLibPermissions() {
		// 工具类，禁止实例化
	}

	/**
	 * 创建查询指令的权限谓词（默认 true，所有玩家可用）。
	 *
	 * @return 用于 {@code Commands.literal(...).requires(...)} 的谓词
	 */
	public static java.util.function.Predicate<CommandSourceStack> requireQuery(PermissionNode<Boolean> node) {
		return PermissionPredicates.require(node, true);
	}

	/**
	 * 创建管理指令的权限谓词（默认 OP 等级 2 / GAMEMASTERS）。
	 *
	 * @return 用于 {@code Commands.literal(...).requires(...)} 的谓词
	 */
	public static java.util.function.Predicate<CommandSourceStack> requireAdmin(PermissionNode<Boolean> node) {
		return PermissionPredicates.require(node, PermissionLevel.GAMEMASTERS);
	}
}
