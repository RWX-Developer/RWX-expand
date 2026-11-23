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

## 2026-11-23 走完整条流程的结论

一次成功的路径（全部走进程内注入，不碰桌面鼠标）：

1. `click 875 491` → 单人游戏（AWT 事件投给 Compose 的 `hierarchyRoot$1`）
2. `click 256 281` → 选中 Beach landing（同一路径）
3. 作战房间：游戏循环按设计停摆（`EmbeddedSlickGameContainer.start()` 第 84-87 行，
   画布不可见就 `skipFrameUntilCanvasReady()` 空转），因此引擎截图与 tick 都停了。
   用 `dump`（在 AWT 线程上 `printAll` 到 BufferedImage）才看得到房间界面。
4. `click 1723 1054` → 房间右下角绿色 ▶，对局开始：日志 "Adopting started battle room"，
   tick 1742 → 2733，截图恢复。
5. 局内：注入的指针等价触屏。单指拖拽会转成相机平移；在单位上点一下（press 60ms + release）
   probe 显示 `wasDown=true`，但没有选中框，`RWX_ORDER` 也没增加（172 → 172）。

### 还没打通的一环

局内的点选/下令要走**入队**的输入路径，不是直接写 pointerState：
`SlickGame.mousePressed` → `enqueueInputEvent` → 循环里 `applyPendingInputEvents` → `pointerState.processEvent`。
现成的入口是 `SlickGame.submitOverlayPointer(x, y, down, pointerId)`，但没有静态拿 SlickGame 的地方
（`SlickCanvasHost` 只存 uiFrame/外观分发器）。要么在源码里加一个静态桥，要么继续找持有者。
