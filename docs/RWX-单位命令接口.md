# RWX 单位命令接口（源码调查）

一句话：**指挥单位走模组 API 的 `api.ai.submitActions`，不要靠模拟点击。**

模拟点击要经过 Compose 覆盖层 → AWT → Slick 指针状态 → 引擎触屏判定，任何一段对不上就“点了没反应”。
命令 API 直接到 `CommandController`，中间只多一个排队，稳定得多。

## 三层结构

| 层 | 位置 | 谁在用 |
| --- | --- | --- |
| 界面动作 | `game/units/actions/*.java`（AttackMoveAction、PatrolAction、GuardUnitAction、PlaceBuildingAction…） | 人类点面板 |
| 引擎命令 | `gameFramework/Command.java` + `CommandController` | 引擎内部、模组通过 API 间接调用 |
| 模组 API | `mod-api/.../UnitCommands.kt` → `UnitCommandsImpl` | 模组 / 测试机器人 |

`Command` 自己不是线程安全的，模组层把每次提交包进 `CommandQueue.enqueue`，排到游戏循环里执行。
这也是它比直接写指针状态可靠的原因：跟引擎同一帧、同一线程。

## 14 种命令（mod-api/src/main/kotlin/io/github/rwx/mod/api/UnitCommands.kt）

| `UnitCommandType` | 目标 | 引擎落到哪个 setter |
| --- | --- | --- |
| MOVE | `Point` | `setMoveTarget(x, y)` |
| ATTACK_MOVE | `Point` | `setAttackMoveTarget(x, y)` |
| ATTACK_UNIT | `Unit` | `setAttackTarget(unit)` |
| BUILD | `Point` + `buildUnitTypeId` | `setBuildTarget(x, y, unitType, buildQueueSize)` |
| REPAIR_UNIT | `Unit` | `setRepairTarget(unit)` |
| GUARD_UNIT | `Unit` | `setGuardTarget(unit)` |
| PATROL | `Point` | `setPatrolTarget(x, y)` |
| RECLAIM_UNIT | `Unit` | `setReclaimTarget(unit)` |
| LOAD_INTO_UNIT | `Unit` | `setLoadIntoTarget(unit)` |
| LOAD_UP_UNIT | `Unit` | `setLoadUpTarget(unit)` |
| STOP | 无 | `setClearExistingOrders()` |
| SET_RALLY | `Point` | `setRallyPoint(PointF)` |
| CUSTOM_ACTION | `actionId`（+ 可选 Point/Unit） | `setActionTarget(ActionId, PointF, unit)` |
| OTHER | — | 直接拒绝 |

请求里还能带：`queued`（排队而不打断当前动作）、`highPriority`、`stopCurrentAction`、`buildQueueSize`、`properties`。

## 提交时的校验（`UnitCommandsImpl.EngineUnitCommandBackend`）

1. 队伍存在（`PlayerTeam.k(teamId)`）。
2. 每个 `unitIds` 找得到、是 `OrderableUnit`、没在转移中、`team` 与 `teamId` 一致。
3. 目标单位和建造类型找得到。
4. 该类型需要的目标齐不齐（点/单位/actionId）。
5. `OTHER` 一律拒绝。

任何一条不过，返回 `UnitCommandResult(accepted=false, message=...)实实在在的原因`，不会静默失败。
注意 BUILD 的 `buildUnitTypeId` 是**单位类型名**，先 `UnitTypeEnum.getUnitTypeByName` 再 `CustomUnitConfig`。

## 观察面（`api.ai.currentObservation`）

`AiObservationFrame` 给 tick、队伍、单位、资源、地图。单位 `UnitRuntimeState` 里跟指挥有关的字段：

- `ref.instanceId`（下命令用这个）、`ref.teamId`
- `position`、`rotationDegrees`、`radius`
- `orderable`、`canMove`、`canAttack`、`isBuilding`、`isBuilder`、`isFactory`、`destroyed`
- `health/maxHealth`、`shield`、`energy`、`armor`、`constructionProgress`
- `currentTargetId`、`availableActions`（含 `actionId`，可喂给 CUSTOM_ACTION）

`AiObservationSpec` 可以要 `includeMap`、`includeResources`、`includeUnitOrders`、`includeHiddenEnemies`、`frameStack`。

## 能跑的例子（bench-mod 里已经在用）

```kotlin
api.ai.registerAgent(AiAgentDefinition(id = "rwx-test-agent"))
api.ai.configurePlayer(
    AiPlayerConfig(teamId = 0, enabled = true, mode = AiPlayerMode.EXTERNAL_AGENT,
        agentId = "rwx-test-agent", scope = AiControlScope(teamIds = setOf(0)))
)
val obs = api.ai.currentObservation(AiObservationScope(teamIds = setOf(0)))
val units = obs.units.filter { it.orderable && !it.isBuilding && !it.destroyed && it.ref.teamId == 0 }
val results = api.ai.submitActions(units.map { u ->
    UnitCommandRequest(teamId = 0, unitIds = listOf(u.ref.instanceId),
        type = UnitCommandType.MOVE, target = UnitCommandTarget.Point(WorldPosition(tx, ty)))
})
// results[i].accepted / results[i].message
```

坑：观察默认 `includeEnemies = true`，不按 `teamId` 过滤就会拿到敌方单位，
引擎会回 “Unit X is not on team Y” 拒绝——那是正确的防护，不是 bug。

## 和我们寻路改动的关系

`setMoveTarget` / `setAttackMoveTarget` 是 RWX 加过料的地方：

- `-Drwx.clampReject=1`（默认开）：图外落点直接拒令，记 `RWX_CLAMP_REJECT`。
- `-Drwx.targetFix=0`（默认关）：旧的“钳到边界 + 搬到最近可站格”行为。开了就是老毛病。
- `-Drwx.astar.maxExpand`、`-Drwx.astar.costScale`：A* 展开上限与代价缩放。

所以模拟点击和命令 API 最终走的是同一段代码，区别只在“命令有没有真的进来”。

## 实测记录

| 项 | 数字 |
| --- | --- |
| bench 机器人用 MOVE 下发 | 600 条 accepted，图外目标被拒，走不到边界的 0 例 |
| 其余 13 种命令 | **没测过**，源码路径清楚但没跑过 |

## 边界

- 只调查了源码，没有为 13 种命令写测试；数值和路径都来自代码，不是跑出来的。
- `CUSTOM_ACTION` 依赖单位自己的 `availableActions`，不同类型单位给的不一样，得先观察再下。
- 命令 API 只对 `OrderableUnit` 有效；建筑要建造/生产得走 BUILD 或者各自的建造接口。
