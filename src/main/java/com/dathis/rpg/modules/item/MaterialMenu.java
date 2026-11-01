package com.dathis.rpg.modules.item;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Выбор ванильного предмета: по страницам, с поиском по английскому названию. */
final class MaterialMenu extends Menu {

    private static final int PER_PAGE = 45;

    private final ItemModule module;
    private final EditorMenu editor;
    private final boolean weaponsOnly;
    private final int page;
    private final String search;
    private final List<Material> materials;

    MaterialMenu(ItemModule module, EditorMenu editor, boolean weaponsOnly, int page, String search) {
        super(6, Icons.text("<dark_gray>Выбор предмета"));
        this.module = module;
        this.editor = editor;
        this.weaponsOnly = weaponsOnly;
        this.page = page;
        this.search = search;
        this.materials = Arrays.stream(Material.values())
                .filter(m -> m.isItem() && !m.isAir() && !m.name().startsWith("LEGACY_"))
                .filter(m -> !weaponsOnly || isWeapon(m))
                .filter(m -> search == null || m.name().contains(search.toUpperCase(Locale.ROOT).replace(' ', '_')))
                .toList();
    }

    @Override
    protected void render() {
        int from = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && from + i < materials.size(); i++) {
            Material material = materials.get(from + i);
            set(i, Icons.of(material, "<white>" + EditorMenu.pretty(material), "<green>Нажмите, чтобы выбрать"), p -> {
                editor.draft().material = material;
                editor.open(p);
            });
        }
        for (int i = PER_PAGE; i < size(); i++) {
            set(i, Icons.filler());
        }
        int pages = Math.max(1, (materials.size() + PER_PAGE - 1) / PER_PAGE);
        if (page > 0) {
            set(45, Icons.of(Material.ARROW, "<yellow>Назад", "<gray>Страница " + page + " из " + pages),
                    p -> new MaterialMenu(module, editor, weaponsOnly, page - 1, search).open(p));
        }
        set(47, Icons.of(Material.COMPASS, "<yellow>Поиск",
                        search == null ? "<gray>По английскому названию" : "<gray>Сейчас: <white>" + search,
                        "<gray>Например: <white>sword</white>, <white>axe</white>, <white>diamond"),
                p -> module.plugin().chatInput().ask(p, "<yellow>Что ищем? (по-английски, например <white>sword</white>)",
                        text -> new MaterialMenu(module, editor, weaponsOnly, 0, text).open(p),
                        () -> open(p)));
        set(49, Icons.of(Material.BARRIER, "<red>К редактору"), editor::open);
        if (editor.draft().isWeapon()) {
            set(51, Icons.of(weaponsOnly ? Material.CHEST : Material.IRON_SWORD,
                            weaponsOnly ? "<yellow>Показать все предметы" : "<yellow>Только оружие"),
                    p -> new MaterialMenu(module, editor, !weaponsOnly, 0, search).open(p));
        }
        if (search != null) {
            set(52, Icons.of(Material.BUCKET, "<yellow>Сбросить поиск"),
                    p -> new MaterialMenu(module, editor, weaponsOnly, 0, null).open(p));
        }
        if (page + 1 < pages) {
            set(53, Icons.of(Material.ARROW, "<yellow>Вперёд", "<gray>Страница " + (page + 2) + " из " + pages),
                    p -> new MaterialMenu(module, editor, weaponsOnly, page + 1, search).open(p));
        }
    }

    static boolean isWeapon(Material m) {
        String n = m.name();
        return n.endsWith("_SWORD") || n.endsWith("_AXE") || m == Material.TRIDENT || m == Material.MACE;
    }
}
