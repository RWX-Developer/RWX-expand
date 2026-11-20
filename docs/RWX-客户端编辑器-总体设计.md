# RWX 客户端编辑器（IDE）总体设计稿

状态：设计稿，未开工。本文件是施工图，不代表任何能力已实现。

## 0. 一句话

把「改游戏的一切」从「手改 ini + 记事本 + 反复重启」变成一个带校验、能预览、能打包的桌面 IDE。
三种模式共用一套底座，只是编辑对象和产物不同。

## 1. 三种模式

| 模式 | 编辑对象 | 产物 | 关键能力 |
| --- | --- | --- | --- |
| 打包模式 Packaging | 工程清单、客户端文件、mods、assets、runtime | 可分发的整合包 / 更新包 | 清单管理、差异、版本号、校验和、CHANGED-FILES 自动生成、一键构建 |
| 客户端编辑模式 Client | 客户端本身：渲染后端、键位、偏好、UI、性能开关 | preferences.toml / 客户端配置 / 桌面层行为 | 设置项分组、影响面提示、即时生效或需重载的标注 |
| 模组编辑模式 Mod | mods/units 下的一切：mod-info、单位 ini、逻辑布尔、资源、地图、局内任务 | 修改后的模组目录 / .rwmod | 结构树、字段表单、表达式编辑器、校验、局内预览 |

## 2. 现有可复用资产（已勘）

- UI：Compose Desktop 已接入（desktop 模块 42 个 kt，Compose + Skia/Slick 双渲染后端，已有 SwingAppHost）。
- 数据：`ModManager.loadJvmMods()` 扫描 mods；`IniFile` 解析器；`TileMap`；`UnitStatisticsManager`。
- 校验：rw-modding-mcp 的 lint（18 事件 / 27 逻辑函数 / 键表）已在仓库外可用，可作为校验规则来源。
- 台架：bench-mod（无头跑、可注入单位/触发条件）可当「局内预览」的驱动。

**关键限制：`IniFile` 只解析不写回**（解析时跳过 `#` 注释，只有 LinkedHashMap 值与 setValue，无序列化）。
所以模组编辑不能用 IniFile 存盘，必须另做无损文档模型。

## 3. 统一底座（三模式共用）

1. **Workspace**：工程根、最近打开、模式切换、任务队列。
2. **Document**：以文本为真源（行数组，保留注释/顺序/空行），旁边挂语义 AST 索引（section→key→value 位置）。
   所有编辑操作走「改动文本 → 重算受影响 AST 片段」，保证存盘字节只差用户改的那部分。
3. **事务/撤销**：命令栈（插入/替换/删除文本区间），粗粒度即可，先不做 CRDT。
4. **校验器**：可插拔 lint 管道，输出 文件/行号/级别/说明，点一下跳到问题处。
5. **预览器**：局外预览（资源缩略图、图标、贴图集）＋ 局内预览（起 headless/嵌入 Slick 一局，注入单位或改后的模组）。
6. **任务队列**：校验、构建、打包、局内预览都是后台任务，带进度与取消。
7. **诊断面板**：日志、错误、变更摘要。

## 4. 能力矩阵

| 能力 | Packaging | Client | Mod |
| --- | --- | --- | --- |
| 工作区/任务队列 | ✓ | ✓ | ✓ |
| 无损文档模型 | 清单/脚本 | toml | ini / mod-info |
| 校验器 | 清单完整性 | 键名与取值 | ini 语义 + 逻辑表达式 |
| 局内预览 | 无 | 生效预览 | ✓（核心） |
| 构建产物 | ✓（核心） | 导出配置 | 导出 .rwmod |

## 5. 第一阶段最小切片（P0，先走通一条线）

模组编辑模式里：**打开 mods/units 下某模组 → 单位列表 → 选中单位 → 编辑一个字段 → 校验 → 保存**，
保存后文件字节只差被改的那一行（注释、顺序、空行全保留）。

不含：撤销、局内预览、打包模式。

验收：拿 wcmodoperation 那个模组（1705 条 autoTrigger）当样本，
打开不卡（目标 < 1s 出结构树）、改一个字段存盘后 `lint` 无新增错误、diff 只有目标行。

## 6. 路线

- P0：底座 + 模组模式的只读浏览（结构树 + 文本视图 + 校验面板）。
- P1：字段编辑 + 保存（无损）+ 撤销；表达式编辑器（逻辑布尔高亮/补全）。
- P2：局内预览（headless 快照式）；客户端编辑模式的设置项。
- P3：打包模式（清单、差异、构建、CHANGED-FILES 生成）。

## 7. 与现有代码的衔接点

- `desktop/src/main/kotlin/io/github/rwx/DesktopMain.kt`：应用入口，挂新窗口/模式切换。
- `desktop/.../SwingAppHost.kt`、`ComposeTexturePanel.kt`：Compose 与游戏面板共存的方式，编辑器复用。
- `core/.../mod/ModManager.java`：模组扫描与启用状态；编辑器要读同一份扫描结果。
- `core/.../utility/IniFile.java`：只做语义参考，不用于存盘（会丢注释）。
- `core/.../units/custom/CustomUnitConfigParser.java`：字段语义与默认值的真源，表单据此生成。
- `bench-mod`：局内预览的驱动手段（注入单位/条件）。

## 8. 需要老师定的关键决策

1. 编辑器形态：**独立 Compose 窗口**（推荐，好做多面板/快捷键）还是游戏内覆盖层？
2. 文档真源：**文本为真源 + AST 缓存**（推荐，保注释、diff 干净）还是 AST 为真源再序列化（实现简单、会重排注释）？
3. 打包模式目标：沿用现在的 jpackage app-image + 整合包布局，还是定义新格式（zip + 清单）？
4. 局内预览做到什么程度：headless 截图式（快、不许交互）还是嵌入 Slick 实机跑（能交互、成本高）？
5. 编码：现有 mod ini 的编码需要实测确认（GBK / UTF-8 / 混合），编辑器读写要按实测来，不能猜。

## 9. 风险

- **编码**：中文 mod 名/文本在日志里是乱码，存盘若按错编码会毁文件 → 读写前先探测并保留 BOM/换行风格。
- **解析性能**：大模组 unitParse 在 500～800ms 量级，结构树要增量解析，不能每次全量。
- **热重载**：改完 ini 能否不重启游戏生效，决定预览体验；需先确认 ModManager 是否支持 reload。
- **写盘安全**：所有保存走「备份 + 临时文件 + 原子替换」，禁止直接覆盖。
- **范围蔓延**：三种模式全做会非常大，P0 只碰一条线，其余先留接口。

## 10. 补充：JVM 模组带来的变化（勘察 my-first-mod-1.0.0.jar 之后）

样例 jar 7.7MB，内容：`mod.toml`（id / entrypoint / name / version / minGameVersion）、
编译后的 Kotlin 类（含 `ComposableSingletons`、`HudSnapshot`）、`assets/`（mod-info.txt、build.json、music、maps、模板）。
mod-api 44 个 kt，模组可用的面：`game`（tick/调度/队伍/观察者）、`units`（DSL 造单位 + rawSection 逃生口）、
`unitWorld`、`commands`、`maps`、`rules`、`localization`、`audio`、`graphics`（含 `registerHud(id, order) { @Composable }`）、`ui`、`ai`。
**模组不再是纯数据，是可执行代码 + 资源 + 描述文件。**

### 10.1 模组两种形态（编辑器必须都认）

| 形态 | 工程内容 | 编辑器要做的 |
| --- | --- | --- |
| 数据模组 | `mods/units/<mod>/`：mod-info.txt、单位 ini、模板、贴图、音乐、地图 | ini 无损编辑、字段表单、模板展开、资源预览 |
| JVM 模组 | `mod.toml` + Kotlin/Java 源码 + gradle 工程 + 编译产物 jar + assets/ | 工程树、gradle 构建、API 补全、运行预览、HUD 预览、（可选）无代码生成代码 |

两者可以共存：JVM 模组照样带 `assets/mod-info.txt` 里的单位/地图/音乐段，
所以模组编辑模式要能「一个工程里既有 ini 又有代码」。

### 10.2 对编辑器底座的影响

- 工程模型要升级成 **Project**：不止一个目录，而是「描述文件 + 源集 + 资源集 + 构建脚本」。
- 需要构建集成：调用本机 gradle 编译模组（编辑器内嵌任务队列、输出到面板）。
- 需要运行/预览集成：起一局加载该模组，看单位、看 HUD 层（`registerHud` 支持多图层 order，可单独预览某一层）。
- 代码模组能执行任意逻辑 → 打开第三方模组工程时给出「会执行代码」的提示，预览运行前可要求确认。

### 10.3 打包模式的定义（老师这句是准绳）

> 打包模式就是把软件和模组合起来的模式。

所以打包模式的产物 = **客户端（引擎 jar / app-image / runtime）+ 选定模组（含 JVM jar 与 assets）+ 清单**，
要处理的事情：

1. 选择包含哪些模组、加载顺序、启用状态（写进整合包的 preferences/modSettings）。
2. 资源合并与冲突：同名文件、同 id 模组、同动画标签、同贴图路径 —— 冲突要报出来让人选。
3. **类冲突**：样例 jar 7.7MB，很可能自带了 Compose 运行时；多模组各带一份时靠 per-mod classLoader 隔离，
   但共享类型（mod-api、compose runtime）必须只有一份 —— 打包时要检测并裁剪重复依赖。
4. 清单与校验：文件清单、sha256、版本号、更新链接（样例 build.json 里就有 updateLink/updateType 字段）。
5. 产物可验证：解包后能跑、能加载每个模组、CHANGED-FILES 自动生成。

### 10.4 修订后的 P0（最小切片）

原 P0 只做 ini 一条线，现在加一条：**打开 JVM 模组工程（只读）**——工程树看到 `mod.toml`、源码、assets，
并能改 `mod.toml` 的 name/version/description 并存盘。不构建、不运行。
理由：编辑器要同时服务两种形态，先把「工程模型」这个底座验证掉，比先做深 ini 表单更值。

### 10.5 待老师定的新问题

1. JVM 模组工程的构建由 IDE 管（内置 gradle 任务）还是「只打开、构建走命令行」？
2. 打包时模组自带的 Compose/依赖怎么处理：强制剥离（用宿主的一份）还是允许带（隔离加载）？
3. 无代码路径：单位表单是生成 **DSL 代码** 还是生成 **rawSection ini**？（样例里 rawSection 与 DSL 并存）
