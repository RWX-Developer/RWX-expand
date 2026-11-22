# RWX 编辑器（独立程序）

图形化编辑 RWX 模组的桌面小程序。**不启动游戏、不依赖引擎**，只读写工作区里的模组文件。

产物是一个单 JAR：`RWX-Editor-windows-x64-<版本>.jar`，双击或 `javaw -jar` 启动。

## 能做什

- **打开模组**：`.jar` / `.rwmod` / `.zip` 归档，或目录形式的模组，就地读写
- **按节拆模块**：一个 ini 的 `[section]` 就是一张卡片，可增可删，值直接改（只替换值本身，注释和键名原样保留）
- **读 kt / toml**：Kotlin 显示顶层声明；TOML 按 `=` 分节，顶层键也能改
- **JVM 代码**：`.class` 显示类名 / 父类 / 接口 / 方法表 / 引用的平台 API；`classes.dex` 显示版本、类与类型统计、顶层包分布、类清单
- **反编译**：`.class` 一键出 Java 源码（内置 CFR 0.152），文本页选中 class 会自动跑
- **二进制**：不当文本硬解，给十六进制预览
- **打包**：目录模组一键打成 jar；归档模组保存时写回原包，并留 `.bak`
- **画布**：`turret_*` 的 x/y 拖一下就改

## 命令行

```bash
# 指定工作区打开
java -jar RWX-Editor-windows-x64-0.1.0.jar <工作区目录>

# 直接跳到一个文件（.class 会自动进文本页并反编译）
java -jar RWX-Editor-windows-x64-0.1.0.jar <工作区> --entry bench/mod/BenchRampMod.class

# 无界面：读包 / ini 分节 / 打包回环自检
java -jar RWX-Editor-windows-x64-0.1.0.jar --selftest <模组jar>

# 无界面：反编译某个条目
java -jar RWX-Editor-windows-x64-0.1.0.jar --decompile <模组jar> [条目名]
```

## 构建

需要 Java 25 与仓库根目录的 Gradle：

```bash
./gradlew :editor:packageUberJarForCurrentOS
# 产物：editor/build/compose/jars/RWX-Editor-windows-x64-0.1.0.jar
```

## 依赖说明

- `editor/src/main/resources/cfr.jar` 是 [CFR 0.152](https://github.com/leibnitz27/cfr) 反编译器的原样副本，运行时解到临时目录、以子进程调用（CFR 出错会 exit，放进程内会带崩编辑器）。它保留自己的许可证，与本仓库许可证分开看。
- 反编译走子进程、60 秒超时，产物写到系统临时目录。

## 已知边界

- 只会**读** dex 结构，不会反编译 dex（那要 jadx）
- `assets/` 里放 `.rwmod` 会当二进制整体打包，需要先解包
- TOML 多行数组（`values = [...]`）会渲染成一个键加几个孤儿行
- 目录模组打包前会调用 `ensureModToml`，entrypoint 目前写死为 `com.example.mymod.MyFirstMod`，接真项目前要参数化
