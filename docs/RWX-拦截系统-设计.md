# RWX 拦截系统重设计（设计稿，未实装）

日期：2026-11-24　状态：**第一步已实装**（键 + 过滤 + 延迟 + 瞄准），未做战斗场景验证

## 一、现在是什么样（源码事实）

| 项目 | 现状 | 位置 |
| --- | --- | --- |
| 开关 | 只有 `interceptProjectiles_withTags` 一个键，写了才成为拦截炮塔 | `TurretConfig.java:529-536` |
| 过滤 | 只认「炮弹标签 ∈ withTags」这一条包含规则 | `ProjectileInterceptorHook.java:55` |
| 发射 | 每帧扫全场炮弹，找到就立刻开火，**没有冷却、没有延迟** | `ProjectileInterceptorHook.java:59-64` |
| 瞄准 | **不转向、不瞄准**，直接朝炮弹坐标生成弹丸 | 同上（没有 rotation 相关代码） |
| 瞬间命中 | 没有任何判断 | — |
| 炮塔自身延迟 | 拦截路径完全没读 | — |

也就是说：现在的拦截炮塔是「全自动、无论延迟、不转炮管、必须靠标签」的。

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
2. 目标炮弹通过全部过滤（瞬间命中/标签/高度/距离/友军/已锁定）
3. `rotation == 0`（引擎冷却就绪）
4. 炮管角度差 `<= 2°`
5. 预热 `speed >= warmup`
6. （可选 `fireDelay > 0`）额外等待累加器到点
7. 扣资源 → 发射弹丸 → `rotation = b(i)+t(i)`、`speed = 0`

> **重要修正**：早期版本（14:49 / 15:47 两次提交）曾自造 `RWX_CHARGE` 毫秒累加并写 `rotation = min(charge, delay)`，方向与引擎的「倒数归零」完全相反、量纲也不一致，导致炮塔永远处于冷却中、**完全拦不住炮弹**。现已删除该机制，改为上面这套引擎同构逻辑。

### 兼容性（模组作者注意）

- 老模组只写 `interceptProjectiles_withTags`、没写 `interceptProjectiles` 的，`TurretConfig.java:559` 仍会自动推断为拦截炮塔（保持兼容，无需补键）。
- 新模组推荐显式写 `interceptProjectiles: true`；`withTags` 现在只是「只拦这些标签」的过滤器，不再负责开启。
- 三个 `interceptProjectiles_and*` 距离/高度键无条件解析，避免 luke 报「键未使用」。

### 验证到哪一步

- `:core:compileJava` 编译通过（JDK 25 + Gradle 9.8）。
- **尚未做**战斗场景验证：需要一个 `interceptProjectiles: true` + `withTags: nuke` 的测试炮塔和带 nuke 的对局，确认拦截弹真的生成并击中目标炮弹。原版（`42e1504`）逻辑已知可用，本次重写刻意保持索敌/发射同构，风险集中在转向阈值和预热衔接。
