# 走廊（corridor）为什么几乎不生效 —— 实测诊断

## 方法

自动化台架（不需要人操作）：`-Drwx.selftest=1` 开启路径审计，`-Dbench.path=N` 由 mod 自动下令，
`RWX_RP_STATS` 每 50 条命令输出走廊规划闸门统计。跑法：

```
java -Drwx.selftest=1 -Dbench.path=200 -jar RWX-windows-x64-1.1.4.jar
```

## 数据（同一台机器，自动进局，各约 100 秒）

| 版本 | 走廊规划成功/尝试 | 主要失败闸门 |
| --- | --- | --- |
| 初始 | 10/450（2.2%） | cmdType=418（命令类型不是 move/attackMove） |
| 放宽为 move/attackMove/attack/patrol | 6/250 | cmdType=212 |
| 改成黑名单（只挡 guard/guardAt/setPassiveTarget） | 18/400 | noLead=313（命令里没有可执行单位，多为空 AI 命令） |

玩家式 MOVE 命令（bench 驱动，ai=false）：**133 波命令产生 200 条走廊**，即玩家移动令走廊正常。

## 结论

1. 「单位不用走廊」的主因是 **命令类型闸门**：AI 的命令绝大多数是 `attack`，被 `!= move && != attackMove` 挡掉 93%。
   已改为黑名单制，attack/patrol/build/repair/load 等都会规划走廊。
2. 剩余 `noLead` 是**没有单位的空命令**，本身不该规划走廊，不算问题。
3. 路径审计（`RWX_PATH_AUDIT`）在本轮 200 条走廊里 **0 违例**：暂未复现「海军路线穿陆地」，
   审计器已就位，后续用带水域的自动场景继续跑。

## 同轮修掉的另一件事：取消权限

- `OrderableUnit.clearAllWaypoints()` 原来对所有来源都保护 `isForceMove` 队首点 → **玩家也取消不掉**模组行动给的路径点。
- 现在拆成 `clearAllWaypoints()`（玩家/引擎：全清）与 `clearAllWaypoints(boolean rwxFromAi)`（AI：保留模组行动点），
  `Command` 的两处调用改为传 `this.rwxFromAi`。

## 环境事故（已处理）

- 构建失败：`Unexpected lock protocol found in lock file. Expected 3, found 0`。
- 原因：gradle 缓存锁文件损坏（先前多次强杀进程所致）。
- 处理：停 daemon，删除 `D:/Game/tools/gradle-home` 下 3014 个 `.lock`，清 `caches/9.8.0/{fileHashes,fileContent,executionHistory}`，冷编译 6 分钟后恢复。
