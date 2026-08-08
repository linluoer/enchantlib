package com.enchantlib.api;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * 玩家生物分类 API。
 *
 * <p>允许将玩家标记为某种生物分类(亡灵/节肢动物/灾厄村民/水生生物),
 * 使服务端在 Mob 目标选择、亡灵杀手附魔、药水效果等场景中将玩家视为该分类。</p>
 *
 * <h2>设计理念</h2>
 * <p>与"实体伪装"不同,本 API <b>不覆盖 {@code getType()}</b>,不影响存档、掉落物、
 * 统计、成就等逻辑。只在关键判定点查询分类标签,影响面可控。</p>
 *
 * <h2>分类数据源</h2>
 * <p>非玩家实体的分类查询使用原版 {@link EntityTypeTags} 标签(如 {@code undead}、
 * {@code arthropod}),自动支持其他模组通过标签注册的同类生物。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 装备亡灵契约头盔时,将玩家标记为亡灵
 * EntityCategory.set(player, EntityCategory.Category.UNDEAD);
 *
 * // 卸下头盔时清除分类
 * EntityCategory.clear(player);
 *
 * // 查询玩家是否被标记为亡灵
 * boolean isUndead = EntityCategory.has(player, EntityCategory.Category.UNDEAD);
 *
 * // 通用查询:任意实体(包括玩家)是否属于某分类
 * boolean isUndead = EntityCategory.isUndead(entity);
 * }</pre>
 *
 * @since 0.1.0
 */
public final class EntityCategory {

	/**
	 * 生物分类枚举。
	 *
	 * <p>每个分类对应一个原版 {@link EntityTypeTags} 标签,
	 * 用于查询非玩家实体是否属于该分类。</p>
	 */
	public enum Category {
		/** 亡灵:僵尸、骷髅、凋灵、幻翼等 */
		UNDEAD(EntityTypeTags.UNDEAD),
		/** 节肢动物:蜘蛛、洞穴蜘蛛、蠹虫、末影螨、蜜蜂 */
		ARTHROPOD(EntityTypeTags.ARTHROPOD),
		/** 灾厄村民:掠夺者、唤魔者、卫道士、女巫等 */
		ILLAGER(EntityTypeTags.ILLAGER),
		/** 水生生物:守卫者、远古守卫者、鱿鱼、海豚等 */
		AQUATIC(EntityTypeTags.AQUATIC);

		private final TagKey<EntityType<?>> tag;

		Category(TagKey<EntityType<?>> tag) {
			this.tag = tag;
		}

		/**
		 * 获取该分类对应的原版实体标签。
		 *
		 * @return 标签键
		 */
		public TagKey<EntityType<?>> tag() {
			return tag;
		}
	}

	/** 玩家 UUID → 分类集合(同步包装的 EnumSet,线程安全) */
	private static final ConcurrentHashMap<UUID, Set<Category>> PLAYER_CATEGORIES = new ConcurrentHashMap<>();

	private EntityCategory() {
	}

	/**
	 * 设置玩家的分类(覆盖原有分类)。
	 *
	 * @param player   目标玩家
	 * @param category 分类
	 */
	public static void set(ServerPlayer player, Category category) {
		Set<Category> set = Collections.synchronizedSet(EnumSet.of(category));
		PLAYER_CATEGORIES.put(player.getUUID(), set);
	}

	/**
	 * 设置玩家的多个分类(覆盖原有分类)。
	 *
	 * @param player     目标玩家
	 * @param categories 分类列表
	 */
	public static void set(ServerPlayer player, Category... categories) {
		Set<Category> set = EnumSet.noneOf(Category.class);
		for (Category c : categories) {
			set.add(c);
		}
		PLAYER_CATEGORIES.put(player.getUUID(), Collections.synchronizedSet(set));
	}

	/**
	 * 给玩家添加一个分类(不影响其他已设置的分类)。
	 *
	 * @param player   目标玩家
	 * @param category 分类
	 */
	public static void add(ServerPlayer player, Category category) {
		PLAYER_CATEGORIES.computeIfAbsent(player.getUUID(),
				k -> Collections.synchronizedSet(EnumSet.noneOf(Category.class)))
			.add(category);
	}

	/**
	 * 移除玩家的某个分类。若移除后无任何分类,则从映射中删除该玩家。
	 *
	 * @param player   目标玩家
	 * @param category 分类
	 */
	public static void remove(ServerPlayer player, Category category) {
		Set<Category> set = PLAYER_CATEGORIES.get(player.getUUID());
		if (set != null) {
			synchronized (set) {
				set.remove(category);
				if (set.isEmpty()) {
					PLAYER_CATEGORIES.remove(player.getUUID());
				}
			}
		}
	}

	/**
	 * 清除玩家的所有分类。
	 *
	 * @param player 目标玩家
	 */
	public static void clear(ServerPlayer player) {
		PLAYER_CATEGORIES.remove(player.getUUID());
	}

	/**
	 * 查询玩家是否被标记为指定分类。
	 *
	 * @param player   目标玩家
	 * @param category 分类
	 * @return true 若玩家被标记为该分类
	 */
	public static boolean has(ServerPlayer player, Category category) {
		Set<Category> set = PLAYER_CATEGORIES.get(player.getUUID());
		if (set == null) {
			return false;
		}
		synchronized (set) {
			return set.contains(category);
		}
	}

	/**
	 * 获取玩家被标记的所有分类(不可变视图)。
	 *
	 * @param player 目标玩家
	 * @return 分类集合的不可变副本,未标记时返回空集合
	 */
	public static Set<Category> get(ServerPlayer player) {
		Set<Category> set = PLAYER_CATEGORIES.get(player.getUUID());
		if (set == null) {
			return Collections.emptySet();
		}
		synchronized (set) {
			return Collections.unmodifiableSet(EnumSet.copyOf(set));
		}
	}

	/**
	 * 通用查询:任意实体是否属于指定分类。
	 *
	 * <p>对于玩家,查询其被标记的分类;对于其他实体,查询原版 {@link EntityTypeTags} 标签。</p>
	 *
	 * @param entity   实体
	 * @param category 分类
	 * @return true 若实体属于该分类
	 */
	public static boolean isCategory(Entity entity, Category category) {
		if (entity instanceof ServerPlayer player) {
			return has(player, category);
		}
		return entity.is(category.tag());
	}

	/**
	 * 便捷查询:实体是否为亡灵。
	 *
	 * @param entity 实体
	 * @return true 若为亡灵(玩家需被标记,其他实体查原版标签)
	 */
	public static boolean isUndead(Entity entity) {
		return isCategory(entity, Category.UNDEAD);
	}

	/**
	 * 便捷查询:实体是否为节肢动物。
	 *
	 * @param entity 实体
	 * @return true 若为节肢动物
	 */
	public static boolean isArthropod(Entity entity) {
		return isCategory(entity, Category.ARTHROPOD);
	}

	/**
	 * 便捷查询:实体是否为灾厄村民。
	 *
	 * @param entity 实体
	 * @return true 若为灾厄村民
	 */
	public static boolean isIllager(Entity entity) {
		return isCategory(entity, Category.ILLAGER);
	}

	/**
	 * 便捷查询:实体是否为水生生物。
	 *
	 * @param entity 实体
	 * @return true 若为水生生物
	 */
	public static boolean isAquatic(Entity entity) {
		return isCategory(entity, Category.AQUATIC);
	}

	/**
	 * 内部方法:通过 UUID 查询玩家是否被标记为指定分类(供 Mixin 使用,避免 instanceof 检查)。
	 *
	 * @param uuid     玩家 UUID
	 * @param category 分类
	 * @return true 若玩家被标记为该分类
	 */
	static boolean hasByUuid(UUID uuid, Category category) {
		Set<Category> set = PLAYER_CATEGORIES.get(uuid);
		if (set == null) {
			return false;
		}
		synchronized (set) {
			return set.contains(category);
		}
	}
}
