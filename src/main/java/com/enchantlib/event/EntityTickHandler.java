package com.enchantlib.event;

import com.enchantlib.EnchantLib;
import com.enchantlib.api.EntityCategory;
import com.enchantlib.api.EntityCounter;
import com.enchantlib.debug.DebugLogger;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * ENTITY_TICK 事件的正确实现。
 *
 * <p>按审查结论 O4 规格实现：</p>
 * <ol>
 *   <li><b>懒挂载</b>：{@link ServerTickEvents#END_SERVER_TICK} 仅在第一个 ENTITY_TICK 回调注册时才订阅（一次性）</li>
 *   <li><b>持有者索引</b>：维护有附魔装备的玩家集合，tick 时只遍历该集合。每 20 tick 全量重扫刷新索引</li>
 *   <li><b>防尖峰节流</b>：interval 判定用 {@code (serverTick + entity.getId()) % interval == 0}，实体 ID 做相位偏移</li>
 *   <li><b>移除 JOIN 桩</b>：JOIN 监听器仅在存在 ENTITY_TICK 回调时注册，用于加入索引（非触发事件）</li>
 * </ol>
 *
 * <h2>分发流程</h2>
 * <pre>{@code
 * ServerTickEvents.END_SERVER_TICK:
 *   └─ 每 tick：
 *        └─ for each player in indexedPlayers:
 *             └─ if (serverTick + player.getId()) % interval == 0:   // 防尖峰节流
 *                  └─ dispatch(ENTITY_TICK, event, player)
 *   └─ 每 20 tick：
 *        └─ 全量重扫所有在线玩家，更新 indexedPlayers（有附魔装备的加入，无的移除）
 * }</pre>
 *
 * <h2>持有者索引</h2>
 * <p>维护 {@code Set<UUID>} 标记有附魔装备的玩家。玩家 JOIN 时立即扫描加入索引，
 * QUIT 时移除。每 20 tick 全量重扫以捕获装备变更（避免写 setItemSlot Mixin）。</p>
 *
 * <h2>防尖峰节流</h2>
 * <p>当 {@code interval > 1} 时，每个玩家按 {@code (serverTick + entityId) % interval == 0} 判定是否触发。
 * 实体 ID 作为相位偏移，使不同玩家分散在不同 tick 触发，避免全服同一 tick 集中执行。</p>
 *
 * @since 0.1.0
 */
public final class EntityTickHandler {

	/** 持有者索引：有附魔装备的玩家 UUID 集合 */
	private static final ConcurrentHashMap<UUID, ServerPlayer> INDEXED_PLAYERS = new ConcurrentHashMap<>();

	/** 是否已订阅 ServerTickEvents.END_SERVER_TICK（一次性，不需要退订） */
	private static final AtomicBoolean SUBSCRIBED = new AtomicBoolean(false);

	/** 全局 tick 间隔（每 interval tick 触发一次，默认 20 = 每秒） */
	private static volatile int interval = 20;

	/** 索引刷新间隔（每 20 tick 全量重扫一次装备） */
	private static final int INDEX_REFRESH_INTERVAL = 20;

	private EntityTickHandler() {
	}

	/**
	 * 设置 tick 间隔。
	 *
	 * @param tickInterval 间隔（>= 1，1 表示每 tick 触发）
	 */
	public static void setInterval(int tickInterval) {
		interval = Math.max(1, tickInterval);
	}

	/**
	 * 懒挂载 ServerTickEvents.END_SERVER_TICK。
	 *
	 * <p>仅在第一个 ENTITY_TICK 回调注册时调用一次，后续调用无副作用。
	 * 同时注册 ServerPlayerEvents.JOIN/LEAVE 监听器维护持有者索引。</p>
	 *
	 * @param server MinecraftServer（用于初始化时全量扫描）
	 */
	public static void lazySubscribe(MinecraftServer server) {
		if (SUBSCRIBED.compareAndSet(false, true)) {
			ServerTickEvents.END_SERVER_TICK.register(EntityTickHandler::onServerTick);
			EnchantLib.LOGGER.info("[EnchantLib] ENTITY_TICK 已懒挂载到 ServerTickEvents.END_SERVER_TICK (interval={})", interval);

			// 初始全量扫描在线玩家（服务端刚启动时通常无玩家，但保险起见）
			refreshIndex(server);
		}
	}

	/**
	 * 玩家加入服务端时调用，加入持有者索引。
	 *
	 * <p>立即扫描玩家装备，若有附魔装备则加入索引。</p>
	 *
	 * @param player 加入的玩家
	 */
	public static void onPlayerJoin(ServerPlayer player) {
		if (hasEnchantedEquipment(player)) {
			INDEXED_PLAYERS.put(player.getUUID(), player);
			DebugLogger.log("[EnchantLib] 玩家 {} 加入 ENTITY_TICK 持有者索引", player.getName().getString());
		}
	}

	/**
	 * 玩家离开服务端时调用，从持有者索引移除。
	 *
	 * @param player 离开的玩家
	 */
	public static void onPlayerLeave(ServerPlayer player) {
		ServerPlayer removed = INDEXED_PLAYERS.remove(player.getUUID());
		if (removed != null) {
			// 清理分类标记,防止离线后标记残留
			EntityCategory.clear(player);
			// 清理计数器,防止离线后计数器残留
			EntityCounter.clear(player);
			DebugLogger.log("[EnchantLib] 玩家 {} 从 ENTITY_TICK 持有者索引移除", player.getName().getString());
		}
	}

	/**
	 * ServerTickEvents.END_SERVER_TICK 回调。
	 *
	 * <p>每 tick 遍历索引中的玩家 dispatch ENTITY_TICK（带防尖峰节流），
	 * 每 {@link #INDEX_REFRESH_INTERVAL} tick 全量重扫刷新索引。</p>
	 *
	 * @param server MinecraftServer
	 */
	private static void onServerTick(MinecraftServer server) {
		int serverTick = server.getTickCount();

		// 1. 每 INDEX_REFRESH_INTERVAL tick 全量重扫刷新索引
		// 必须在 isEmpty 早返回之前，否则索引为空时 refreshIndex 永远不执行，形成死锁
		if (serverTick % INDEX_REFRESH_INTERVAL == 0) {
			refreshIndex(server);
		}

		if (INDEXED_PLAYERS.isEmpty()) {
			return;
		}

		// 2. 遍历索引中的玩家，dispatch ENTITY_TICK（带防尖峰节流）
		Iterator<ServerPlayer> it = INDEXED_PLAYERS.values().iterator();
		while (it.hasNext()) {
			ServerPlayer player = it.next();
			// 检查玩家是否已离线（并发场景下索引可能未及时清理）
			if (player.hasDisconnected() || player.isRemoved()) {
				it.remove();
				continue;
			}

			// 防尖峰节流：interval > 1 时按实体 ID 相位偏移
			if (interval > 1 && (serverTick + player.getId()) % interval != 0) {
				continue;
			}

			// 使用玩家所在维度的 ServerLevel，而非 server.overworld()。
			// 否则跨维度（下界/末地）玩家会收到主世界 level，
			// 导致回调中 serverLevel.getBlockState/partition 等查询错误。
			// MC 26.2 中 ServerPlayer 重写了 level() 返回 ServerLevel（无 serverLevel() 方法）。
			ServerLevel playerLevel = player.level();
			BuiltInEvents.EntityTickEvent event = new BuiltInEvents.EntityTickEvent(
				playerLevel, player, serverTick);
			EnchantmentEventDispatcher.dispatch(BuiltInEvents.ENTITY_TICK, event, player);
		}
	}

	/**
	 * 全量重扫所有在线玩家，更新持有者索引。
	 *
	 * <p>遍历所有在线玩家，扫描装备是否有附魔：
	 * 有附魔的加入索引，无附魔的从索引移除。</p>
	 *
	 * @param server MinecraftServer
	 */
	private static void refreshIndex(MinecraftServer server) {
		// 收集当前有附魔装备的玩家 UUID
		java.util.Set<UUID> currentEnchanted = new java.util.HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (hasEnchantedEquipment(player)) {
				currentEnchanted.add(player.getUUID());
				INDEXED_PLAYERS.put(player.getUUID(), player);
			}
		}

		// 移除索引中已无附魔装备或已离线的玩家
		Iterator<java.util.Map.Entry<UUID, ServerPlayer>> it = INDEXED_PLAYERS.entrySet().iterator();
		while (it.hasNext()) {
			java.util.Map.Entry<UUID, ServerPlayer> entry = it.next();
			UUID uuid = entry.getKey();
			if (!currentEnchanted.contains(uuid)) {
				it.remove();
				// 清理分类标记,防止卸下附魔装备后标记残留
				ServerPlayer removed = entry.getValue();
				EntityCategory.clear(removed);
				DebugLogger.log("[EnchantLib] 玩家 UUID {} 已从 ENTITY_TICK 索引移除（无附魔装备或离线）", uuid);
			}
		}
	}

	/**
	 * 检查实体是否有附魔装备。
	 *
	 * <p>遍历所有装备槽位，若有任意一件附魔装备返回 true。
	 * 复用 {@link EnchantmentScanner#scanAllSlots(LivingEntity)} 的扫描逻辑，
	 * 但只判断是否为空（不构造 context 列表，减少分配）。</p>
	 *
	 * @param entity 要检查的实体
	 * @return true 若有任意附魔装备
	 */
	private static boolean hasEnchantedEquipment(LivingEntity entity) {
		return !EnchantmentScanner.scanAllSlots(entity).isEmpty();
	}

	/**
	 * 获取当前持有者索引大小（用于调试/统计）。
	 *
	 * @return 索引中的玩家数量
	 */
	public static int indexedPlayerCount() {
		return INDEXED_PLAYERS.size();
	}

	/**
	 * 获取当前 tick 间隔。
	 *
	 * @return 间隔（>= 1）
	 */
	public static int getInterval() {
		return interval;
	}
}
