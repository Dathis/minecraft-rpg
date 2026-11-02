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
| `core/gui` | `Menu` (окно-сундук с кнопками и слотами-приёмниками), `MenuListener`, `Icons` (иконки, `wrap` переносит текст), `ChoiceMenu` (выбор из списка со страницами), `EquipmentMenu` (снаряжение мобов и NPC) |
| `core/selection` | `SelectionWand`: палочка выделения областей (зоны спавна, «Разведать»), принадлежит `RpgPlugin` |
| `core/input` | `ChatInput` (следующее сообщение игрока идёт в обработчик), `Numbers` (разбор `15`, `15%`, `10-20`) |
| `core/storage` | `YamlStore`: по файлу на модуль; `ItemCodec`: ItemStack ↔ base64 |
| `core/combat` | `HealthService`: своё ХП и броня без ванильных пределов |
| `core/platform` | `PlatformService.isBedrock(player)` |
| `core/config` | `Messages` |
| `modules/item` | кастомные предметы, оружие, броня, `/create-item` |
| `modules/mob` | кастомные мобы, зоны спавна, запрет естественного спавна, `/create-mob` |
| `modules/health` | перехват урона и лечения для своего ХП |
| `modules/hud` | цифры урона, полоска ХП цели |
| `modules/npc` | NPC, точки в мире, торговля, `/create-npc` |
| `modules/quest` | квесты, прогресс игроков, `/create-quest`, `/quests` |
| `modules/region` | защита мира, зоны строительства и добычи, возврат блоков, `/create-region` |
| `modules/tree`, `dialog` | пока пустые заготовки |

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
- `ArmorListener` (броня): раз в секунду суммирует статы надетых кастомных вещей и отдаёт броню и +ХП в
  `HealthService` как `Provider`. Регенерация каждую секунду через `HealthService.heal`, пауза 5 с после урона.
  Вампиризм тоже лечит через `HealthService`.

## Мобы (`modules/mob`)

- `CustomMob` шаблон: `EntityType`, название, `Tier` (NORMAL белое / ELITE красное / BOSS красное жирное), ХП (без предела),
  броня, урон от-до, снаряжение по слотам, лут (`MobDrop`: ItemStack в base64 + шанс), ванильный лут вкл/выкл.
  `data/mobs.yml`, id `mob1`.
- `MobModule.spawn` спавнит через `world.spawn(loc, class, randomizeData=false, ...)`: без случайной брони,
  детёнышей и жокеев. Ставит PDC `mob_id`, название, ванильное ХП (≤1024)
  и полное своё ХП (`HealthService.fill`), снаряжение (шанс выпадения 0), `removeWhenFarAway=false`.
- `MobListener`: урон моба и его снарядов, лут при смерти, использование яйца призыва (своё яйцо не работает
  по спавнеру, мобу и из раздатчика).
- `MobBrain` (поведение): при `EntityAddToWorldEvent` (спавн, загрузка чанка) снимает у моба ванильные задачи
  Paper MobGoals типа TARGET и «бродить» (stroll/wander/...), атака и оглядывание остаются. Место спавна в PDC
  `mob_home`. Каждые 10 тиков: враждебный (`CustomMob.hostile`) ищет видимого игрока в `mobs.aggro-range`, дальше
  `mobs.leash-radius` от дома — бежит домой через Pathfinder (без реакций, 15 с — телепорт). Цель ставит только он:
  `EntityTargetEvent` от ванили отменяется, удар игрока назначает цель.
- `NaturalSpawnBlocker`: отменяет `CreatureSpawnEvent` с причинами NATURAL, JOCKEY, PATROL, REINFORCEMENTS, TRAP
  для `Enemy` (и для `Animals`, если включено). Конфиг `spawning.*`.
- Зоны спавна: `SpawnZone` (`data/zones.yml`, id `zone1`), `ZoneManager` раз в секунду для каждой зоны, рядом с
  которой есть игрок (64 блока), считает своих мобов (PDC `zone_id`, в радиусе зоны + 48) и досоздаёт до `maxAlive`.
  Убитые ждут `respawnSeconds` (таймеры в памяти, после рестарта зона просто заполняется). Место спавна: случайный
  твёрдый блок в зоне с двумя свободными над ним.
- `SelectionWand` (теперь `core/selection`): огненный стержень с PDC `selection_wand`, удар — первый угол, ПКМ — второй, частицы границ
  каждые 10 тиков, пока стержень в руке.

## Своё ХП и броня (`core/combat/HealthService`, `modules/health`)

- `HealthService` (создаёт `RpgPlugin`, доступен как `plugin.health()`): текущее ХП в PDC сущности
  (`rpg_health`, double), максимум и броня считаются вживую. У игрока: `health.player-base` + бонусы провайдеров;
  у других сущностей ХП «наше», только если какой-то `Provider` вернул для неё не null (мобы из `/create-mob`:
  ХП и броня шаблона). К броне прибавляется ванильный атрибут ARMOR. Пока модуль `health` выключен,
  методы отвечают ванильными значениями.
- Ванильное ХП — «зеркало»: `vanillaMax × наше / максимум` (не ниже 0.5, пока жив). `sync` ставит его;
  если ванильное ХП с прошлого `sync` поменял кто-то другой (команда, другой плагин), эта доля становится нашим ХП.
- `HealthListener` (`EntityDamageEvent` HIGHEST, после оружия и мобов на HIGH): урон без живого источника
  масштабируется долей от максимума, броня `armor/(armor+K)` (кэп `max-armor-reduction`) для причин из `ARMORED`,
  сопротивление −20%/уровень, доля щита (модификатор BLOCKING), поглощение. Затем наше ХП уменьшается, а ванильный
  урон подменяется (BASE = сдвиг зеркала, остальные `DamageModifier` = 0). При нуле — урон `health + 1000`, чтобы
  смерть была ванильной (сообщение, лут, тотем). MONITOR: если событие отменили — ХП возвращается; иначе `sync`
  на следующем тике. `HealthService.dealt(event)` — наш урон для цифр урона.
- `EntityRegainHealthEvent` отменяется и пересчитывается в долю нашего максимума. Тотем, возрождение, вход игрока
  (снимает сжатие сердец и модификаторы старой версии) обрабатываются отдельно. Игроки синхронизируются раз в 10 тиков.

## HUD (`modules/hud`)

- `DamageIndicators`: на MONITOR урона от игрока (или его снаряда) спавнит невидимую маленькую стойку-маркер с именем
  `-20` (крит: золотой, жирный, `!`), поднимает её 20 тиков и удаляет. Стойки непостоянные, помечены PDC и
  удаляются при загрузке чанка на случай падения сервера.
- `TargetHealthBar`: каждые 2 тика для игрока ищет `getTargetEntity(range)` или цель последнего удара (3 с) и
  показывает Adventure BossBar с названием и `HP x / y` (из `HealthService`), цвет по проценту.

## NPC (`modules/npc`)

- `NpcTemplate` шаблон (публичные `id()`, `name()`, `displayName()`, `hasTrades()`): тип, имя (MiniMessage),
  приветствие, профессия/местность жителя, снаряжение (скин = `PLAYER_HEAD` в слоте HEAD, `NpcSkins`), сделки `NpcTrade`.
  `NpcPlacement` — точка в мире. Всё в `data/npcs.yml` (`npcs.<id>`, `placements.<id>`).
- `NpcModule` ставит сущности: `setPersistent(false)` (не сохраняются в чанк), без ИИ, бессмертные, тихие.
  Раз в секунду `upkeep` ставит недостающих NPC на точки в загруженных чанках (`live`: точка → UUID).
  Раз в 5 тиков поворачивает их к ближайшему игроку. Сохранение шаблона пересоздаёт его NPC.
- `NpcListener`: клик (PlayerInteractEntity / InteractAt для стоек) → приветствие → `NpcInteractEvent`. Если событие
  не отменили, открывается торговля (`Bukkit.createMerchant`). Shift-клик админа открывает `PlacementMenu`.
  Отменяет урон, горение, превращения, цели; убирает чужие копии при загрузке чанка.
- `NpcModule.QuestLink` — крючок, через который модуль квестов показывает кнопку «Квесты» в редакторе NPC.

## Квесты (`modules/quest`)

- `Quest`: название, NPC, описание (абзацы), цели `Objective` (KILL `mob:<id>`/`type:<ENTITY>`, BRING ItemStack,
  TALK id NPC, EXPLORE область), награды (ItemStack + опыт), `requiredQuest`, `requiredLevel` (пока не проверяется).
  `data/quests.yml`, id `quest1`.
- `QuestProgress`: `data/quest-players.yml`, `players.<uuid>.active.<quest>` = счётчики целей, `completed` — список.
  Пишется на диск раз в 30 с, при выходе игрока и выключении. Прогресс «Принести» не хранится, считается по инвентарю.
- `QuestModule.status`: LOCKED / AVAILABLE / ACTIVE / READY / COMPLETED → вид книги (`book`).
- `QuestListener`: `NpcInteractEvent` (засчитывает «Поговорить», открывает `QuestBoardMenu`, если у NPC есть квесты),
  `EntityDeathEvent` (убийства). «Разведать» проверяется задачей раз в 10 тиков.
- Игроку: `QuestBoardMenu` (книги NPC или журнал `/quests`), `QuestViewMenu` (цели, награды, взять/сдать/отказаться).
  Админу: `QuestMainMenu`, `QuestEditorMenu`, `ObjectiveMenu`, `RewardsMenu`.
- С модулем мобов связь через `MobModule.mobInfo/mobInfos/mobIdOf`.

## Защита мира и зоны (`modules/region`)

- `Area`: кубоид (или весь мир, `wholeWorld`), `Kind` BUILD / MINING. У MINING правила `BlockRule` по материалу:
  `replacement` (null — пусто) и `regenSeconds` (0 — не возвращать). `AreaRegistry`: `data/regions.yml`, раздел
  `areas`, id `build1` / `mine1`. `ruleAt` ищет правило сначала в обычных зонах, потом в «весь мир».
- `ProtectionListener`: игрок без `rpg.region.bypass` (OP по умолчанию) вне BUILD-зон не ломает/ставит/выливает,
  не открывает блоки (`setUseInteractedBlock(DENY)` для `isInteractable`, кроме `regions.allowed-blocks` и дверей),
  не обтёсывает/вспахивает, не трогает рамки, картины, стойки. Взрывы, огонь, мобы-вредители — тоже вне BUILD-зон.
- Ломание по правилу (кроме OP в творческом): опыт сразу игроку, `BlockDropItemEvent` перекладывает предметы в
  инвентарь, на следующем тике ставится замена, `RegenManager.schedule` запоминает исходный `BlockData`.
- `RegenManager`: очередь возврата (позиция → время, BlockData строкой) в разделе `regen` того же файла, задача раз
  в секунду, на диск не чаще раза в 10 с и при выключении. Ждёт, пока чанк загружен; поднимает сущности из блока.
- Листва в MINING-зонах не осыпается (`LeavesDecayEvent`).
- Участки: `Area.Kind.LAND` (id `land1`, Y всегда от `minHeight` до `maxHeight-1`) — земля под участки.
  `Plot` — квадрат X/Z на всю высоту с владельцем и помощниками, `PlotRegistry` хранит их в `data/plots.yml`
  (`plotAt`, `overlapping`). `PlotSize` SMALL/MEDIUM/LARGE, сторона из `plots.size-*`.
  `PlotDeeds`: бумага (PAPER) с PDC `plot_deed_land` + `plot_deed_size`, остальное берётся вживую.
  `PlotListener` (ПКМ бумагой) → `RegionModule.useDeed` → `PlotMapMenu`: сетка мест с шагом в сторону участка от
  min-угла земли, 9x5 клеток на экран со сдвигом, занятые — пересечение с любым участком. `PlotClaimMenu` ещё раз
  проверяет место, лимит, забирает бумагу и создаёт участок. `/plot` → `PlotListMenu` / `PlotMenu`.
- В `ProtectionListener` участок важнее всего: `restricted` на участке = «не владелец и не помощник» (независимо
  от `protect-world`), `worldProtected` (взрывы, огонь, мобы) всегда true на участке, правила добычи там не действуют.

## Хранение

`plugins/MinecraftRPG/`:
- `config.yml` настройки (новые ключи читаются с дефолтами, старые конфиги не ломаются);
- `data/items.yml`, `data/mobs.yml`, `data/zones.yml`, `data/npcs.yml`, `data/quests.yml`, `data/quest-players.yml`, `data/regions.yml`, `data/plots.yml`.

## Совместимость с Bedrock

См. правила в `CLAUDE.md`. Коротко: только сундуки, чат, боссбары, обычные сущности, один обычный клик на кнопку,
ввод через чат, без Display-сущностей и ресурспаков.
