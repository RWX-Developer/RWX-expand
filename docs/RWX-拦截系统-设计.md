# RWX 拦截系统重设计（设计稿，未实装）

日期：2026-11-24　状态：**已实装并完成无头验证**（键 + 过滤 + 延迟 + 引擎同构开火）；拦截弹已确认可发射。

## 一、现在是什么样（源码事实）

| 项目 | 现状 | 位置 |
| --- | --- | --- |
| 开关 | 只有 `interceptProjectiles_withTags` 一个键，写了才成为拦截炮塔 | `TurretConfig.java:529-536` |
| 过滤 | 只认「炮弹标签 ∈ withTags」这一条包含规则 | `ProjectileInterceptorHook.java:55` |
| 发射 | 每帧扫全场炮弹，找到就立刻开火，**没有冷却、没有延迟** | `ProjectileInterceptorHook.java:59-64` |
| 瞄准 | **不转向、不瞄准**，直接朝炮弹坐标生成弹丸 | 同上（没有 rotation 相关代码） |
| 瞬间命中 | 没有任何判断 | — |
| 炮塔自身延迟 | 拦截路径完全没读 | — |

也就是说：重写前的拦截炮塔是「全自动、无论延迟、不转炮管、必须靠标签」的。（本节为修改前快照，不代表当前实现。）

## 二、重设计后的键（提案）

新增 4 个键，全部挂在炮塔节下，命名沿用现有 `interceptProjectiles_*` 前缀：

| 新键 | 类型 | 默认 | 语义 |
| --- | --- | --- | --- |
| `interceptProjectiles` | bool | false | 总开关。true 即成为拦截炮塔，**不需要写 withTags**，默认拦所有炮弹 |
| `interceptProjectiles_withoutTags` | 标签列表 | 空 | **排除**规则：带这些标签的炮弹一律不拦（优先于包含规则） |
| `interceptProjectiles_fireDelay` | 毫秒 | -1 | 拦截发射间隔。**-1 = 沿用该炮塔自己的开火延迟** |
| `interceptProjectiles_allowInstantHit` | bool | false | 是否允许拦截瞬间命中（`Projectile.isInstantHit`）的炮弹 |

保留 `interceptProjectiles_withTags`：写了就变成「只拦这些标签」（缩小范围），不写就是全拦。
保留 `interceptProjectiles_andTargetingGroundUnderDistance` / `_andUnderDistance` / `_andOverHeight` 三个距离/高度门槛不变。

## 三、判定顺序（重写后的 `ProjectileInterceptorHook`）

1. 炮塔不是拦截炮塔 → 跳过。
2. 能量/资源不够（`CustomUnit.a(TurretConfig)`）→ 跳过。
3. 冷却未到（见第四节）→ 跳过。
4. 逐颗炮弹过滤：
   - `isInstantHit` 且未开 `allowInstantHit` → 跳过；
   - 命中 `withoutTags` → 跳过；
   - 写了 `withTags` 且炮弹标签不在其中 → 跳过；
   - 距离/高度门槛不满足 → 跳过；
   - 已被别的拦截弹锁定（现有 `a(Projectile)`）→ 跳过。
5. 选中最近的一颗（现在是「最后一个命中」，要改成最近）。
6. **转向瞄准**：炮管转到目标角度，转到位才发射。
7. 发射后重置冷却。

## 四、两个还没定的实现细节

### 1. 「炮塔自己的开火延迟」到底是哪个字段

需要确认 `TurretConfig` 里对应「两次开火之间间隔」的字段（候选：`attackDelay`、`warmup`、`reloadTime`）。
现有代码里 `warmup` 出现在转向/预热比较（`CustomUnit.java:1377`），值域 0..9000，看着像**预热进度**不是毫秒延迟。
这个确认之前不能把 `-1` 的默认接到错误字段上。

### 2. 炮塔转向用什么接口

`movementLevels[i]` 的 `rotation` 是 0..9000 的**预热进度**，不是角度；`targetX` 才是目标角度（`UnitMovementData.java:8-20`）。
但真正把 targetX 往角度上靠的那段代码在反编译里被改名了，我还没定位到。
**在定位到之前，强行写 rotation 会把所有炮塔的转向逻辑弄坏。**

## 五、打算怎么落地（分两步）

**第一步（低风险，可先做）**

- `TurretConfig` 加 4 个字段与解析，`hasProjectileInterceptorTurret` 的置位条件改成「`interceptProjectiles` 为真 **或** `withTags` 非空」。
- Hook 里加：瞬间命中过滤、排除标签过滤、写了 withTags 才做包含过滤、改「选最近」。
- 这一步完全不碰转向与延迟，行为可预测。

**第二步（需要先定位字段）**

- 接 `fireDelay` 默认值到正确的炮塔延迟字段。
- 接转向瞄准：找到引擎里「把炮塔角度转向 targetX」的那段，复用它，转到阈值内才开火。

## 六、边界

- 本文档的所有「现状」都有行号，可核对；但**没有跑过一次拦截测试**，因为要造一个带拦截炮塔的测试模组。
- 第一步做完也只能证明「编译通过 + 键被读到」，战斗里的表现要另做场景验证。
- 键名如果老师要改（比如 `withoutTags` 想叫 `cannotInterceptWithTags`），现在改最便宜。


## 七、实装记录（2026-11-24）

### 改了什么

| 文件 | 改动 |
| --- | --- |
| `TurretConfig.java` | 新增 4 个字段与解析；`hasProjectileInterceptorTurret` 的置位改成看 `interceptProjectiles`（或写了过滤器，兼容老模组） |
| `hooks/ProjectileInterceptorHook.java` | 开关换成 `interceptProjectiles`；加瞬间命中过滤、排除标签、包含标签（只在写了时生效）；**开火改为完全复用引擎炮塔状态机** |

### 关键字段（以引擎源码为准，单位统一为「帧」）

- `b(i)` = 炮塔 `delay`（帧，`delay: 2s` 经 `parseUnitValue` ×60 = 120，已乘 `shootDelayMultiplier`）
- `t(i)` = `warmupShootDelayTransfer` 折算的延迟补偿（帧，通常为 0）
- `e(i)` = `warmup`（帧）
- `movementLevels[i].rotation` = **开火冷却倒数**：发射后 = `b(i)+t(i)`，引擎每帧 `moveTowardsZero(rotation, f)` 递减，**归零才就绪**（`OrderableUnit.java:899 / :3296 / :3312`）
- `movementLevels[i].speed` = 预热进度，0..warmup（引擎 `advanceMovementLevelTowardTarget` 累加）
- `movementLevels[i].targetX` = 炮管角度（度）

### 当前拦截开火条件（与引擎同构）

1. 资源够（`customUnit.a(turretConfig)`）
2. 目标炮弹通过全部过滤（瞬间命中/标签/高度/距离/友军/已锁定）。每炮塔会**锁定**当前目标：只要它还通过过滤就一直瞄它，避免每帧换目标来回摆；开火后清空锁定
3. **炮管先转到入射角**：目标角取抛射物飞行方向的反向角 `projectile.angle + 180`（归一化到 -180..180）。这是稳定角，不会像「炮塔→抛射物当前位置」那样随距离快速变化。先算炮塔当前角与入射角的差 `rotateTowardsAngle(targetX, incomingAngle, 360)`，再走引擎同一个 `customUnit.a(frameDelta, angle, i)`，转速/加减速与攻击敌人时一致（`turnSpeed` / `turnSpeedAcceleration` / `turnSpeedDeceleration`）。剩余角度 `<= 2°`（固定阈值，不允许 `canAttackMaxAngle=181` 这类「免转向发射」配置绕过）
4. `rotation == 0`（引擎冷却就绪）
5. 预热 `speed >= warmup`；预热只在第 3、4 步都通过后开始累，转向/冷却期间按 `warmupCallDownRate` 回落
6. （可选 `fireDelay > 0`）额外等待累加器到点，未就绪时同样清零重来
7. 扣资源 → 发射弹丸 → `rotation = b(i)+t(i)`、`speed = 0`

> 顺序很重要：必须是「先转向 → 再预热 → 再开火」。如果转向过程中就攒预热，炮管刚转到位会立刻开火，视觉上像拦截弹比炮管还快。

> **重要修正**：早期版本（14:49 / 15:47 两次提交）曾自造 `RWX_CHARGE` 毫秒累加并写 `rotation = min(charge, delay)`，方向与引擎的「倒数归零」完全相反、量纲也不一致，导致炮塔永远处于冷却中、**完全拦不住炮弹**。现已删除该机制，改为上面这套引擎同构逻辑。

### 兼容性（模组作者注意）

- 老模组只写 `interceptProjectiles_withTags`、没写 `interceptProjectiles` 的，`TurretConfig.java:559` 仍会自动推断为拦截炮塔（保持兼容，无需补键）。
- 新模组推荐显式写 `interceptProjectiles: true`；`withTags` 现在只是「只拦这些标签」的过滤器，不再负责开启。
- 三个 `interceptProjectiles_and*` 距离/高度键无条件解析，避免 luke 报「键未使用」。

### 附属炮塔 / 主炮塔的关系

引擎里其实有三套不同的“联动”，不要混为一谈：

| 配置 | 落点 | 语义 |
| --- | --- | --- |
| `attachedTo: 主炮` | `TurretConfig.parentTurret` → `linkedTurretIndex` | 子炮待机角 `C(i)` = 父炮 `targetX + idleDir`；`limitingAngle` 也按父炮方向算；子炮有目标时仍可独立转向 |
| `slave: true` | 父炮的 `childTurret` → `x` → `getLinkedTurretIndex(i)` | 父炮的 `targetX` 和 `isActive` 从子炮继承（双联炮：子炮负责索敌转向，父炮跟随开火） |
| `linkDelayWithTurret: 另一炮` | `TurretConfig.linkedTurret` | 本炮开火时把引用它的另一炮 `rotation` 清零；不影响角度 |

拦截钩子的处理：

- 开火时已补回 `CustomUnit.a(BaseUnit, int)` 里的联炮冷却联动（`linkDelayWithTurret` + `attachedTo` 父炮 recoil），否则拦截弹走 6 参数生成弹丸会绕过这段，附属炮塔与主炮塔冷却失步。
- 当前游戏内三处拦截炮（`antinuke_launcher`、`experimental_gunship`、`experimental_carrier`、`nautilus` 等）都是独立 `canShoot:false` 炮塔，没有 `attachedTo` / `slave`，所以不受角度继承影响。
- 若以后把拦截炮挂到主炮上：必须写 `shouldResetTurret: false`，否则主炮转向会把拦截炮的目标角拉回去；`slave` 父炮不要单独开拦截，应由负责索敌的子炮承担。

### 验证到哪一步

- `:core:compileJava`、`:desktop:packageUberJarForCurrentOS` 编译/打包通过（JDK 25 + Gradle 9.8）。
- 已用最小无头场景验证：`itest:shooter` 发射带 `nuke` 标签的靶弹，`itest:blocker` 开启拦截；日志确认选中目标、炮管到位（`remaining=0.0` / `turretX=180.0`）并连续生成拦截弹（`RWX_INTERCEPT fired=1` ... `fired=30`）。
- 测试场景踩过的坑：`rawSection` 同名段会整段替换 typed turret，必须把 `projectile` / `turnSpeed` 等原生键写全；拦截炮塔建议 `shouldResetTurret: false`，否则引擎每帧把炮管拉回 `idleDir`，会把拦截钩子的转向抵消掉。
- 验证用的临时 mod、探针和配置已在验证后移除，正式仓库只保留引擎同构的开火逻辑。
