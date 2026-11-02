package com.dathis.rpg.modules.region;

import com.dathis.rpg.core.gui.Icons;
import com.dathis.rpg.core.gui.Menu;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Один участок. Владелец (и админ) добавляет и убирает помощников и может отказаться от участка.
 * Помощник видит участок, но ничего не меняет.
 */
final class PlotMenu extends Menu {

    private static final int MEMBERS_FROM = 18;
    private static final int MEMBERS_TO = 26;

    private final RegionModule module;
    private final Plot plot;
    private final Player viewer;
    private final Consumer<Player> back;
    private boolean confirmDelete;
    private UUID confirmRemove;

    PlotMenu(RegionModule module, Plot plot, Player viewer, Consumer<Player> back) {
        super(4, Icons.text("<dark_gray>Участок " + plot.ownerName));
        this.module = module;
        this.plot = plot;
        this.viewer = viewer;
        this.back = back;
    }

    private boolean canManage(Player player) {
        return plot.isOwner(player) || player.hasPermission("rpg.admin");
    }

    @Override
    protected void render() {
        for (int i = 0; i < size(); i++) {
            set(i, Icons.filler());
        }
        Area land = module.areas().get(plot.land);
        set(4, Icons.of(Material.OAK_DOOR, "<gold>Участок " + plot.ownerName,
                "<gray>Земля: <white>" + (land == null ? plot.land : land.name),
                "<gray>Размер: <white>" + plot.size.title + " " + plot.sizeText() + "<gray>, вся высота",
                "<gray>Где: <white>" + plot.world + " " + plot.coordsText(),
                "<gray>Помощников: <white>" + plot.members.size()));

        set(10, Icons.of(Material.SPYGLASS, "<yellow>Показать границы",
                "<gray>Подсветит участок на 10 секунд"), p -> {
            p.closeInventory();
            PlotClaimMenu.showBorders(module, p, plot.world, plot.minX, plot.minZ, plot.maxX, plot.maxZ);
        });
        boolean admin = viewer.hasPermission("rpg.admin");
        if (module.plotTeleport() || admin) {
            set(11, Icons.of(Material.ENDER_PEARL, "<yellow>Телепорт на участок"), p -> {
                Location center = plot.center();
                if (center != null) {
                    p.closeInventory();
                    p.teleport(center);
                }
            });
        }
        Player player = viewer;
        boolean manage = canManage(player);
        if (manage) {
            int max = module.maxPlotMembers();
            set(13, Icons.of(Material.PLAYER_HEAD, "<green>Добавить помощника",
                            "<gray>Помощник тоже сможет строить здесь.",
                            "<gray>Помощников: <white>" + plot.members.size() + "/" + max,
                            "", "<green>Нажмите и напишите ник в чат"),
                    p -> module.plugin().chatInput().ask(p, "<yellow>Ник игрока, которому разрешить строить:", text -> {
                        addMember(p, text.trim());
                        open(p);
                    }, () -> open(p)));
            boolean own = plot.isOwner(player);
            set(16, Icons.of(confirmDelete ? Material.TNT : Material.BARRIER,
                    confirmDelete ? "<red><bold>Точно?" : (own ? "<red>Отказаться от участка" : "<red>Удалить участок"),
                    confirmDelete ? "<gray>Нажмите ещё раз для подтверждения"
                            : (own && module.refundDeed() ? "<gray>Место освободится, бумага вернётся вам"
                            : "<gray>Место освободится, постройки останутся")), p -> {
                if (!confirmDelete) {
                    confirmDelete = true;
                    redraw();
                    return;
                }
                release(p, own);
            });
        }

        int slot = MEMBERS_FROM;
        for (Map.Entry<UUID, String> member : new ArrayList<>(plot.members.entrySet())) {
            if (slot > MEMBERS_TO) {
                break;
            }
            UUID uuid = member.getKey();
            boolean confirm = uuid.equals(confirmRemove);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Может строить на участке");
            if (manage) {
                lore.add("");
                lore.add(confirm ? "<red>Нажмите ещё раз, чтобы убрать" : "<yellow>Нажмите, чтобы убрать");
            }
            if (manage) {
                set(slot++, Icons.of(confirm ? Material.TNT : Material.PLAYER_HEAD,
                        "<white>" + member.getValue(), lore), p -> {
                    if (!uuid.equals(confirmRemove)) {
                        confirmRemove = uuid;
                        redraw();
                        return;
                    }
                    plot.members.remove(uuid);
                    module.plots().save();
                    confirmRemove = null;
                    module.plugin().messages().send(p, "<yellow>" + member.getValue() + " больше не может строить здесь.");
                    redraw();
                });
            } else {
                set(slot++, Icons.of(Material.PLAYER_HEAD, "<white>" + member.getValue(), lore));
            }
        }

        set(27, Icons.of(Material.ARROW, "<gray>Назад"), back::accept);
        set(35, Icons.of(Material.BOOK, "<yellow>Как это работает",
                "<gray>На участке строят, ломают и открывают",
                "<gray>сундуки только владелец и помощники.",
                "<gray>Остальным здесь ничего нельзя.",
                "<gray>Ряд с головами - помощники."));
    }

    private void addMember(Player player, String name) {
        var messages = module.plugin().messages();
        OfflinePlayer target = Bukkit.getPlayerExact(name);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(name);
        }
        if (target == null || target.getName() == null) {
            messages.send(player, "<red>Игрок " + name + " не найден. Он должен хотя бы раз зайти на сервер.</red>");
            return;
        }
        if (target.getUniqueId().equals(plot.owner)) {
            messages.send(player, "<red>Это владелец участка.</red>");
            return;
        }
        if (plot.members.containsKey(target.getUniqueId())) {
            messages.send(player, "<yellow>" + target.getName() + " уже помощник.");
            return;
        }
        if (plot.members.size() >= module.maxPlotMembers()) {
            messages.send(player, "<red>Уже максимум помощников: " + module.maxPlotMembers() + ".</red>");
            return;
        }
        plot.members.put(target.getUniqueId(), target.getName());
        module.plots().save();
        messages.send(player, "<green>" + target.getName() + " теперь может строить на участке.");
        if (target instanceof Player online && online.isOnline()) {
            module.plugin().messages().send(online, "<green>" + plot.ownerName + " разрешил вам строить на своём участке. <white>/plot");
        }
    }

    private void release(Player player, boolean own) {
        module.plots().delete(plot);
        Area land = module.areas().get(plot.land);
        if (own && module.refundDeed() && land != null) {
            for (var left : player.getInventory().addItem(module.deeds().create(land, plot.size)).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
            module.plugin().messages().send(player, "<yellow>Вы отказались от участка. Бумага вернулась к вам.");
        } else {
            module.plugin().messages().send(player, "<yellow>Участок " + plot.id + " удалён, место свободно.");
        }
        back.accept(player);
    }
}
