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
| `TurretConfig.java` | 新增 4 个字段与解析；`hasProjectileInterceptorTurret` 的置位改成看 `interceptProjectiles`（或写了过滤器） |
| `hooks/ProjectileInterceptorHook.java` | 开关换成 `interceptProjectiles`；加瞬间命中过滤、排除标签、包含标签（只在写了时生效）、选最近、充能/预热门槛、转向瞄准、开火后重置充能 |

### 关键字段（实测确认）

- `b(i)` = 炮塔 `delay`（毫秒，已乘 `shootDelayMultiplier`）
- `e(i)` = `warmup`（毫秒）
- `movementLevels[i].rotation` = 已充能时间，0..delay
- `movementLevels[i].speed` = 预热进度，0..warmup
- `movementLevels[i].targetX` = 炮管角度（度）

拦截开火条件：`rotation >= (fireDelay>0 ? fireDelay : b(i))` 且 `speed >= warmup`，炮管角度差 <= 6° 才发射，每 tick 最多转 12°。

### 兼容性（要告诉模组作者）

`interceptProjectiles_withTags` **不再负责开启拦截**。老模组只写 withTags 的话，现在不会拦截了，必须补一行：

```
interceptProjectiles: true
```

三个 `interceptProjectiles_and*` 距离/高度键仍然无条件解析，避免 luke 报「键未使用」。

### 验证到哪一步

- 编译通过；headless 600 tick 启动正常。
- 之前 `units/experimental_carrier/Experiemental_carrier.ini` 因为键判定未使用而报错，修成无条件解析后不再报错（这条是从真实报错里抓到的）。
- **没有验证**拦截是否真的击中炮弹、转向是否看起来正常——需要一个 `interceptProjectiles: true` 的测试炮塔和带炮弹的对局。
