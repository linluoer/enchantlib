package com.enchantlib;

import com.enchantlib.api.EnchantmentBuilder;
import com.enchantlib.api.EnchantmentEntrypoint;
import com.enchantlib.api.EnchantmentRegistrar;
import com.enchantlib.api.ExclusiveGroupBuilder;
import com.enchantlib.api.ExclusiveGroupRegistrar;
import com.enchantlib.api.LootInjectionBuilder;
import com.enchantlib.api.LootInjectionRegistrar;
import com.enchantlib.api.LootTables;
import com.enchantlib.api.TradeableEnchantmentsBuilder;
import com.enchantlib.api.VillagerTradeRegistrar;
import com.enchantlib.command.EnchantLibCommand;
import com.enchantlib.config.AcquisitionConfig;
import com.enchantlib.config.ConfigLoader;
import com.enchantlib.config.TriggerPolicyConfig;
import com.enchantlib.datapack.RuntimeDatapackContent;
import com.enchantlib.debug.DebugLogger;
import com.enchantlib.event.BuiltInEvents;
import com.enchantlib.event.EnchantLibEvents;
import com.enchantlib.event.EnchantmentEventDispatcher;
import com.enchantlib.event.EnchantmentEventRegistrar;
import com.enchantlib.event.EntityTickHandler;
import com.enchantlib.event.LivingEntityTickEvent;
import com.enchantlib.event.TriggerPolicy;
import com.enchantlib.loot.LootInjectionHandler;
import com.enchantlib.resources.ClientResourcePackBuilder;
import com.enchantlib.resources.EnchantSyncScanner;
import com.enchantlib.resources.LanguageMerger;
import com.enchantlib.resources.ResourcePackHttpServer;
import com.enchantlib.util.SmeltingLookup;
import com.enchantlib.validation.EnchantmentValidator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EnchantLib 主入口。
 *
 * <p>EnchantLib 是一个纯服务端模组，为其他模组提供自定义附魔的注册管线、
 * 获取途径、事件系统与资源分发能力。</p>
 *
 * @since 0.1.0
 */
public class EnchantLib implements ModInitializer {
	public static final String MOD_ID = "enchantlib";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** entrypoint key，其他模组通过此 Key 在 fabric.mod.json 声明附魔注册入口 */
	public static final String ENTRYPOINT_KEY = "enchantlib:enchantments";

	/** 已注册的附魔列表（指令系统访问，onInitialize 后非 null） */
	private static volatile EnchantmentRegistrar enchantmentRegistrar;
	/** 已注册的互斥组列表（指令系统访问，onInitialize 后非 null） */
	private static volatile ExclusiveGroupRegistrar exclusiveGroupRegistrar;
	/** 全局获取途径配置（reload 指令重新加载） */
	private static volatile AcquisitionConfig acquisitionConfig;
	/** 触发策略配置（trigger.toml，启动时加载，按附魔 ID 覆盖注册时的默认 policy） */
	private static volatile TriggerPolicyConfig triggerPolicyConfig;

	@Override
	public void onInitialize() {
		LOGGER.info("[EnchantLib] 服务端附魔库已加载。");

		// 加载全局获取途径配置
		acquisitionConfig = AcquisitionConfig.load();

		// 初始化调试开关（受配置文件 debug_enabled 控制）
		DebugLogger.setEnabled(acquisitionConfig.isDebugEnabled());
		LOGGER.info("[EnchantLib] 调试模式: {} (配置文件 debug_enabled={})",
			DebugLogger.isEnabled() ? "已开启" : "已关闭", acquisitionConfig.isDebugEnabled());
		DebugLogger.log("EnchantLib onInitialize 开始");

		enchantmentRegistrar = new EnchantmentRegistrar();
		exclusiveGroupRegistrar = new ExclusiveGroupRegistrar();

		// 收集 entrypoint 注册的附魔
		List<EnchantmentEntrypoint> entrypoints = FabricLoader.getInstance()
			.getEntrypoints(ENTRYPOINT_KEY, EnchantmentEntrypoint.class);

		for (EnchantmentEntrypoint entrypoint : entrypoints) {
			try {
				entrypoint.onRegisterEnchantments(enchantmentRegistrar);
			} catch (Throwable t) {
				LOGGER.error("[EnchantLib] 收集附魔 entrypoint 时出错: {}", t.getMessage(), t);
			}
		}

		int entrypointCount = enchantmentRegistrar.size();
		if (entrypointCount > 0) {
			LOGGER.info("[EnchantLib] 从 {} 个 entrypoint 收集到 {} 个自定义附魔", entrypoints.size(), entrypointCount);
		}

		// 收集 entrypoint 注册的互斥组
		for (EnchantmentEntrypoint entrypoint : entrypoints) {
			try {
				entrypoint.onRegisterExclusiveGroups(exclusiveGroupRegistrar);
			} catch (Throwable t) {
				LOGGER.error("[EnchantLib] 收集互斥组 entrypoint 时出错: {}", t.getMessage(), t);
			}
		}

		int groupCount = exclusiveGroupRegistrar.size();
		if (groupCount > 0) {
			LOGGER.info("[EnchantLib] 从 entrypoint 收集到 {} 个自定义互斥组", groupCount);
		}

		// 加载配置文件定义的附魔
		List<EnchantmentBuilder> configEnchantments = ConfigLoader.loadAll();
		for (EnchantmentBuilder builder : configEnchantments) {
			try {
				enchantmentRegistrar.register(builder);
			} catch (IllegalStateException e) {
				LOGGER.error("[EnchantLib] 配置附魔注册失败（ID 重复）: {}", e.getMessage());
			}
		}

		// 冻结前全局校验（fail-fast，发现错误立即崩溃）
		EnchantmentValidator.validate(enchantmentRegistrar.getBuilders(), exclusiveGroupRegistrar.getBuilders());

		// 将收集到的附魔和互斥组注入运行时数据包
		RuntimeDatapackContent.setCustomEnchantments(enchantmentRegistrar.getBuilders());
		RuntimeDatapackContent.setExclusiveGroups(exclusiveGroupRegistrar.getBuilders());
		DebugLogger.log("注册管线完成: {} 个附魔, {} 个互斥组", enchantmentRegistrar.size(), exclusiveGroupRegistrar.size());

		// 收集 entrypoint 注册的战利品注入规则（受全局开关控制）
		LootInjectionRegistrar lootRegistrar = new LootInjectionRegistrar();
		if (acquisitionConfig.isLootInjectionEnabled()) {
			for (EnchantmentEntrypoint entrypoint : entrypoints) {
				try {
					entrypoint.onRegisterLootInjections(lootRegistrar);
				} catch (Throwable t) {
					LOGGER.error("[EnchantLib] 收集战利品注入 entrypoint 时出错: {}", t.getMessage(), t);
				}
			}

			// 为配置文件定义的附魔自动注册战利品注入（acquisition.loot = true）
			Set<String> autoLootEnchantments = new LinkedHashSet<>();
			for (EnchantmentBuilder builder : enchantmentRegistrar.getBuilders()) {
				if (builder.isAcquisitionLoot()) {
					autoLootEnchantments.add(builder.getId().toString());
				}
			}
			if (!autoLootEnchantments.isEmpty()) {
				LOGGER.info("[EnchantLib] 为 {} 个配置附魔自动注册战利品注入", autoLootEnchantments.size());
				lootRegistrar.register(LootInjectionBuilder.create()
					.toTables(LootTables.SIMPLE_DUNGEON, LootTables.ABANDONED_MINESHAFT)
					.asBook()
					.withEnchantments(autoLootEnchantments)
					.chance(0.5F)
					.weight(1)
					.quality(0));
			}

			int lootInjectionCount = lootRegistrar.size();
			if (lootInjectionCount > 0) {
				LOGGER.info("[EnchantLib] 从 entrypoint 收集到 {} 条战利品注入规则", lootInjectionCount);
				// 初始化战利品注入处理器（注册 LootTableEvents.MODIFY 监听器）
				LootInjectionHandler.initialize(lootRegistrar.getInjections());
				DebugLogger.log("战利品注入: {} 条规则已注册", lootInjectionCount);
			}
		} else {
			LOGGER.info("[EnchantLib] 全局战利品注入已关闭（acquisition.toml: loot_injection_enabled=false）");
		}

		// 收集 entrypoint 注册的村民交易（受全局开关控制）
		VillagerTradeRegistrar villagerTradeRegistrar = new VillagerTradeRegistrar();
		if (acquisitionConfig.isVillagerTradeEnabled()) {
			for (EnchantmentEntrypoint entrypoint : entrypoints) {
				try {
					entrypoint.onRegisterVillagerTrades(villagerTradeRegistrar);
				} catch (Throwable t) {
					LOGGER.error("[EnchantLib] 收集村民交易 entrypoint 时出错: {}", t.getMessage(), t);
				}
			}

			// 为配置文件定义的附魔自动加入 tradeable 标签（acquisition.trade = true）
			Set<String> autoTradeEnchantments = new LinkedHashSet<>();
			for (EnchantmentBuilder builder : enchantmentRegistrar.getBuilders()) {
				if (builder.isAcquisitionTrade()) {
					autoTradeEnchantments.add(builder.getId().toString());
				}
			}
			if (!autoTradeEnchantments.isEmpty()) {
				LOGGER.info("[EnchantLib] 为 {} 个配置附魔自动加入 #minecraft:tradeable 标签", autoTradeEnchantments.size());
				villagerTradeRegistrar.registerTradeableEnchantments(
					TradeableEnchantmentsBuilder.create().addEnchantments(autoTradeEnchantments));
			}

			if (!villagerTradeRegistrar.isEmpty()) {
				LOGGER.info("[EnchantLib] 从 entrypoint 收集到 {} 个可交易附魔, {} 条自定义村民交易",
					villagerTradeRegistrar.tradeableEnchantmentsCount(), villagerTradeRegistrar.tradesCount());
				RuntimeDatapackContent.setVillagerTrades(
					villagerTradeRegistrar.getTradeableEnchantments(),
					villagerTradeRegistrar.getTrades());
			}
		} else {
			LOGGER.info("[EnchantLib] 全局村民交易已关闭（acquisition.toml: villager_trade_enabled=false）");
		}

		// 扩展钩子：注册 Fabric API 事件监听器，将原生事件桥接到 EnchantLib 事件系统
		registerExtensionHooks();

		// 扫描所有模组 assets 目录下任意命名空间的 enchant_sync/ 目录，收集客户端资源
		// 无条件执行：既服务服务端资源分发，也作为客户端本地资源包注入（RuntimeClientPackContent）的数据源
		int scannedCount = EnchantSyncScanner.scan();
		int langCount = EnchantSyncScanner.getLangFiles().size();
		LOGGER.info("[EnchantLib] 资源扫描完成: 共 {} 个资源文件，其中 {} 个语言文件",
			scannedCount, langCount);

		// 服务端资源分发系统（受全局开关控制；客户端本地注入不受此开关影响）
		if (acquisitionConfig.isResourceDistributionEnabled()) {
			// 合并所有模组的语言文件，按语言代码生成统一翻译表
			int langCodeCount = LanguageMerger.merge();
			LOGGER.info("[EnchantLib] 语言合并完成: {} 种语言", langCodeCount);
			for (String langCode : LanguageMerger.getMergedLanguages().keySet()) {
				LOGGER.info("[EnchantLib] 语言 {} 合并结果: {} 个键", langCode,
					LanguageMerger.getTranslations(langCode).size());
			}

			// 构建客户端运行时资源包（ZIP），包含合并后的语言文件和其他客户端资源
			int builtCount = ClientResourcePackBuilder.build();
			if (ClientResourcePackBuilder.isBuilt()) {
				LOGGER.info("[EnchantLib] 资源包已构建: {} 个文件, {} 字节, sha1={}",
					builtCount, ClientResourcePackBuilder.getSize(), ClientResourcePackBuilder.getSha1());
			}
		} else {
			LOGGER.info("[EnchantLib] 服务端资源分发已关闭（acquisition.toml: resource_distribution_enabled=false），"
				+ "客户端本地注入不受影响");
		}

		// 收集 entrypoint 注册的事件回调
		// 需要在注册表完全就绪后收集（onInitialize 阶段附魔注册表已可访问，但为安全起见推迟到 SERVER_STARTED）
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			// 初始化熔炼配方缓存查询（自动烧炼附魔需要）
			SmeltingLookup.initialize(server);
			LOGGER.info("[EnchantLib] 熔炼配方缓存已初始化（{} 个配方已缓存）", SmeltingLookup.cacheSize());

			// 启动内置 HTTP 服务器，提供资源包下载
			if (ClientResourcePackBuilder.isBuilt()) {
				int httpPort = ResourcePackHttpServer.start(
					acquisitionConfig.getHttpServerPort(),
					acquisitionConfig.getHttpServerHost());
				if (httpPort > 0) {
					LOGGER.info("[EnchantLib] 资源包分发已就绪: {}", ResourcePackHttpServer.getResourcePackUrl());
				} else {
					LOGGER.warn("[EnchantLib] HTTP 服务器启动失败，资源包分发不可用");
				}
			} else {
				LOGGER.info("[EnchantLib] 资源包未构建，跳过 HTTP 服务器启动");
			}

			EnchantmentEventRegistrar eventRegistrar = new EnchantmentEventRegistrar();
			for (EnchantmentEntrypoint entrypoint : entrypoints) {
				try {
					entrypoint.onRegisterEventCallbacks(eventRegistrar, server.registryAccess());
				} catch (Throwable t) {
					LOGGER.error("[EnchantLib] 收集事件回调 entrypoint 时出错: {}", t.getMessage(), t);
				}
			}
			int callbackCount = eventRegistrar.registeredCount();
			if (callbackCount > 0) {
				LOGGER.info("[EnchantLib] 从 entrypoint 收集到 {} 个事件回调，覆盖 {} 个附魔",
					callbackCount, EnchantmentEventDispatcher.registeredEnchantments());

				// 加载触发策略配置并应用附魔级覆盖
				triggerPolicyConfig = TriggerPolicyConfig.load();
				applyTriggerPolicyOverrides();

				// F2/O4: ENTITY_TICK 正确实现 —— 懒挂载 + 持有者索引 + 防尖峰节流
				// 仅当存在 ENTITY_TICK 回调时才订阅 ServerTickEvents.END_SERVER_TICK
				int entityTickCount = EnchantmentEventDispatcher.callbackCount(BuiltInEvents.ENTITY_TICK);
				if (entityTickCount > 0) {
					EntityTickHandler.setInterval(acquisitionConfig.getEntityTickInterval());
					EntityTickHandler.lazySubscribe(server);
					LOGGER.info("[EnchantLib] ENTITY_TICK 已启用：{} 个回调，interval={} tick，持有者索引懒挂载",
						entityTickCount, acquisitionConfig.getEntityTickInterval());

					// 注册 JOIN/LEAVE 监听器维护持有者索引
					ServerPlayerEvents.JOIN.register(EntityTickHandler::onPlayerJoin);
					ServerPlayerEvents.LEAVE.register(EntityTickHandler::onPlayerLeave);
				} else {
					LOGGER.info("[EnchantLib] 无 ENTITY_TICK 回调，跳过 tick 订阅");
				}
			} else {
				LOGGER.info("[EnchantLib] 未收集到事件回调，事件分发器处于待命状态");
			}

			// 玩家加入时发送资源包推送包
			if (ClientResourcePackBuilder.isBuilt() && ResourcePackHttpServer.isRunning()) {
				ServerPlayerEvents.JOIN.register(player -> {
					sendResourcePackPush(player);
				});
				LOGGER.info("[EnchantLib] 资源包推送已注册，玩家加入时将自动发送");
			}

			// LIVING_ENTITY_TICK 全局事件分发（懒挂载，仅当 example-mod 调用 enableLivingEntityTick() 时启用）
			if (EnchantLibEvents.isLivingEntityTickEnabled()) {
				ServerTickEvents.END_SERVER_TICK.register(minecraftServer -> {
					int tickCount = minecraftServer.getTickCount();
					for (ServerLevel level : minecraftServer.getAllLevels()) {
						// 先快照实体列表再迭代：回调（如焚心 hurt 致死）可能触发掉落物 addFreshEntity
						// 或实体移除，修改 EntityLookup.byId（getAllEntities 返回其 values 视图）。
						// MC 原版用 EntityTickList 做并发保护（迭代时 add/remove 切换 passive 副本），
						// getAllEntities() 无此保护，直接迭代在结构性修改时行为未定义，需快照。
						List<Entity> snapshot = new ArrayList<>();
						level.getAllEntities().forEach(snapshot::add);
						for (Entity entity : snapshot) {
							// 快照后实体可能已被移除，加 isRemoved 保护避免操作已死实体
							if (!entity.isRemoved() && entity instanceof LivingEntity living) {
								EnchantLibEvents.LIVING_ENTITY_TICK.invoker().onTick(
									new LivingEntityTickEvent(level, living, tickCount));
							}
						}
					}
				});
				LOGGER.info("[EnchantLib] LIVING_ENTITY_TICK 已启用：每 tick 遍历所有 LivingEntity 分发全局事件");
			} else {
				LOGGER.info("[EnchantLib] LIVING_ENTITY_TICK 未启用（无全局回调注册）");
			}
		});

		// 服务端关闭时停止 HTTP 服务器
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ResourcePackHttpServer.stop();
		});

		// 数据包重载时清空熔炼配方缓存（配方可能变更）
		ServerLifecycleEvents.START_DATA_PACK_RELOAD.register((server, resourceManager) -> {
			SmeltingLookup.invalidate();
			LOGGER.info("[EnchantLib] 熔炼配方缓存已清空（数据包重载）");
		});

		// 注册 /enchantlib 指令系统（list/give/groups/dump/reload/debug）
		CommandRegistrationCallback.EVENT.register(new EnchantLibCommand());
		LOGGER.info("[EnchantLib] 指令系统已注册: /enchantlib list|give|groups|dump|reload|debug");
		DebugLogger.log("EnchantLib onInitialize 完成");
	}

	/**
	 * 向玩家发送资源包推送包。
	 *
	 * <p>通过 MC 原生资源包推送机制（{@link ClientboundResourcePackPushPacket}），
	 * 告知客户端下载 EnchantLib 运行时资源包。客户端会自动下载并提示玩家应用。</p>
	 *
	 * @param player 目标玩家
	 */
	private static void sendResourcePackPush(ServerPlayer player) {
		String url = ResourcePackHttpServer.getResourcePackUrl();
		String sha1 = ClientResourcePackBuilder.getSha1();
		if (url == null || sha1 == null || sha1.isEmpty()) {
			LOGGER.warn("[EnchantLib] 无法发送资源包推送: URL 或 SHA1 不可用");
			return;
		}

		UUID packId = UUID.nameUUIDFromBytes(("enchantlib:" + sha1).getBytes());
		Component prompt = Component.literal("EnchantLib 资源包（附魔本地化名称）");

		ClientboundResourcePackPushPacket packet = new ClientboundResourcePackPushPacket(
			packId, url, sha1, false, Optional.of(prompt));
		player.connection.send(packet);

		LOGGER.info("[EnchantLib] 已向玩家 {} 发送资源包推送: url={}, sha1={}",
			player.getName().getString(), url, sha1);
	}

	/**
	 * 应用触发策略配置覆盖。
	 *
	 * <p>遍历所有已注册回调的附魔，按附魔 ID 查询 {@link TriggerPolicyConfig} 的覆盖配置，
	 * 若存在覆盖则替换该附魔所有回调的 {@link TriggerPolicy}。</p>
	 *
	 * <p>应在 entrypoint 收集完成后调用（SERVER_STARTED 阶段）。</p>
	 */
	private static void applyTriggerPolicyOverrides() {
		if (triggerPolicyConfig == null) {
			return;
		}
		int overrideCount = 0;
		for (Holder<Enchantment> holder : EnchantmentEventDispatcher.registeredEnchantmentHolders()) {
			String id = holder.unwrapKey()
				.map(key -> key.identifier().toString())
				.orElse(null);
			if (id == null) {
				continue;
			}
			TriggerPolicy override = triggerPolicyConfig.getOverride(id);
			if (override != null) {
				EnchantmentEventDispatcher.applyPolicyOverride(holder, override);
				overrideCount++;
				LOGGER.info("[EnchantLib] 触发策略覆盖已应用: {} → {}", id, override);
			}
		}
		if (overrideCount > 0) {
			LOGGER.info("[EnchantLib] 触发策略覆盖完成: {} 个附魔的策略被配置文件覆盖", overrideCount);
		}
	}

	/**
	 * 扩展钩子：注册 Fabric API 事件监听器，将原生事件桥接到 EnchantLib 事件系统。
	 *
	 * <p>注册的监听器：</p>
	 * <ul>
	 *   <li>{@code ServerLivingEntityEvents.AFTER_DAMAGE} → {@link BuiltInEvents#POST_HURT}</li>
	 *   <li>{@code ServerLivingEntityEvents.AFTER_DEATH} → {@link BuiltInEvents#POST_KILL}</li>
	 *   <li>{@code PlayerBlockBreakEvents.AFTER} → {@link BuiltInEvents#POST_BLOCK_BREAK}</li>
	 *   <li>{@code UseItemCallback.EVENT} → {@link BuiltInEvents#ITEM_USE}</li>
	 *   <li>{@code UseBlockCallback.EVENT} → {@link BuiltInEvents#BLOCK_USE}</li>
	 *   <li>{@code UseEntityCallback.EVENT} → {@link BuiltInEvents#ENTITY_USE}</li>
	 * </ul>
	 */
	private void registerExtensionHooks() {
		// POST_HURT：受击后事件，扫描目标的装备附魔
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, amount, blockedDamage, blocked) -> {
			if (!(entity.level() instanceof ServerLevel serverLevel)) {
				return;
			}
			LivingEntity attacker = null;
			Entity attackerEntity = source.getEntity();
			if (attackerEntity instanceof LivingEntity livingAttacker) {
				attacker = livingAttacker;
			}
			BuiltInEvents.PostHurtEvent event = new BuiltInEvents.PostHurtEvent(
				serverLevel,
				entity,
				attacker,
				source,
				amount,
				blockedDamage,
				blocked
			);
			EnchantmentEventDispatcher.dispatch(BuiltInEvents.POST_HURT, event, entity);
		});

		// POST_KILL：击杀后事件，扫描击杀者的装备附魔
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity.level() instanceof ServerLevel serverLevel)) {
				return;
			}
			Entity killerEntity = source.getEntity();
			if (!(killerEntity instanceof LivingEntity killer)) {
				return;
			}
			BuiltInEvents.PostKillEvent event = new BuiltInEvents.PostKillEvent(
				serverLevel,
				killer,
				entity,
				source
			);
			EnchantmentEventDispatcher.dispatch(BuiltInEvents.POST_KILL, event, killer);
		});

		// POST_BLOCK_BREAK：方块破坏后事件（纯通知），扫描玩家的主手/副手装备附魔
		// 注意：PlayerBlockBreakEvents.AFTER 不提供 tool 参数，从玩家主手获取
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
				return;
			}
			// O1 位掩码短路
			if (!EnchantmentEventDispatcher.hasCallbacks(BuiltInEvents.POST_BLOCK_BREAK)) {
				return;
			}
			ItemStack tool = serverPlayer.getMainHandItem();
			BuiltInEvents.PostBlockBreakEvent event = new BuiltInEvents.PostBlockBreakEvent(
				serverLevel, serverPlayer, pos, state, tool);
			EnchantmentEventDispatcher.dispatch(BuiltInEvents.POST_BLOCK_BREAK, event, serverPlayer);
		});

		// ITEM_USE：物品使用事件，扫描触发手的装备附魔
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!(player instanceof ServerPlayer serverPlayer)
				|| !(level instanceof ServerLevel serverLevel) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			Mutable<InteractionResult> result = new MutableObject<>(InteractionResult.PASS);
			ItemStack itemStack = serverPlayer.getItemInHand(hand);
			BuiltInEvents.ItemUseEvent event = new BuiltInEvents.ItemUseEvent(
				serverLevel, serverPlayer, hand, itemStack, result);
			return EnchantmentEventDispatcher.dispatchInteraction(
				BuiltInEvents.ITEM_USE, event, serverPlayer, handToSlot(hand));
		});

		// BLOCK_USE：方块交互事件，扫描触发手的装备附魔
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (!(player instanceof ServerPlayer serverPlayer)
				|| !(level instanceof ServerLevel serverLevel) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			Mutable<InteractionResult> result = new MutableObject<>(InteractionResult.PASS);
			BuiltInEvents.BlockUseEvent event = new BuiltInEvents.BlockUseEvent(
				serverLevel, serverPlayer, hand, hitResult, result);
			return EnchantmentEventDispatcher.dispatchInteraction(
				BuiltInEvents.BLOCK_USE, event, serverPlayer, handToSlot(hand));
		});

		// ENTITY_USE：实体交互事件，扫描触发手的装备附魔
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!(player instanceof ServerPlayer serverPlayer)
				|| !(level instanceof ServerLevel serverLevel) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			Mutable<InteractionResult> result = new MutableObject<>(InteractionResult.PASS);
			BuiltInEvents.EntityUseEvent event = new BuiltInEvents.EntityUseEvent(
				serverLevel, serverPlayer, hand, entity, hitResult, result);
			return EnchantmentEventDispatcher.dispatchInteraction(
				BuiltInEvents.ENTITY_USE, event, serverPlayer, handToSlot(hand));
		});

		LOGGER.info("[EnchantLib] 扩展钩子已注册：POST_HURT, POST_KILL, POST_BLOCK_BREAK, ITEM_USE, BLOCK_USE, ENTITY_USE");
	}

	/**
	 * 将 {@link InteractionHand} 转换为对应的 {@link EquipmentSlot}。
	 *
	 * @param hand 交互手
	 * @return 对应的装备槽位
	 */
	private static EquipmentSlot handToSlot(InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
	}

	/**
	 * 获取已注册的附魔注册器（指令系统访问）。
	 *
	 * @return 附魔注册器，若 onInitialize 未完成返回 null
	 */
	public static EnchantmentRegistrar getEnchantmentRegistrar() {
		return enchantmentRegistrar;
	}

	/**
	 * 获取已注册的互斥组注册器（指令系统访问）。
	 *
	 * @return 互斥组注册器，若 onInitialize 未完成返回 null
	 */
	public static ExclusiveGroupRegistrar getExclusiveGroupRegistrar() {
		return exclusiveGroupRegistrar;
	}

	/**
	 * 获取当前的全局获取途径配置（指令系统访问）。
	 *
	 * @return 配置实例，若 onInitialize 未完成返回 null
	 */
	public static AcquisitionConfig getAcquisitionConfig() {
		return acquisitionConfig;
	}

	/**
	 * 重新加载全局获取途径配置（reload 指令调用）。
	 *
	 * <p>仅重新读取 {@code config/enchantlib/acquisition.toml} 文件并替换静态实例。
	 * 已注入到运行时数据包的附魔/互斥组/村民交易/战利品注入规则无法热重载，
	 * 需重启服务端才能生效。</p>
	 *
	 * @return 新加载的配置实例
	 */
	public static AcquisitionConfig reloadAcquisitionConfig() {
		AcquisitionConfig newConfig = AcquisitionConfig.load();
		acquisitionConfig = newConfig;
		// 同步调试开关（仅当运行时未通过 /enchantlib debug toggle 修改时才覆盖）
		DebugLogger.setEnabled(newConfig.isDebugEnabled());
		LOGGER.info("[EnchantLib] 全局获取途径配置已重新加载: loot_injection={}, villager_trade={}, resource_distribution={}, http_port={}, debug={}",
			newConfig.isLootInjectionEnabled(), newConfig.isVillagerTradeEnabled(),
			newConfig.isResourceDistributionEnabled(), newConfig.getHttpServerPort(), newConfig.isDebugEnabled());
		return newConfig;
	}
}
