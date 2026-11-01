package com.dathis.rpg.modules.npc;

import com.dathis.rpg.RpgPlugin;
import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import com.dathis.rpg.core.module.AbstractModule;
import com.dathis.rpg.modules.mob.MobModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NPC: админ создаёт шаблон в /create-npc (тип сущности, имя, скин головы, броня и оружие, приветствие, торговля)
 * и ставит его в мир. NPC — обычные сущности сервера без ИИ, поэтому их видят и Java, и Bedrock.
 *
 * Сущности NPC не сохраняются в мир (setPersistent(false)): раз в секунду модуль проверяет точки в загруженных
 * чанках и ставит недостающих. Так после краха сервера не бывает дублей, а правка шаблона применяется пересозданием.
 */
public final class NpcModule extends AbstractModule {

    /** Какие сущности можно выбрать для NPC: человекоподобные (носят броню и оружие) и пара особых. */
    static final List<EntityType> TYPES = List.of(
            EntityType.VILLAGER, EntityType.WANDERING_TRADER, EntityType.ZOMBIE_VILLAGER,
            EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED,
            EntityType.SKELETON, EntityType.STRAY, EntityType.WITHER_SKELETON, EntityType.BOGGED,
            EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.ZOMBIFIED_PIGLIN,
            EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.EVOKER, EntityType.WITCH,
            EntityType.IRON_GOLEM, EntityType.SNOW_GOLEM, EntityType.ALLAY, EntityType.ARMOR_STAND);

    /** Связь с модулем квестов (он подключается сам, если включён). */
    public interface QuestLink {
        int questCount(String npcId);

        void openQuests(Player player, String npcId, Menu back);
    }

    private NpcRegistry registry;
    private NamespacedKey idKey;
    private NamespacedKey placementKey;
    /** id точки → сущность NPC, которая сейчас стоит в мире. */
    private final Map<String, UUID> live = new HashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private QuestLink questLink;
    private double lookRange;

    public NpcModule() {
        super("npc", "NPC");
    }

    @Override
    protected void onEnable() {
        idKey = new NamespacedKey(plugin, "npc_id");
        placementKey = new NamespacedKey(plugin, "npc_placement");
        registry = new NpcRegistry(plugin);
        registry.load();
        lookRange = plugin.getConfig().getDouble("npc.look-at-players", 8);

        // Остатки прошлого запуска (если сервер упал) — убираем, дальше поставим заново
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isNpc(entity)) {
                    entity.remove();
                }
            }
        }
        listen(new NpcListener(this));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::upkeep, 5L, 20L));
        if (lookRange > 0) {
            tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::lookAtPlayers, 7L, 5L));
        }

        PluginCommand command = plugin.getCommand("create-npc");
        if (command != null) {
            CreateNpcCommand executor = new CreateNpcCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    @Override
    protected void onDisable() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        for (String placementId : new ArrayList<>(live.keySet())) {
            despawn(placementId);
        }
        PluginCommand command = plugin.getCommand("create-npc");
        if (command != null) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
    }

    // ---------- API для других модулей ----------

    public NpcTemplate npc(String id) {
        return registry.get(id);
    }

    public List<NpcTemplate> npcs() {
        return registry.all();
    }

    public void setQuestLink(QuestLink link) {
        this.questLink = link;
    }

    QuestLink questLink() {
        return questLink;
    }

    /** Иконка NPC для меню: голова со скином или яйцо его типа. */
    public ItemStack icon(NpcTemplate npc) {
        ItemStack base;
        if (npc.hasSkin()) {
            base = npc.equipment.get(org.bukkit.inventory.EquipmentSlot.HEAD).clone();
        } else if (npc.type == EntityType.ARMOR_STAND) {
            base = new ItemStack(Material.ARMOR_STAND);
        } else {
            Material egg = MobModule.eggMaterial(npc.type);
            base = new ItemStack(egg != null ? egg : Material.VILLAGER_SPAWN_EGG);
        }
        ItemStack icon = Icons.of(base.getType(), npc.name,
                "<gray>Тип: <white>" + typeName(npc.type),
                "<gray>Сделок: <white>" + npc.trades.size()
                        + (questLink != null && npc.id != null ? " <gray>Квестов: <white>" + questLink.questCount(npc.id) : ""));
        if (base.getType() == Material.PLAYER_HEAD) {
            // переносим скин головы на иконку
            ItemStack head = base;
            head.editMeta(meta -> {
                meta.displayName(icon.getItemMeta().displayName());
                meta.lore(icon.getItemMeta().lore());
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            });
            return head;
        }
        return icon;
    }

    /** Окно торговли как у жителя (его Geyser показывает и на Bedrock). */
    public void openTrade(Player player, NpcTemplate npc) {
        if (!npc.hasTrades()) {
            return;
        }
        @SuppressWarnings("deprecation")
        Merchant merchant = Bukkit.createMerchant(npc.displayName());
        List<MerchantRecipe> recipes = new ArrayList<>();
        for (NpcTrade trade : npc.trades) {
            if (!trade.complete()) {
                continue;
            }
            MerchantRecipe recipe = new MerchantRecipe(trade.result.clone(), 0, Integer.MAX_VALUE, false);
            recipe.addIngredient(trade.cost1.clone());
            if (trade.cost2 != null) {
                recipe.addIngredient(trade.cost2.clone());
            }
            recipes.add(recipe);
        }
        merchant.setRecipes(recipes);
        player.openMerchant(merchant, true);
    }

    // ---------- внутреннее ----------

    RpgPlugin plugin() {
        return plugin;
    }

    NpcRegistry registry() {
        return registry;
    }

    static String typeName(EntityType type) {
        return "<lang:" + type.translationKey() + ">";
    }

    boolean isNpc(Entity entity) {
        return entity.getPersistentDataContainer().has(idKey, PersistentDataType.STRING);
    }

    NpcTemplate templateOf(Entity entity) {
        return registry.get(entity.getPersistentDataContainer().get(idKey, PersistentDataType.STRING));
    }

    NpcPlacement placementOf(Entity entity) {
        return registry.placement(entity.getPersistentDataContainer().get(placementKey, PersistentDataType.STRING));
    }

    /** Живая ли эта сущность — та, что сейчас числится за точкой (а не забытая копия). */
    boolean isCurrent(Entity entity) {
        String placementId = entity.getPersistentDataContainer().get(placementKey, PersistentDataType.STRING);
        return placementId != null && entity.getUniqueId().equals(live.get(placementId));
    }

    NpcPlacement place(NpcTemplate npc, Location location) {
        NpcPlacement placement = registry.place(npc.id, location);
        spawn(placement);
        return placement;
    }

    void removePlacement(NpcPlacement placement) {
        despawn(placement.id);
        registry.removePlacement(placement.id);
    }

    void movePlacement(NpcPlacement placement, Location location) {
        placement.moveTo(location);
        registry.writeFile();
        despawn(placement.id);
        spawn(placement);
    }

    /** Пересоздать всех NPC шаблона (после сохранения изменений). */
    void respawnAll(String npcId) {
        for (NpcPlacement placement : registry.placementsOf(npcId)) {
            despawn(placement.id);
            spawn(placement);
        }
    }

    void deleteTemplate(String npcId) {
        for (NpcPlacement placement : registry.placementsOf(npcId)) {
            despawn(placement.id);
        }
        registry.delete(npcId);
    }

    private void despawn(String placementId) {
        UUID uuid = live.remove(placementId);
        Entity entity = uuid == null ? null : Bukkit.getEntity(uuid);
        if (entity != null) {
            entity.remove();
        }
    }

    /** Раз в секунду: ставим NPC на точки в загруженных чанках, если их там нет. */
    private void upkeep() {
        for (NpcPlacement placement : registry.placements()) {
            UUID uuid = live.get(placement.id);
            Entity entity = uuid == null ? null : Bukkit.getEntity(uuid);
            if (entity != null && entity.isValid()) {
                continue;
            }
            live.remove(placement.id);
            World world = Bukkit.getWorld(placement.world);
            if (world == null || !world.isChunkLoaded((int) Math.floor(placement.x) >> 4, (int) Math.floor(placement.z) >> 4)) {
                continue;
            }
            spawn(placement);
        }
    }

    private void spawn(NpcPlacement placement) {
        NpcTemplate npc = registry.get(placement.npcId);
        Location location = placement.location();
        if (npc == null || location == null || !location.isChunkLoaded()) {
            return;
        }
        Class<? extends Entity> type = npc.type.getEntityClass();
        if (type == null) {
            return;
        }
        Entity entity = location.getWorld().spawn(location, type, false, e -> apply(npc, placement, e));
        live.put(placement.id, entity.getUniqueId());
    }

    private void apply(NpcTemplate npc, NpcPlacement placement, Entity entity) {
        entity.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, npc.id);
        entity.getPersistentDataContainer().set(placementKey, PersistentDataType.STRING, placement.id);
        entity.customName(npc.displayName());
        entity.setCustomNameVisible(true);
        entity.setPersistent(false);   // в мир не сохраняется, ставим сами
        entity.setInvulnerable(true);
        entity.setSilent(true);
        if (entity instanceof Ageable ageable) {
            ageable.setAdult();
        }
        if (entity instanceof LivingEntity living) {
            living.setRemoveWhenFarAway(false);
            living.setCollidable(false);
            EntityEquipment equipment = living.getEquipment();
            if (equipment != null) {
                equipment.clear();
                npc.equipment.forEach((slot, item) -> {
                    equipment.setItem(slot, item.clone());
                    if (living instanceof Mob) {
                        equipment.setDropChance(slot, 0f);
                    }
                });
            }
        }
        if (entity instanceof Mob mob) {
            mob.setAI(false);
        }
        if (entity instanceof Zombie zombie) {
            zombie.setShouldBurnInDay(false);
        }
        if (entity instanceof AbstractSkeleton skeleton) {
            skeleton.setShouldBurnInDay(false);
        }
        if (entity instanceof PiglinAbstract piglin) {
            piglin.setImmuneToZombification(true);
        }
        if (entity instanceof Raider raider) {
            raider.setPatrolLeader(false);
            raider.setCanJoinRaid(false);
        }
        if (entity instanceof WanderingTrader trader) {
            trader.setDespawnDelay(0);
        }
        if (entity instanceof ArmorStand stand) {
            stand.setArms(true);
            stand.setBasePlate(false);
        }
        Villager.Profession profession = profession(npc.profession);
        Villager.Type villagerType = villagerType(npc.villagerType);
        if (entity instanceof Villager villager) {
            if (profession != null) {
                villager.setProfession(profession);
            }
            if (villagerType != null) {
                villager.setVillagerType(villagerType);
            }
        }
        if (entity instanceof ZombieVillager zombieVillager) {
            if (profession != null) {
                zombieVillager.setVillagerProfession(profession);
            }
            if (villagerType != null) {
                zombieVillager.setVillagerType(villagerType);
            }
        }
    }

    @SuppressWarnings("deprecation")
    static Villager.Profession profession(String key) {
        NamespacedKey k = NamespacedKey.fromString(key);
        return k == null ? null : Registry.VILLAGER_PROFESSION.get(k);
    }

    @SuppressWarnings("deprecation")
    static Villager.Type villagerType(String key) {
        NamespacedKey k = NamespacedKey.fromString(key);
        return k == null ? null : Registry.VILLAGER_TYPE.get(k);
    }

    /** NPC поворачиваются к ближайшему игроку. */
    private void lookAtPlayers() {
        for (Map.Entry<String, UUID> entry : live.entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getValue());
            if (entity == null || !entity.isValid()) {
                continue;
            }
            Location eye = entity instanceof LivingEntity living ? living.getEyeLocation() : entity.getLocation();
            Player nearest = null;
            double best = lookRange * lookRange;
            for (Player player : entity.getWorld().getNearbyPlayers(eye, lookRange)) {
                double d = player.getLocation().distanceSquared(eye);
                if (d <= best) {
                    best = d;
                    nearest = player;
                }
            }
            if (nearest == null) {
                NpcPlacement placement = registry.placement(entry.getKey());
                if (placement != null && Math.abs(entity.getYaw() - placement.yaw) > 1) {
                    entity.setRotation(placement.yaw, 0);
                }
                continue;
            }
            Vector d = nearest.getEyeLocation().toVector().subtract(eye.toVector());
            float yaw = (float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
            float pitch = (float) Math.toDegrees(-Math.atan2(d.getY(), Math.sqrt(d.getX() * d.getX() + d.getZ() * d.getZ())));
            entity.setRotation(yaw, pitch);
        }
    }

    /** Ближайшая к игроку точка NPC в радиусе, или null. */
    NpcPlacement nearestPlacement(Player player, double radius) {
        NpcPlacement best = null;
        double bestDistance = radius * radius;
        for (NpcPlacement placement : registry.placements()) {
            Location location = placement.location();
            if (location == null || !location.getWorld().equals(player.getWorld())) {
                continue;
            }
            double d = location.distanceSquared(player.getLocation());
            if (d <= bestDistance) {
                bestDistance = d;
                best = placement;
            }
        }
        return best;
    }
}
