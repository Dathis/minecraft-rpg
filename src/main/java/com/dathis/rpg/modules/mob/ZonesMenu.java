package com.dathis.rpg.modules.mob;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Зоны спавна одного моба: список, создание из выделения, палочка. */
final class ZonesMenu extends Menu {

    private static final int MAX_SHOWN = 45;

    private final MobModule module;
    private final MobEditorMenu editor;
    private final String mobId;

    ZonesMenu(MobModule module, MobEditorMenu editor) {
        super(6, Icons.text("<dark_gray>Зоны спавна моба"));
        this.module = module;
        this.editor = editor;
        this.mobId = editor.draft().id;
    }

    @Override
    protected void render() {
        List<SpawnZone> zones = module.zones().forMob(mobId);
        for (int i = 0; i < MAX_SHOWN && i < zones.size(); i++) {
            SpawnZone zone = zones.get(i);
            set(i, Icons.of(Material.GRASS_BLOCK, "<green>" + zone.id,
                            "<gray>Где: <white>" + zone.coordsText(),
                            "<gray>Размер: <white>" + zone.sizeText(),
                            "<gray>Мобов: <white>" + module.zones().aliveCount(zone) + " / " + zone.maxAlive,
                            "<gray>Респавн: <white>" + ZoneMenu.time(zone.respawnSeconds),
                            "", "<green>Нажмите, чтобы настроить"),
                    p -> new ZoneMenu(module, this, zone).open(p));
        }
        for (int i = MAX_SHOWN; i < size(); i++) {
            set(i, Icons.filler());
        }
        set(45, Icons.of(Material.ARROW, "<gray>К редактору моба"), editor::open);
        set(47, Icons.of(Material.BLAZE_ROD, "<gold>Получить палочку выделения",
                        "<gray>Удар по блоку — первый угол",
                        "<gray>ПКМ по блоку — второй угол"),
                p -> {
                    p.getInventory().addItem(module.wand().create());
                    module.plugin().messages().send(p, "<green>Палочка выдана. Выделите область и вернитесь сюда.");
                    p.closeInventory();
                });
        set(49, Icons.of(Material.LIME_CONCRETE, "<green>Создать зону из выделения",
                        "<gray>Новая зона: 3 моба, респавн 60 сек.",
                        "<gray>Всё можно поменять после создания"),
                this::create);
        set(51, Icons.of(Material.BOOK, "<yellow>Как это работает",
                "<gray>В зоне всегда живёт заданное число",
                "<gray>этих мобов. Убитый появится снова",
                "<gray>через время респавна в случайном",
                "<gray>месте зоны. Зона работает, когда",
                "<gray>рядом есть игроки."));
    }

    private void create(Player player) {
        SelectionWand.Selection selection = module.wand().selection(player);
        if (selection == null || !selection.complete()) {
            module.plugin().messages().send(player,
                    "<red>Сначала выделите область палочкой: удар по блоку и ПКМ по другому блоку.</red>");
            return;
        }
        SpawnZone zone = module.zones().create(mobId, selection.first, selection.second);
        module.plugin().messages().send(player, "<green>Зона " + zone.id + " создана. Мобы начнут появляться.");
        new ZoneMenu(module, this, zone).open(player);
    }
}
