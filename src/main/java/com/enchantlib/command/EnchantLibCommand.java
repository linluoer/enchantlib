package com.enchantlib.command;

import com.enchantlib.EnchantLib;
import com.enchantlib.api.EnchantmentBuilder;
import com.enchantlib.api.EnchantmentRegistrar;
import com.enchantlib.api.ExclusiveGroupBuilder;
import com.enchantlib.api.ExclusiveGroupRegistrar;
import com.enchantlib.config.AcquisitionConfig;
import com.enchantlib.debug.DebugLogger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * EnchantLib 指令系统。
 *
 * <p>注册 {@code /enchantlib} 根指令，提供 6 个子指令：</p>
 * <ul>
 *   <li>{@code list} - 列出所有已注册的自定义附魔（含 ID、最大等级、权重、互斥组）</li>
 *   <li>{@code give <target> <enchantment_id> [level]} - 给予玩家一本附魔书（指定附魔与等级）</li>
 *   <li>{@code groups} - 列出所有已注册的互斥组（含标签 ID 与成员附魔）</li>
 *   <li>{@code dump <enchantment_id> [file]} - 导出附魔 JSON 定义到文件（默认导出到 {@code config/enchantlib/dump/}）</li>
 *   <li>{@code reload} - 重新加载 {@code config/enchantlib/acquisition.toml}（仅全局开关，附魔定义需重启服务端）</li>
 *   <li>{@code debug <status|toggle|info>} - 调试指令组（查询状态、切换开关、查看附魔详情）</li>
 * </ul>
 *
 * <h2>权限要求</h2>
 * <ul>
 *   <li>{@code list} / {@code groups}: 默认所有玩家可用（权限节点 {@code enchantlib.command.list/groups}，默认 true）</li>
 *   <li>{@code give} / {@code dump} / {@code reload} / {@code debug}: 默认需 OP 等级 2（权限节点 {@code enchantlib.command.give/dump/reload/debug}，默认 GAMEMASTERS）</li>
 * </ul>
 * <p>权限节点可通过权限管理插件（如 LuckPerms）覆盖默认行为。</p>
 *
 * @since 0.1.0
 */
public final class EnchantLibCommand implements CommandRegistrationCallback {

	private static final String DUMP_DIR = "config/enchantlib/dump";

	@Override
	public void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess,
						 Commands.CommandSelection environment) {
		dispatcher.register(
			Commands.literal("enchantlib")
				// /enchantlib list - 列出所有自定义附魔
				.then(Commands.literal("list")
					.requires(EnchantLibPermissions.requireQuery(EnchantLibPermissions.LIST))
					.executes(this::executeList))
				// /enchantlib give <target> <enchantment_id> [level] - 给予附魔书
				.then(Commands.literal("give")
					.requires(EnchantLibPermissions.requireAdmin(EnchantLibPermissions.GIVE))
					.then(Commands.argument("target", EntityArgument.players())
						.then(Commands.argument("enchantment_id", IdentifierArgument.id())
							.executes(ctx -> executeGive(ctx, 1))
							.then(Commands.argument("level", IntegerArgumentType.integer(1, 255))
								.executes(ctx -> executeGive(ctx,
									IntegerArgumentType.getInteger(ctx, "level")))))))
				// /enchantlib groups - 列出所有互斥组
				.then(Commands.literal("groups")
					.requires(EnchantLibPermissions.requireQuery(EnchantLibPermissions.GROUPS))
					.executes(this::executeGroups))
				// /enchantlib dump <enchantment_id> [file] - 导出附魔 JSON
				.then(Commands.literal("dump")
					.requires(EnchantLibPermissions.requireAdmin(EnchantLibPermissions.DUMP))
					.then(Commands.argument("enchantment_id", IdentifierArgument.id())
						.executes(this::executeDump)
						.then(Commands.argument("file", StringArgumentType.greedyString())
							.executes(this::executeDump))))
				// /enchantlib reload - 重新加载 acquisition.toml
				.then(Commands.literal("reload")
					.requires(EnchantLibPermissions.requireAdmin(EnchantLibPermissions.RELOAD))
					.executes(this::executeReload))
				// /enchantlib debug <status|toggle|info> - 调试指令组
				.then(Commands.literal("debug")
					.requires(EnchantLibPermissions.requireAdmin(EnchantLibPermissions.DEBUG))
					.then(Commands.literal("status")
						.executes(this::executeDebugStatus))
					.then(Commands.literal("toggle")
						.executes(this::executeDebugToggle))
					.then(Commands.literal("info")
						.then(Commands.argument("enchantment_id", IdentifierArgument.id())
							.executes(this::executeDebugInfo))))
		);
	}

	/**
	 * 执行 {@code /enchantlib list}：列出所有已注册的自定义附魔。
	 */
	private int executeList(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		EnchantmentRegistrar registrar = EnchantLib.getEnchantmentRegistrar();
		if (registrar == null || registrar.size() == 0) {
			source.sendSuccess(() -> Component.literal("§e[EnchantLib] §f当前没有已注册的自定义附魔。"), false);
			return 0;
		}

		List<EnchantmentBuilder> builders = registrar.getBuilders();
		source.sendSuccess(() -> Component.literal(
			"§a[EnchantLib] §f共 " + builders.size() + " 个自定义附魔："), false);

		for (EnchantmentBuilder b : builders) {
			String desc = b.getDescriptionFallback() != null ? b.getDescriptionFallback() : "(无描述)";
			String exclusive = b.getExclusiveSet() != null ? " §7| 互斥: " + b.getExclusiveSet() : "";
			source.sendSuccess(() -> Component.literal(
				"§6- " + b.getId() + " §7(lv" + b.getMaxLevel() + ", w" + b.getWeight() + ")"
					+ " §f" + desc + exclusive), false);
		}
		return builders.size();
	}

	/**
	 * 执行 {@code /enchantlib give <target> <enchantment_id> [level]}：给予附魔书。
	 */
	private int executeGive(CommandContext<CommandSourceStack> ctx, int level) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		Identifier enchantId = IdentifierArgument.getId(ctx, "enchantment_id");
		String enchantIdStr = enchantId.toString();

		// 解析附魔 ID 为 Holder<Enchantment>
		HolderLookup.Provider registries = source.registryAccess();
		Optional<? extends HolderLookup.RegistryLookup<Enchantment>> registryOpt =
			registries.lookup(Registries.ENCHANTMENT);
		if (registryOpt.isEmpty()) {
			source.sendFailure(Component.literal("§c[EnchantLib] 无法访问附魔注册表。"));
			return 0;
		}
		HolderLookup.RegistryLookup<Enchantment> registry = registryOpt.get();

		ResourceKey<Enchantment> resourceKey = ResourceKey.create(Registries.ENCHANTMENT, enchantId);
		Optional<Holder.Reference<Enchantment>> holderOpt = registry.get(resourceKey);
		if (holderOpt.isEmpty()) {
			source.sendFailure(Component.literal("§c[EnchantLib] 附魔 ID 不存在: " + enchantIdStr));
			return 0;
		}
		Holder<Enchantment> enchantmentHolder = holderOpt.get();

		// 检查附魔最大等级（若指定 level 超过附魔定义的 maxLevel，仍允许但提示）
		Enchantment enchantment = enchantmentHolder.value();
		int definedMax = enchantment.getMaxLevel();
		if (level > definedMax) {
			source.sendSuccess(() -> Component.literal(
				"§e[EnchantLib] §f警告: 指定等级 " + level + " 超过附魔最大等级 " + definedMax + "（仍会强制给予）"), false);
		}

		// 构建附魔书
		ItemStack bookStack = createEnchantedBook(enchantmentHolder, level);

		// 给予目标玩家
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "target");
		if (targets.isEmpty()) {
			source.sendFailure(Component.literal("§c[EnchantLib] 未找到目标玩家。"));
			return 0;
		}

		for (ServerPlayer player : targets) {
			ItemStack heldItem = player.getMainHandItem();
			if (!heldItem.isEmpty()) {
				// 手持物品时直接附魔该物品（方便测试，无需铁砧）
				ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(heldItem.getEnchantments());
				mutable.set(enchantmentHolder, level);
				heldItem.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
				source.sendSuccess(() -> Component.literal(
					"§a[EnchantLib] §f已为 " + player.getName().getString() + " 的手持物品附魔: "
						+ enchantIdStr + " 等级 " + level), true);
			} else {
				// 手中无物品时给予附魔书（原行为）
				ItemStack copy = bookStack.copy();
				player.getInventory().add(copy);
				if (!copy.isEmpty()) {
					player.drop(copy, false);
				}
				source.sendSuccess(() -> Component.literal(
					"§a[EnchantLib] §f已给予 " + player.getName().getString() + " 附魔书: "
						+ enchantIdStr + " 等级 " + level), true);
			}
		}
		return targets.size();
	}

	/**
	 * 执行 {@code /enchantlib groups}：列出所有互斥组。
	 */
	private int executeGroups(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		ExclusiveGroupRegistrar registrar = EnchantLib.getExclusiveGroupRegistrar();
		if (registrar == null || registrar.size() == 0) {
			source.sendSuccess(() -> Component.literal("§e[EnchantLib] §f当前没有已注册的自定义互斥组。"), false);
			return 0;
		}

		List<ExclusiveGroupBuilder> builders = registrar.getBuilders();
		source.sendSuccess(() -> Component.literal(
			"§a[EnchantLib] §f共 " + builders.size() + " 个自定义互斥组："), false);

		for (ExclusiveGroupBuilder g : builders) {
			List<String> members = g.getEnchantments();
			StringBuilder memberStr = new StringBuilder();
			for (int i = 0; i < members.size(); i++) {
				if (i > 0) memberStr.append(", ");
				memberStr.append(members.get(i));
			}
			source.sendSuccess(() -> Component.literal(
				"§6- #" + g.getTagId() + " §7(" + members.size() + " 个) §f" + memberStr), false);
		}
		return builders.size();
	}

	/**
	 * 执行 {@code /enchantlib dump <enchantment_id> [file]}：导出附魔 JSON 到文件。
	 *
	 * <p>默认输出到 {@code config/enchantlib/dump/<enchantment_id>.json}，
	 * 若指定 {@code file} 参数，则输出到 {@code config/enchantlib/dump/<file>}。</p>
	 */
	private int executeDump(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		Identifier enchantId = IdentifierArgument.getId(ctx, "enchantment_id");
		String enchantIdStr = enchantId.toString();

		EnchantmentRegistrar registrar = EnchantLib.getEnchantmentRegistrar();
		if (registrar == null) {
			source.sendFailure(Component.literal("§c[EnchantLib] 注册器尚未初始化。"));
			return 0;
		}

		// 在 EnchantmentBuilder 列表中查找（这些是 EnchantLib 注册的附魔）
		EnchantmentBuilder targetBuilder = null;
		for (EnchantmentBuilder b : registrar.getBuilders()) {
			if (b.getId().toString().equals(enchantIdStr)) {
				targetBuilder = b;
				break;
			}
		}
		if (targetBuilder == null) {
			source.sendFailure(Component.literal(
				"§c[EnchantLib] 未找到 EnchantLib 注册的附魔: " + enchantIdStr
					+ "（dump 仅支持 EnchantLib 注册的自定义附魔）"));
			return 0;
		}

		// 确定输出文件名
		String fileName;
		try {
			fileName = ctx.getArgument("file", String.class);
			if (!fileName.endsWith(".json")) {
				fileName = fileName + ".json";
			}
		} catch (IllegalArgumentException e) {
			// 未指定 file 参数，使用附魔 ID 作为文件名
			fileName = enchantIdStr.replace(":", "_").replace("/", "_") + ".json";
		}

		// 输出到 config/enchantlib/dump/<fileName>
		Path dumpDir = Path.of(DUMP_DIR);
		Path outputPath = dumpDir.resolve(fileName);
		try {
			Files.createDirectories(dumpDir);
			String json = targetBuilder.toJson();
			Files.writeString(outputPath, json, StandardCharsets.UTF_8);
			Path absolutePath = outputPath.toAbsolutePath();
			source.sendSuccess(() -> Component.literal(
				"§a[EnchantLib] §f已导出附魔 " + enchantIdStr + " 到: " + absolutePath), false);
			source.sendSuccess(() -> Component.literal("§7JSON 字节数: " + json.getBytes(StandardCharsets.UTF_8).length), false);
		} catch (Exception e) {
			source.sendFailure(Component.literal("§c[EnchantLib] 导出失败: " + e.getMessage()));
			EnchantLib.LOGGER.error("[EnchantLib] /enchantlib dump 写入文件失败: {}", e.getMessage(), e);
			return 0;
		}
		return 1;
	}

	/**
	 * 执行 {@code /enchantlib reload}：重新加载全局获取途径配置。
	 */
	private int executeReload(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();

		AcquisitionConfig oldConfig = EnchantLib.getAcquisitionConfig();
		AcquisitionConfig newConfig = EnchantLib.reloadAcquisitionConfig();

		source.sendSuccess(() -> Component.literal("§a[EnchantLib] §f全局获取途径配置已重新加载。"), true);
		source.sendSuccess(() -> Component.literal(
			"§7- loot_injection: " + oldConfig.isLootInjectionEnabled() + " → " + newConfig.isLootInjectionEnabled()), false);
		source.sendSuccess(() -> Component.literal(
			"§7- villager_trade: " + oldConfig.isVillagerTradeEnabled() + " → " + newConfig.isVillagerTradeEnabled()), false);
		source.sendSuccess(() -> Component.literal(
			"§7- resource_distribution: " + oldConfig.isResourceDistributionEnabled()
				+ " → " + newConfig.isResourceDistributionEnabled()), false);
		source.sendSuccess(() -> Component.literal(
			"§7- http_server_port: " + oldConfig.getHttpServerPort() + " → " + newConfig.getHttpServerPort()), false);
		source.sendSuccess(() -> Component.literal(
			"§7- http_server_host: " + formatHost(oldConfig.getHttpServerHost()) + " → " + formatHost(newConfig.getHttpServerHost())
			+ " §c(需重启生效)"), false);
		source.sendSuccess(() -> Component.literal(
			"§7- debug_enabled: " + oldConfig.isDebugEnabled() + " → " + newConfig.isDebugEnabled()), false);

		// 提示：附魔/互斥组/交易/战利品注入需重启服务端才能生效
		source.sendSuccess(() -> Component.literal(
			"§e[注意] §f附魔定义、互斥组、村民交易、战利品注入规则、http_server_host 的变更需重启服务端才能生效。"), false);
		return 1;
	}

	/**
	 * 执行 {@code /enchantlib debug status}：显示调试开关状态与系统统计。
	 */
	private int executeDebugStatus(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		boolean debugOn = DebugLogger.isEnabled();

		source.sendSuccess(() -> Component.literal(
			"§a[EnchantLib] §f调试模式: " + (debugOn ? "§a已开启" : "§c已关闭")), false);

		EnchantmentRegistrar enchantReg = EnchantLib.getEnchantmentRegistrar();
		ExclusiveGroupRegistrar groupReg = EnchantLib.getExclusiveGroupRegistrar();
		AcquisitionConfig config = EnchantLib.getAcquisitionConfig();

		int enchantCount = enchantReg != null ? enchantReg.size() : 0;
		int groupCount = groupReg != null ? groupReg.size() : 0;
		int callbackCount = com.enchantlib.event.EnchantmentEventDispatcher.registeredEnchantments();

		source.sendSuccess(() -> Component.literal(
			"§7- 已注册附魔: §f" + enchantCount + " 个"), false);
		source.sendSuccess(() -> Component.literal(
			"§7- 已注册互斥组: §f" + groupCount + " 个"), false);
		source.sendSuccess(() -> Component.literal(
			"§7- 事件回调覆盖附魔: §f" + callbackCount + " 个"), false);
		source.sendSuccess(() -> Component.literal(
			"§7- loot_injection: §f" + (config != null && config.isLootInjectionEnabled() ? "开启" : "关闭")), false);
		source.sendSuccess(() -> Component.literal(
			"§7- villager_trade: §f" + (config != null && config.isVillagerTradeEnabled() ? "开启" : "关闭")), false);
		source.sendSuccess(() -> Component.literal(
			"§7- resource_distribution: §f" + (config != null && config.isResourceDistributionEnabled() ? "开启" : "关闭")), false);
		source.sendSuccess(() -> Component.literal(
			"§7- http_server_host: §f" + (config != null ? formatHost(config.getHttpServerHost()) : "未知")), false);
		source.sendSuccess(() -> Component.literal(
			"§7- 配置文件 debug_enabled: §f" + (config != null ? config.isDebugEnabled() : "未知")), false);
		source.sendSuccess(() -> Component.literal(
			"§e[提示] §f使用 /enchantlib debug toggle 切换运行时调试开关"), false);
		return 1;
	}

	/**
	 * 格式化 http_server_host 显示文本。
	 *
	 * @param host 主机地址字符串
	 * @return 显示文本（空字符串显示为 "(auto)"）
	 */
	private static String formatHost(String host) {
		return (host == null || host.isEmpty()) ? "(auto)" : host;
	}

	/**
	 * 执行 {@code /enchantlib debug toggle}：切换运行时调试开关。
	 *
	 * <p>仅修改运行时状态，不持久化到配置文件。重启服务端后回到配置文件 {@code debug_enabled} 的值。</p>
	 */
	private int executeDebugToggle(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		boolean newState = !DebugLogger.isEnabled();
		DebugLogger.setEnabled(newState);

		source.sendSuccess(() -> Component.literal(
			"§a[EnchantLib] §f调试模式已" + (newState ? "§a开启" : "§c关闭") + " §7(运行时，重启后回到配置值)"), true);

		if (newState) {
			DebugLogger.log("调试模式已通过 /enchantlib debug toggle 开启");
			EnchantmentRegistrar enchantReg = EnchantLib.getEnchantmentRegistrar();
			ExclusiveGroupRegistrar groupReg = EnchantLib.getExclusiveGroupRegistrar();
			if (enchantReg != null) {
				DebugLogger.log("当前已注册 {} 个附魔:", enchantReg.size());
				for (EnchantmentBuilder b : enchantReg.getBuilders()) {
					DebugLogger.log("  - {} (maxLv={}, weight={}, exclusive={})",
						b.getId(), b.getMaxLevel(), b.getWeight(), b.getExclusiveSet());
				}
			}
			if (groupReg != null) {
				DebugLogger.log("当前已注册 {} 个互斥组:", groupReg.size());
				for (ExclusiveGroupBuilder g : groupReg.getBuilders()) {
					DebugLogger.log("  - #{} ({} 个成员)", g.getTagId(), g.getEnchantments().size());
				}
			}
		}
		return 1;
	}

	/**
	 * 执行 {@code /enchantlib debug info <enchantment_id>}：显示指定附魔的详细信息。
	 */
	private int executeDebugInfo(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		Identifier enchantId = IdentifierArgument.getId(ctx, "enchantment_id");
		String enchantIdStr = enchantId.toString();

		EnchantmentRegistrar registrar = EnchantLib.getEnchantmentRegistrar();
		if (registrar == null) {
			source.sendFailure(Component.literal("§c[EnchantLib] 注册器尚未初始化。"));
			return 0;
		}

		EnchantmentBuilder found = null;
		for (EnchantmentBuilder b : registrar.getBuilders()) {
			if (b.getId().toString().equals(enchantIdStr)) {
				found = b;
				break;
			}
		}
		if (found == null) {
			source.sendFailure(Component.literal(
				"§c[EnchantLib] 未找到 EnchantLib 注册的附魔: " + enchantIdStr));
			return 0;
		}

		final EnchantmentBuilder b = found;
		source.sendSuccess(() -> Component.literal(
			"§a[EnchantLib] §f附魔详情: §6" + enchantIdStr), false);
		source.sendSuccess(() -> Component.literal(
			"§7- 描述键: §f" + (b.getDescriptionKey() != null ? b.getDescriptionKey() : "(无)")), false);
		source.sendSuccess(() -> Component.literal(
			"§7- 描述回退: §f" + (b.getDescriptionFallback() != null ? b.getDescriptionFallback() : "(无)")), false);
		source.sendSuccess(() -> Component.literal(
			"§7- 最大等级: §f" + b.getMaxLevel()), false);
		source.sendSuccess(() -> Component.literal(
			"§7- 权重: §f" + b.getWeight()), false);
		source.sendSuccess(() -> Component.literal(
			"§7- 铁砧成本: §f" + b.getAnvilCost()), false);
		source.sendSuccess(() -> Component.literal(
			"§7- 支持物品: §f" + (b.getSupportedItems() != null ? b.getSupportedItems() : "(无)")), false);
		source.sendSuccess(() -> Component.literal(
			"§7- 主要物品: §f" + (b.getPrimaryItems() != null ? b.getPrimaryItems() : "(无)")), false);
		source.sendSuccess(() -> Component.literal(
			"§7- 互斥组: §f" + (b.getExclusiveSet() != null ? b.getExclusiveSet() : "(无)")), false);
		source.sendSuccess(() -> Component.literal(
			"§7- 槽位: §f" + String.join(", ", b.getSlots())), false);
		source.sendSuccess(() -> Component.literal(
			"§7- 战利品注入: §f" + b.isAcquisitionLoot()), false);
		source.sendSuccess(() -> Component.literal(
			"§7- 村民交易: §f" + b.isAcquisitionTrade()), false);

		DebugLogger.log("查询附魔详情: {}", enchantIdStr);
		return 1;
	}

	/**
	 * 创建一本附魔书，包含指定的附魔与等级。
	 *
	 * <p>使用 MC 26.2 的 {@link ItemEnchantments} 与 {@code STORED_ENCHANTMENTS} 数据组件。</p>
	 *
	 * @param enchantmentHolder 附魔 Holder
	 * @param level 附魔等级
	 * @return 附魔书 ItemStack
	 */
	private static ItemStack createEnchantedBook(Holder<Enchantment> enchantmentHolder, int level) {
		ItemStack bookStack = new ItemStack(Items.ENCHANTED_BOOK);
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		mutable.set(enchantmentHolder, level);
		bookStack.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
		return bookStack;
	}
}
