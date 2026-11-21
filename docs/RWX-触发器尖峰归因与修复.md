# 4000 兵 + 高频自动触发：尖峰归因与修复

## 归因（新计数器 trigN=触发帧/条件求值/cb回调访问）

尖峰帧的账本：`trigN=600/720/cb1879527 objects=4808 total=95.1ms triggers=89.3` ——
600 个触发单位 × 每 tick 求值 → **每 tick 访问 170–190 万个单位**（cb 计数），
全在 `withinRange` 计数扫描里。gc=0，不是垃圾回收；平均行里 triggers 只有 1–2 ms，
**掉帧全是触发扫描的尖峰帧**（50–130 ms）。

## 修复

1. **cap 取最大需求（max-need）**：原第十轮实现多阈值时取最小值，且显式比较
   （`numberOfUnitsInTeam(...)>20` 的 greaterThan=20）不参与 cap。现改为
   `need = max(g+1, l, 1)`，比较结果与真值恒等（计数饱和到 cap）。
   `full`/无阈值的纯计数保持精确值。
2. 尖峰归因计数器进 `RWX_SPIKE` 行：`trigN=<触发帧>/<条件求值>/cb<回调访问>`。

## 实测（4000 兵 + 600 高频触发单位）

| 写法 | 尖峰帧数 | triggers 平均 | 尖峰上 triggers |
| --- | --- | --- | --- |
| cmp 形（`f(..)>999999`，修复前） | 42 | 1.5–5.8 ms | 89–126 ms |
| cmp 形（修复后） | 29 | 2.6–3.5 ms | 45–93 ms |
| gt 形（`f(greaterThan=0,..)`，修复后） | **3** | **0.05 ms** | 0.3 ms（残留全是 AI 涨兵期 teams 段） |

`RWX_COUNT_ABORT cap=1 at unit=1` 留痕证明早退真实执行：`greaterThan=0` 的条件
扫到 1 个单位就收手，不再全范围清点。

## 残余问题（下一轮）

cmp 形把阈值写在外层（`numberOfUnitsInTeam(withinRange=600) > 999999`），
内层计数函数拿不到阈值 → 仍全扫描。修法是解析期把外层字面量比较**下推**进
计数函数的 greaterThan/lessThan（仅当左操作数是 rwxCountOnly 函数、右是常量时）。
