# Архитектура MinecraftRPG

## Общая схема

```
RpgPlugin (точка входа)
├── Messages        сообщения MiniMessage с префиксом
├── PlatformService Java или Bedrock (через Floodgate, если он есть)
├── YamlStore       файлы data/<имя>.yml
├── ChatInput       ввод текста через чат
├── MenuListener    клики по меню-сундукам
├── ModuleManager   включает модули из config.yml
└── /rpg            help, modules, reload
```

Пакет `com.dathis.rpg`:

| Пакет | Что внутри |
|---|---|
| `core/module` | `RpgModule`, `AbstractModule` (слушатели снимаются при выключении), `ModuleManager` |
| `core/gui` | `Menu` (окно-сундук с кнопками и слотами-приёмниками), `MenuListener`, `Icons` (иконки кнопок) |
| `core/input` | `ChatInput` (следующее сообщение игрока идёт в обработчик), `Numbers` (разбор `15`, `15%`, `10-20`) |
| `core/storage` | `YamlStore`: по файлу на модуль |
| `core/platform` | `PlatformService.isBedrock(player)` |
| `core/config` | `Messages` |
| `modules/item` | кастомные предметы, оружие, броня, `/create-item` |
| `modules/mob` | кастомные мобы, зоны спавна, запрет естественного спавна, `/create-mob` |
| `modules/hud` | цифры урона, полоска ХП цели |
| `modules/region`, `tree`, `npc`, `dialog`, `quest`, `shop` | пока пустые заготовки |

## Модули

Каждый модуль наследует `AbstractModule`, имеет `id` (ключ в `config.yml` → `modules.<id>`) и включается в
`RpgPlugin.onEnable` в порядке регистрации. `/rpg reload` выключает все модули, перечитывает конфиг и включает заново.

Модуль регистрирует слушателей через `listen(...)`, команды через `plugin.getCommand(...)`, задачи хранит сам
и отменяет в `onDisable`.

## Меню (`core/gui/Menu`)

- `render()` заполняет окно, `open(player)` открывает, `redraw()` перерисовывает без закрытия.
- `set(slot, icon, player -> ...)` обычная кнопка, реагирует на любой клик.
- `setInput(slot, icon, (player, cursor) -> ...)` слот-приёмник: игрок кладёт предмет с курсора, обработчик получает
  копию (`cursor == null`, если курсор пуст). Предмет у игрока остаётся.
- `quickAdd(player, item)` вызывается при shift-клике по предмету в инвентаре игрока.
- `MenuListener` отменяет все клики в окне, разрешает обычные клики в инвентаре игрока (чтобы взять предмет на курсор)
  и переносит действия на следующий тик (открывать окна прямо в событии клика нельзя).
- Перетаскивание на один слот окна считается кликом.

Типичный редактор: черновик шаблона (`CustomItem`/`CustomMob`) живёт в объекте меню, ввод через `ChatInput`
открывает меню снова, кнопка «Сохранить» пишет в реестр.

## Предметы (`modules/item`)

- `CustomItem` шаблон: `Kind` = `ITEM` / `WEAPON` / `ARMOR`, материал, название, цвет названия, описание;
  оружие: `damageMin/Max`, `critChance`, `vampirism`; броня: `armor`, `bonusHealth`, `regeneration`.
- `ItemRegistry` хранит шаблоны в `data/items.yml`, id вида `item1`; `of(ItemStack)` читает id из PDC (`item_id`).
- `ItemFactory.build` делает ItemStack: название, описание, строки статов, PDC id. У оружия копируются ванильные
  модификаторы и скрываются атрибуты. У брони ставится нулевой модификатор `armor_base`, чтобы отключить ванильную
  защиту материала, и скрываются атрибуты.
- `CombatListener` (оружие): `EntityDamageByEntityEvent` HIGH, урон = случайный в диапазоне × заряд атаки
  (как в ванилле), крит × `items.crit-multiplier`, вампиризм лечит % от нанесённого урона. При крите ставит
  метаданные `HudModule.CRIT_METADATA` на цель.
- `ArmorListener` (броня): раз в секунду суммирует статы надетых кастомных вещей и ставит игроку временные
  модификаторы `custom_armor` (ARMOR, максимум 30 у Minecraft) и `custom_health` (MAX_HEALTH). Регенерация каждую
  секунду, пауза 5 с после урона. При максимуме ХП больше `items.health-scale` включает сжатие сердец.

## Мобы (`modules/mob`)

- `CustomMob` шаблон: `EntityType`, название, `Tier` (NORMAL белое / ELITE красное / BOSS красное жирное), ХП (≤1024),
  урон от-до, снаряжение по слотам, лут (`MobDrop`: ItemStack в base64 + шанс), ванильный лут вкл/выкл.
  `data/mobs.yml`, id `mob1`.
- `MobModule.spawn` спавнит через `world.spawn(loc, class, randomizeData=false, ...)`: без случайной брони,
  детёнышей и жокеев. Ставит PDC `mob_id`, название, ХП, снаряжение (шанс выпадения 0), `removeWhenFarAway=false`.
- `MobListener`: урон моба и его снарядов, лут при смерти, использование яйца призыва (своё яйцо не работает
  по спавнеру, мобу и из раздатчика).
- `NaturalSpawnBlocker`: отменяет `CreatureSpawnEvent` с причинами NATURAL, JOCKEY, PATROL, REINFORCEMENTS, TRAP
  для `Enemy` (и для `Animals`, если включено). Конфиг `spawning.*`.
- Зоны спавна: `SpawnZone` (`data/zones.yml`, id `zone1`), `ZoneManager` раз в секунду для каждой зоны, рядом с
  которой есть игрок (64 блока), считает своих мобов (PDC `zone_id`, в радиусе зоны + 48) и досоздаёт до `maxAlive`.
  Убитые ждут `respawnSeconds` (таймеры в памяти, после рестарта зона просто заполняется). Место спавна: случайный
  твёрдый блок в зоне с двумя свободными над ним.
- `SelectionWand`: огненный стержень с PDC `selection_wand`, удар — первый угол, ПКМ — второй, частицы границ
  каждые 10 тиков, пока стержень в руке.

## HUD (`modules/hud`)

- `DamageIndicators`: на MONITOR урона от игрока (или его снаряда) спавнит невидимую маленькую стойку-маркер с именем
  `-20` (крит: золотой, жирный, `!`), поднимает её 20 тиков и удаляет. Стойки непостоянные, помечены PDC и
  удаляются при загрузке чанка на случай падения сервера.
- `TargetHealthBar`: каждые 2 тика для игрока ищет `getTargetEntity(range)` или цель последнего удара (3 с) и
  показывает Adventure BossBar с названием и `HP x / y`, цвет по проценту.

## Хранение

`plugins/MinecraftRPG/`:
- `config.yml` настройки (новые ключи читаются с дефолтами, старые конфиги не ломаются);
- `data/items.yml`, `data/mobs.yml`, `data/zones.yml`.

## Совместимость с Bedrock

См. правила в `CLAUDE.md`. Коротко: только сундуки, чат, боссбары, обычные сущности, один обычный клик на кнопку,
ввод через чат, без Display-сущностей и ресурспаков.
