# Minecraft RPG

RPG-плагин для сервера **Paper 1.21.x**, который работает и для **Java**, и для **Bedrock** игроков
(через [GeyserMC](https://geysermc.org) + [Floodgate](https://geysermc.org/wiki/floodgate/)).
Цель — «Скайрим в Майнкрафте»: админ настраивает всё прямо в игре, игроки играют.

## Что планируется

| Модуль | Пакет | Что делает |
|---|---|---|
| Зоны | `modules.region` | Мир защищён, админ выделяет зоны, где можно строить и ломать |
| Деревья | `modules.tree` | После сруба дерева сразу садится саженец |
| NPC | `modules.npc` | Админ создаёт NPC: собеседники, квестодатели, торговцы |
| Диалоги | `modules.dialog` | Разговоры с NPC с вариантами ответа |
| Квесты | `modules.quest` | Задания с целями и наградами |
| Магазины | `modules.shop` | Обмен «что-то на что-то» у NPC |
| Мобы | `modules.mob` | Кастомные мобы на основе ванильных: имя, ХП, лут |
| Оружие | `modules.weapon` | Кастомное оружие: название, урон, перки |

Сейчас готова только база: загрузка модулей, конфиг, сообщения, хранилище, определение Bedrock-игроков
и команда `/rpg` (`help`, `modules`, `reload`). Модули пока пустые заготовки.

## Совместимость с Bedrock

Правило для всего кода: всё, что видит игрок, должно работать у обоих клиентов.
Используем сундучные меню, чат, титулы, боссбары и обычные сущности для NPC.
Не опираемся на ресурспаки, кастомные шрифты и клиентские моды. Подробнее в
`core/platform/PlatformService.java`.

## Сборка

Нужна Java 21.

```bash
./gradlew build        # готовый jar: build/libs/minecraft-rpg-<версия>.jar
./gradlew runServer    # запустить тестовый Paper-сервер с плагином (папка run/)
```

Для проверки с Bedrock положите в `run/plugins/` [Geyser-Spigot и Floodgate](https://geysermc.org/download).

## Структура

```
src/main/java/com/dathis/rpg/
├── RpgPlugin.java        точка входа
├── core/
│   ├── command/          /rpg
│   ├── config/           сообщения (MiniMessage)
│   ├── module/           система модулей
│   ├── platform/         Java или Bedrock
│   └── storage/          YAML-хранилище данных
└── modules/              region, tree, npc, dialog, quest, shop, mob, weapon
```
