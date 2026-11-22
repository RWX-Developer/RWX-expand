# RWX 第十三轮：AI 区域化名单——把 zone 的整份名单切成三条保序子序列

老师提的方向：「矩阵化处理和区域化处理也可以考虑一下，通过把一些单位组划分为
不同的区域以减少线程」。落地时先量了一遍「谁在读 zone 名单」，结果把方向修正了
两处（见第五节），最后做的是**名单区域化**而不是地图网格。

## 一、账本先摆出来（bench：4000 单位 + 600 高频触发单位，tick=1200 窗口，每 tick 均值）

| 每 tick zone 名单访问 | 桶关闭（原路径） | 桶开启 | 变化 |
| --- | --- | --- | --- |
| list 合计 | 12553 | 5526 | **-56%** |
| └ findFactoryForUnitType（fact） | 9892 | 3350 | -66% |
| └ countIdleCombatUnits（idle，12 连发） | 2430 | 759 | -69% |
| └ findHarvesterForBuildTarget（bld） | 205 | 0 | 本窗口未触发 |
| 每单位名单访问（objects≈4950） | 2.54 | 1.09 | -57% |

时间侧同窗参考：aiZones 181.0ms/帧 → 106.8ms/帧。**但这两局不是同一世界**
（见第四节），时间数字只当参考，不当证据。

## 二、做法：桶 = 原名单的保序子序列，谓词链一字不动

`BaseZone.rwxZoneScan()` 每次重建 unitsInZone 时顺手把成员分进三条 `UnitList`：

| 桶 | 分桶谓词 | 消费者 |
| --- | --- | --- |
| rwxFactoryUnits | `instanceof FactoryQueueInterface && instanceof OrderableUnit` | findFactoryForUnitType、canBuild 集合 |
| rwxIdleCandidateUnits | `instanceof OrderableUnit && !canUnitAttack()` | countIdleCombatUnits |
| rwxHarvesterUnits | `r().n()`（isHarvester） | findHarvesterForBuildTarget、retargetHarvestersToBetterBuilding |

三个不变式，缺一条就不等价：

1. **超集**：分桶谓词是消费链里某条**静态**谓词的同一次调用。canUnitAttack() 有
   4 处重写（OrderableUnit=`r().m()`、CustomUnit=`unitConfig.useAsBuilder`、
   FireUnit/Tree=恒 false），全是兵种/配置级常量，tick 内不变——分桶时直接调
   **同一个虚方法**，不做跨类等价推导。
2. **保序**：桶按 unitsInZone 的 add 顺序追加，消费链选中的对象、lastTimeBuilt
   自增次数、RNG 消耗次数（RNG 只对通过链前段的元素消耗）与改前逐位一致。
3. **同生同灭**：unitsInZone 全部 4 处 add、2 处 clear、1 处 remove 都同步维护桶
   （updateUnitsInZone 这条目前无调用者的路径也补了，防以后接上）。

消费循环仍逐字执行原来的整条谓词链（isActive/canAfford 这些同 tick 内会变的照旧
现场判），桶只缩短遍历长度，不替链做决定。

## 三、等价性怎么证：同进程不变式，不是跨进程对答案

`-Drwx.zoneVerify=1`（默认关）：每次消费前现场把 unitsInZone 按同一谓词过滤一遍，
与桶逐元素比对身份与顺序。实测 **8193 次校验、0 次失配、0 异常**。
校验与分桶同源，所以它证明的是「实现没写错」；谓词选取的正确性靠第二节的
超集推导 + 源码逐字核对（canUnitAttack 的全部 4 个重写都读过了）。

## 四、说清楚：同种子跨进程复现在这台机器上不成立

本轮把 12 处 `Math.random()` 和 1 处裸 `new Random()` 统一进了 `Utility` 的可
种子随机流，又接上了引擎自己的网络校验和（`-Drwx.stateSum=1`）。但实测同一 jar、
同一 `-Drwx.seed=777` 连跑两局，世界 gid 仍然不同（1487112536 vs 1545589874），
而且拿到两局完全相同参数连跑的实锤：状态校验和序列从第一行（tick=60）就分叉
（total=85477740260 vs 81106857914，单位 id 和 出生位置从开局就不同）——
出生点/地图生成还没被 `-Drwx.seed` 覆盖，这条种子统一只到位了逻辑层随机流。
所以「两局对 state-sum 序列」这条证据路**没用上**，等价证据换成了第三节的同进程
不变式。null 守卫（GameLogic 对压缩尾部 null 的 continue）与校验和打印仍留在包里。

## 五、被自己数据推翻的假设：最近区域网格

「按地图把 zone 划成网格查表」的原始想法，实测 `findNearestZone` 每 tick 只有
63 次调用、381 次内层迭代（nz63/nzi381）——zone 总数本来就小，网格纯属浪费，
**没有做**。数据说大头是名单遍历（上万次/帧），就按名单下手。

## 六、开关与回退

- `-Drwx.zoneBuckets=0`：消费点退回整份 unitsInZone，桶代码空转。
- `-Drwx.zoneVerify=1`：开同进程不变式校验（有现场过滤成本，只用于测试）。
- 回退不需要改任何行为逻辑，一个属性就回到旧路径。

## 七、没测过的 / 边界

- 时间收益没有同世界 A/B，只敢给「名单访问 -56%、每单位访问 2.54→1.09」这种
  直接计数结论；181→107ms 里有多少是桶、多少是这两局本身走偏，没拆开。
- snap（本队快照循环）两局 1117 vs 2032，波动来自世界分歧不是桶，桶不碰快照。
- type 计数 207→1385：countUnitsOfType 还没吃桶（它按兵种计数，谓词链没有静态
  必要条件可提），下一轮可以考虑兵种×zone 计数矩阵。
- 残余 AI 尖峰里 aiStrat/aiTryB 单次仍有 40–168ms，本轮没有动它们。


## 八、补：老师澄清方向后——「一个区域中同兵种共用一条名单」

第一节的桶解决的是「找工厂别陪跑战斗单位」。老师澄清说要的是**同兵种合批**后，
回头发现账上正好躺着一台重复计算器：getBestBuildingToBuild 每调一次，对模组里
~120 个建筑型**各调一次 countUnitsOfType，每次把整份 unitsInZone 从头扫到尾**。

实现（同一个 `-Drwx.zoneBuckets` 开关下）：

- `rwxZoneScan` 重建名单时顺手按 `baseUnit.unitType` 建一张
  「兵种 → 保序名单」表（`HashMap<UnitType, UnitList>`，名单对象跨 tick
  复用，只 clear 不重建）。
- `countUnitsOfType` 只走本兵种那一条；消费循环原来的整条谓词链
  （team/unitType/countUnitsForStrategy）照旧逐字重跑。
- 为什么等价：`unitType` 在单位存活期间不变（运行期唯一改写点是模组重载路径
  `CustomUnitConfig.load()`，只在 ModManager 里调，不落在逻辑 tick 中间）；
  桶是原名单按同一身份的分组视图，链里含 `unitType == 查询型` 这一条，
  所以候选集合与原整表扫严格相同。
- 兵种表同样接了 `-Drwx.zoneVerify=1` 的同进程不变式校验（桶 == 现筛子序列，
  逐元素比身份与顺序）。

### 数字（tick=1200 窗口，每 tick 均值；三局不是同一世界，时间列只作趋势参考）

| 指标 | 桶关 ab-off | 上一轮 perf1 | 合批后 perf2 |
| --- | --- | --- | --- |
| list 名单访问/tick | 12553 | 5526 | **3580** |
| └ type（countUnitsOfType） | 207 | 1385 | **0** |
| └ fact（找工厂） | 9892 | 3350 | 2813 |
| └ idle（12 连发） | 2430 | 759 | 755 |
| aiZones | 181.0 ms | 106.8 ms | **36.4 ms** |
| 校验 | — | 8193 次 0 失配 | 4097+ 次 **0 失配** |

type 归零 = GBB 的百型循环不再每型全扫名单。合批后再算一笔账：
每单位每 tick 名单访问 12553/4935=2.54 → 3580/4133=0.87，是原路径的三分之一。

### 这离老师说的还差一层（记在这里）

「共用一条名单」做了（同兵种共用遍历，不再各扫各的）。
更进一步的「同兵种共用**一次决策**」（12 连发里同型只掷一次骰、算一次
canAfford，然后批量下发）还没做——那条链上 `reclaimScore -= 0.5f`、
`credits` 扣费、工厂队列都是同 tick 内会变的量，缓存判定要逐个失效点核，
本轮先把无争议的重复扫减掉。剩余大头 fact2813 同理。
