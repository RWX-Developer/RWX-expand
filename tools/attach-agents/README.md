# RWX 附加测试代理（attach）

这些是挂进**正在运行的**游戏 JVM 的小工具，用来在不重启、不依赖窗口焦点的情况下观察/驱动游戏。

## 怎么用

```bash
J=/d/Game/tools/zulu25.38.109-ca-jdk25.0.5.0.3-win_x64
export PATH="$J/bin:$PATH"
cd tools/attach-agents
javac -cp "/d/Game/RWX-整合包/RWX-windows-x64-1.1.4.jar" -d . XxxAgent.java
jar cfm xxx.jar MANIFESTx.MF XxxAgent.class
PID=$(jps -l | grep DesktopMain | awk '{print $1}' | head -1)
java -cp . AttachT $PID "C:/.../xxx.jar" 参数
```

注意：attach 的 jar 参数必须给**绝对路径**，相对路径会报 “Agent JAR not found”。
每个 JVM 只认第一次加载的 agent 类名，改代码后要换新类名（TouchAgent2/3/4 就是这么来的）。

## 已跑通的两条注入路线

| 目标 | 路线 | 实测 |
| --- | --- | --- |
| 游戏世界（相机/选择） | `RwxInputBridge.press/release` → `MultiTouchPointerState.processEvent` | probe 显示 isDown=true/numPointers=1 保持了 500ms |
| 菜单 UI（Compose 覆盖层） | `EventQueue.postEvent` 投给 `SwingSkiaLayerComponent$hierarchyRoot$1` | 主菜单「单人游戏」点进去了，进到地图选择页 |

菜单和世界是两套输入：菜单走 AWT 事件 → Compose，世界走 Slick 指针状态。只改后者点不动菜单。

## 截图

`RwxInputBridge.screenshot()` 等价按 F12，写到 `<游戏目录>/screenshots/`，
不依赖窗口焦点——游戏在浏览器后面也能拍到真实一帧。
