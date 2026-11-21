# 无头寻路测试台架（headless）

不开窗口、自动进图、自动下令、到 tick 自动退出。

```
java -Drwx.selftest=1 -Dbench.water=8 -Dbench.autoExit=3000 -Dbench.waveTicks=300 -Drwx.pathing=1 \
  -jar RWX-windows-x64-1.1.4.jar --headless "--map=maps/skirmish/[z;p10]Many Islands Large (10p).tmx" --ticks=3000
```

地图路径写 `maps/...`，写 `assets/maps/...` 会被再拼一层而找不到。

## 参数

| 属性 | 作用 |
| --- | --- |
| `-Drwx.selftest=1` | 逐点审计路径点是否落在该移动层可走地形，违例打 `RWX_PATH_AUDIT` |
| `-Dbench.water=N` | 在水域刷 N 艘 `bench:boat`（真实 WATER 层） |
| `-Dbench.path=N` | 刷 N 个陆战 `bench:fodder` 并反复下令 |
| `-Dbench.waveTicks` | 多久换一次目标（默认 600） |
| `-Dbench.seed` | 随机种子（同种子同结果） |
| `-Dbench.autoExit` | 到该 tick 自动退出 |
| `-Drwx.pathing=1/2` | 老求解器 / 并行求解器 v2 |

## 已跑过的矩阵（同一台机器）

| 场景 | 水域波次 | 到达率（300 tick 窗口） | 审计违例 | 走廊条数 |
| --- | --- | --- | --- | --- |
| 大岛10p / 8 船 / seed 20261122 | 33 | 43% | 0 | 0 |
| 大岛10p / 8 船 / seed 11 | 33 | 52% | 0 | 0 |
| 大岛10p / 8 船 / seed 22 | 33 | 46% | 0 | 1 |
| 大岛10p / 32 船 / seed 20261122 | 44 | 47% | 0 | 0 |
| 8p Many Islands / 16 船 | 33 | 39% | 0 | 0 |
| 2p Big Island / 8 船 | 33 | 36% | 0 | 2 |
| 大岛10p / 16 船 / seed 33 | 33 | 58% | 0 | 0 |
| 陆战 300 兵 / solver1 | 4（陆战波次） | — | 0 | 0 |
| 陆战 300 兵 / solver2 | 4（陆战波次） | — | 0 | 0 |

同种子同配置的两次结果完全一致（A1/A2、B1/B2），说明台架是确定性的。

## 结论

1. 海军「被要求从陆地穿过」在这 9 次水战测试里 **一次都没出现**（审计 0 违例）。
   单位会绕、会到（近目标窗口内 36%–58% 到达，距离一直在缩短）。
2. 船几乎不走走廊：闸门统计里主要卡在 `route`（直线水域被岛打断），引擎退回普通寻路。
   普通寻路结果正确，但「走廊」对船形同虚设，这是下一步要改的。
3. solver2：同一张图 958 次求解里 **失败 738 次**，平均扩张 1261 格。
   继续只作实验开关（`-Drwx.pathing=2`），默认仍是 solver1。
