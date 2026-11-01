package com.dathis.rpg.modules.mob;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Выбор ванильного моба: все живые существа, у которых есть яйцо призыва. */
final class EntityTypeMenu extends Menu {

    private static final int PER_PAGE = 45;
    private static final List<EntityType> TYPES = Arrays.stream(EntityType.values())
            .filter(t -> t.isAlive() && t.isSpawnable() && t != EntityType.PLAYER && t != EntityType.ARMOR_STAND)
            .filter(t -> MobModule.eggMaterial(t) != null)
            .toList();

    private final MobModule module;
    private final MobEditorMenu editor;
    private final int page;

    EntityTypeMenu(MobModule module, MobEditorMenu editor, int page) {
        super(6, Icons.text("<dark_gray>Выбор моба"));
        this.module = module;
        this.editor = editor;
        this.page = page;
    }

    @Override
    protected void render() {
        int from = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && from + i < TYPES.size(); i++) {
            EntityType type = TYPES.get(from + i);
            set(i, Icons.of(MobModule.eggMaterial(type), "<white>" + type.name().toLowerCase(Locale.ROOT).replace('_', ' '),
                    "<green>Нажмите, чтобы выбрать"), p -> {
                editor.draft().type = type;
                editor.open(p);
            });
        }
        for (int i = PER_PAGE; i < size(); i++) {
            set(i, Icons.filler());
        }
        if (page > 0) {
            set(45, Icons.of(Material.ARROW, "<yellow>Назад"), p -> new EntityTypeMenu(module, editor, page - 1).open(p));
        }
        set(49, Icons.of(Material.BARRIER, "<red>К редактору"), editor::open);
        if (from + PER_PAGE < TYPES.size()) {
            set(53, Icons.of(Material.ARROW, "<yellow>Вперёд"), p -> new EntityTypeMenu(module, editor, page + 1).open(p));
        }
    }
}
