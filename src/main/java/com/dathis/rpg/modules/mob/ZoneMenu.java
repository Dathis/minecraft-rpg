package com.dathis.rpg.modules.mob;

import com.dathis.rpg.core.selection.SelectionWand;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import com.dathis.rpg.core.input.Numbers;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.OptionalDouble;

/** Настройка одной зоны спавна. */
final class ZoneMenu extends Menu {

    private final MobModule module;
    private final ZonesMenu back;
    private final SpawnZone zone;
    private boolean confirmDelete;

    ZoneMenu(MobModule module, ZonesMenu back, SpawnZone zone) {
        super(3, Icons.text("<dark_gray>Зона " + zone.id));
        this.module = module;
        this.back = back;
        this.zone = zone;
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        set(4, Icons.of(Material.GRASS_BLOCK, "<green>" + zone.id,
                "<gray>Где: <white>" + zone.coordsText(),
                "<gray>Размер: <white>" + zone.sizeText(),
                "<gray>Живых мобов: <white>" + module.zones().aliveCount(zone) + " / " + zone.maxAlive));

        set(10, Icons.of(Material.ZOMBIE_HEAD, "<yellow>Сколько мобов",
                        "<gray>Сейчас: <white>" + zone.maxAlive,
                        "", "<green>Нажмите и напишите число (1-50)"),
                p -> ask(p, "<yellow>Сколько мобов держать в зоне (1-50)?", text -> {
                    OptionalDouble value = Numbers.parse(text);
                    if (value.isPresent() && value.getAsDouble() >= 1 && value.getAsDouble() <= 50) {
                        zone.maxAlive = (int) value.getAsDouble();
                        module.zones().trim(zone);
                        module.zones().save();
                    } else {
                        module.plugin().messages().send(p, "<red>Нужно число от 1 до 50.</red>");
                    }
                }));
        set(11, Icons.of(Material.CLOCK, "<yellow>Время респавна",
                        "<gray>Сейчас: <white>" + time(zone.respawnSeconds),
                        "<gray>Через сколько появится убитый моб",
                        "", "<green>Нажмите и напишите секунды",
                        "<gray>Можно с буквой: <white>90</white>, <white>5m</white>, <white>1h"),
                p -> ask(p, "<yellow>Время респавна: секунды или с буквой, например <white>30</white>, <white>5m</white>, <white>1h</white>:", text -> {
                    int seconds = parseTime(text);
                    if (seconds >= 1 && seconds <= 86400) {
                        zone.respawnSeconds = seconds;
                        module.zones().save();
                    } else {
                        module.plugin().messages().send(p, "<red>Нужно время от 1 секунды до 24 часов.</red>");
                    }
                }));
        set(12, Icons.of(Material.SPYGLASS, "<yellow>Показать границы",
                "<gray>Подсветит зону на 10 секунд"), this::showBorders);
        set(13, Icons.of(Material.ENDER_PEARL, "<yellow>Телепорт в зону"), p -> {
            Location center = zone.center();
            if (center != null) {
                p.closeInventory();
                p.teleport(center.add(0, 1, 0));
            }
        });
        set(14, Icons.of(Material.BLAZE_ROD, "<yellow>Переместить на выделение",
                "<gray>Зона займёт текущую область,",
                "<gray>выделенную палочкой"), p -> {
            SelectionWand.Selection selection = module.plugin().selectionWand().selection(p);
            if (selection == null || !selection.complete()) {
                module.plugin().messages().send(p, "<red>Сначала выделите новую область палочкой.</red>");
                return;
            }
            module.zones().killMobs(zone);
            zone.setArea(selection.first, selection.second);
            module.zones().save();
            module.plugin().messages().send(p, "<green>Зона перемещена.");
            redraw();
        });
        set(15, Icons.of(Material.IRON_SWORD, "<yellow>Убрать мобов зоны",
                "<gray>Удалит живых мобов этой зоны,",
                "<gray>зона сразу заполнится заново"), p -> {
            int removed = module.zones().killMobs(zone);
            module.plugin().messages().send(p, "<yellow>Убрано мобов: " + removed);
            redraw();
        });
        set(16, Icons.of(confirmDelete ? Material.TNT : Material.BARRIER,
                confirmDelete ? "<red><bold>Точно удалить зону?" : "<red>Удалить зону",
                confirmDelete ? "<gray>Нажмите ещё раз для подтверждения" : "<gray>Мобы этой зоны тоже исчезнут"), p -> {
            if (!confirmDelete) {
                confirmDelete = true;
                redraw();
                return;
            }
            module.zones().delete(zone);
            module.plugin().messages().send(p, "<yellow>Зона " + zone.id + " удалена.");
            back.open(p);
        });
        set(18, Icons.of(Material.ARROW, "<gray>К списку зон"), back::open);
    }

    private void showBorders(Player player) {
        player.closeInventory();
        Location a = new Location(zone.bukkitWorld(), zone.minX, zone.minY, zone.minZ);
        Location b = new Location(zone.bukkitWorld(), zone.maxX, zone.maxY, zone.maxZ);
        if (a.getWorld() == null) {
            return;
        }
        new BukkitRunnable() {
            private int times;

            @Override
            public void run() {
                if (!player.isOnline() || ++times > 20) {
                    cancel();
                    return;
                }
                SelectionWand.outline(player, a, b, Particle.FLAME);
            }
        }.runTaskTimer(module.plugin(), 0L, 10L);
    }

    private void ask(Player player, String prompt, java.util.function.Consumer<String> apply) {
        module.plugin().chatInput().ask(player, prompt, text -> {
            apply.accept(text);
            open(player);
        }, () -> open(player));
    }

    static int parseTime(String text) {
        return Numbers.parseTime(text);
    }

    static String time(int seconds) {
        return Numbers.formatTime(seconds);
    }
}
