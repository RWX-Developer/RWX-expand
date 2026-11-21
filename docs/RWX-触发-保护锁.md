# RWX 第十二轮：触发器「保护锁」——让空转的自动触发连扫描都不发起

起因是老师转述顾寂的几句话：

> 「他们写个自动触发就一直触发，都不带关的」
> 「自动触发都会带有优化意识，写一个自动保护锁」
> 「就是自动触发必须关」「不能让他空转」
> 「其实本质上是铁锈战争，它没有用于高频率触发的东西」
> 「这个刚好不是可以 jvm 来做吗」

前面几轮已经把阈值写在参数里的写法（`numberOfUnitsInTeam(greaterThan=0, withinRange=400)`）
压到 3 个尖峰。剩下的是阈值写在外层的写法：

```
if numberOfUnitsInTeam(withinRange=400) > 999999
```

这种条件**永远为假**，但引擎每 tick 都要在 400 半径里把单位一个一个数出来才发现它假 ——
4000 兵实测每次求值 180 万回调访问/tick。cap（第十/十一轮）救不了它：要区分
`> 999999` 就得真数到 100 万，上限根本没有意义。

所以这一轮做的是顾寂说的那个东西：**保护锁**。判断「这个条件这条 tick 到底有没有可能变」，
不可能变就**连扫描都不发起**。

---

## 一、两条锁

### 1. 保护锁（本轮的新东西）

范围内计数 ≤ 全队总数，而全队总数是引擎里免费的 O(1) 统计。
如果总数本身就够不到阈值，比较结果已经确定，不需要扫描：

| 条件形状 | 判定 | 返回 |
| --- | --- | --- |
| `count > g` 且 `total <= floor(g)` | 必假 | 直接返回 `total` |
| `count < l` 且 `total < ceil(l)` | 必真 | 直接返回 `total`（≤ l，代入结果一致） |
| 两侧夹逼 / `==` / `full` / `empty` | 需要精确 | 不锁，照旧扫 |

上界来源分三种，都是核过源码的：

- 本队（`numberOfUnitsInTeam`，默认）：`PlayerTeam.getUnitCount()` / `getUnitCountWithTag()`。
  `UnitStatisticsManager.a(BaseUnit)` 把每个单位同时放进格子全局桶 `a` 和该格
  `b[spatialIndexTeamId]` / `c`(-2) / `d`(-1) 桶，所以「颜色队桶 ⊆ 该队统计」成立。
- 敌军 / 友军（`numberOfUnitsInEnemyOrAllyTeam`）：`PlayerTeam.b()` / `.c()` 是对 ≤10 个队的
  统计求和，同样覆盖对应桶。
- `numberOfUnitsInAllTeams` / `neutralTeam`：**不能**用颜色队求和当上界 ——
  `getTeamInstances()` 只返回 `TEAM_ALL`、`TEAM_UNKNOWN` 和颜色队 0..9，中立特殊队
  （teamId ≥ 10）的单位在扫描桶里但不在求和里，求和可能小于真实计数，锁会误判。
  所以这一种改用**全局对象列表** `GameObject.dK().size()`：
  `BaseUnit → SizedObject → PositionedObject → gameFramework.GameObject`，构造函数里
  `a.a(this)` 把每个非静态对象都放进那个列表，`size()` 是 O(1) 字段读，单位是其子集 —— 普适真上界。

### 2. 饱和 cap（第十/十一轮，本轮补齐外层路径）

扫描数到 `阈值+1` 就收手，`min(count, cap)` 与真值在「与该阈值比较」下结果恒等。
本轮补上了**数值读取路径**（`readNumber`）：`f(...) > N` 走的是这条路，原来完全不设 cap。

## 二、阈值怎么进到内层

外层字面量比较在求值前把阈值交给计数节点：

```java
// CompareJoinerBoolean.rwxPushThreshold —— 只在「恰好两个子节点、其中一个是字面量」时下推
// 链式 a > b > c 语义不同，保守不动；== / != 不下推（那两个要精确总数）
```

计数节点拿到后合并进自己的阈值（`rwxMergedGt/Lt`），同一份逻辑同时决定 cap 和保护锁。
下推安全的前提核过：`LogicBoolean.with(cfg,args,name)` 是 `clone()` 出**每个解析位一份实例**，
字段不跨条件共享（对照第二轮那个 static 走廊串味的 bug，这次不是 static）。

## 三、必须精确的情况一律退回

锁和 cap 都会改变「拿到的数」，只有当这个数**只被那一个比较消费**时才安全。所以：

- `==` / `!=`、被数学运算（`+ - * / %`）消费的计数，在**解析期**标记 `rwxExactNeeded`，
  该节点的 cap 与保护锁一起关闭，走精确计数；
- 参数里需要 `==maxValue`（`full`）或 `empty` 的，保持精确；
- 没有阈值的纯数值读取（`f(...)` 当数用）cap = `MAX_VALUE`，不动。

## 四、怎么证明锁没错

引擎里留了一个开关：`-Drwx.countVerify=1`（影子对账）。打开后**跳过之前仍然真扫一遍**，
把「总数」和「真扫计数」分别代入该条件的阈值算出两边判断结果，不一致就打一条
`RWX_LOCK_MISMATCH`（最多 3 条）。

台架加了第 10 组条件 `bench.trigForm=lock`，专挑阈值落在总数附近、边界上的形状：

```
> 999999 / >= 999999 / < 999999 / <= 999999 / < 3000 / >= 3000 / == 3000
```

**实测结果：`MISMATCH=0`**（该次运行窗口统计 11.96 万次求值、cb=40582071 —— 对账模式下
照常全扫，同时逐条核对）。这个模式很慢（它本来就是要全扫），只用于取证。

## 五、数字

4000 兵 + 600 只 6 条件自动触发单位，`-Dbench.trigForm=cmp`（阈值写外层），**同一个 jar**、
同一个启动脚本，只差 `-Drwx.countLock=0` 这一个属性；600-tick 窗口累计（`trigWin=<触发帧>/<条件求值>/cb<回调访问>`）：

| 窗口 | 关锁 TOTAL 均/峰 | 开锁 TOTAL 均/峰 | 关锁 triggers 均 | 开锁 triggers 均 | 关锁 cb/窗口 | 开锁 cb/窗口 |
| --- | --- | --- | --- | --- | --- | --- |
| tick=600 | 43.88/330.2 ms | 17.88/305.0 ms | 29.51 ms | 0.33 ms | 228,102,287 | **0** |
| tick=1200 | 23.03/309.1 ms | 5.67/74.6 ms | 17.38 ms | 0.11 ms | 129,955,299 | **0** |
| tick=1800 | 47.49/305.5 ms | 6.56/68.3 ms | 41.81 ms | 0.23 ms | 343,510,127 | **0** |

触发器这一项是 **100 倍以上**（41.81 → 0.23 ms/tick），逻辑帧均耗时从 23–47 ms 降到 5.7–17.9 ms。
影子对账在两个模式下各跑过一次：`run-verify.out`（cmp 形）和 `run-lock.out`（lock 形，119,641 次
触发帧求值、40,582,071 次回调访问），两次 **`MISMATCH=0`**，回调早退 `RWX_COUNT_ABORT` 均有留痕。

**必须说清楚的边界**：

- 峰值帧（第一个窗口的 max 305/330 ms）**没有被本轮解决**。那些帧发生在刷兵期，归因
  `worst=teams`/`worst=objects`/`worst=commands`（AI 决策 + 对象更新 + 指令下发），
  与触发器无关。稳态窗口里 `cb>0`（也就是触发器造成的）尖峰：关锁有 7 个，开锁 **0 个**。
- 每局随机种子不同（`gid`），单位落位不同，AI 决策量就不同；TOTAL 的均值是三轮窗口的实测，
  没有做同种子多次重复，**不要把 43.88→17.88 当成可复现的固定倍数**，cb→0 才是本轮确定的东西。
- 台架是合成负载。真实模组里若 `withinRange` 计数被 `==`/`!=`/数学运算消费，走精确路径（不锁）。
- 残余的 AI 尖峰（`aiStrat`/`aiTryB` 单次 50–60 ms）是下一个目标，本轮没有动它。

## 六、开关与回退

| 属性 | 作用 |
| --- | --- |
| `-Drwx.countLock=0` | 关闭本轮全部机制（下推、cap 外层路径、保护锁），回到第十一轮行为 |
| `-Drwx.countVerify=1` | 影子对账（跳过前仍全扫并核对），慢，仅调试用 |

代码位置：

- `core/.../logicBooleans/LogicBoolean.java` —— `AbstractNumberBoolean`：`rwxCountLock`、
  `rwxPushThreshold`、`rwxMergedGt/Lt`、`rwxSkipLockApplies`、`rwxVerifySkipped`、
  `rwxExactNeeded`、带 cap 的 `readNumber`。
- `core/.../logicBooleans/CompareJoinerBoolean.java` —— `rwxPushThreshold`（四个序比较的
  `read` 首行调用）、`rwxMarkExact`（`==`/`!=` 与数学运算在 `validateAndOptimize` 里打标）。
- `core/.../logicBooleans/LogicBooleanGameFunctions.java` —— `NumberOfUnitsInTeam.getValue`
  （三种上界来源 + 锁 + 对账）、`NumberOfUnitsInEnemyOrAllyTeam.getValue`（同样两条锁）。
- `core/.../io/github/rwx/probe/RwxPerf.java` —— 尖峰行加 `/lock<累计>`，窗口行加
  `trigWin=<帧>/<条件求值>/cb<回调访问>/lockWin=<每 tick 平均命中>`，
  这样**没有尖峰的窗口也能看到扫描次数为 0**（原来只有尖峰帧才打印，属于取证盲区）。

顾寂那句「这个刚好不是可以 jvm 来做吗」是对的：这事在引擎里做，模组作者不用改任何东西，
也不会因为忘了写保护锁而卡。
