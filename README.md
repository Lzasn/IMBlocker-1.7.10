# IMBlocker — Minecraft 1.7.10 / Forge（GTNH）反向移植版

把 [IMBlocker](https://github.com/mrjesen/ImBlockerFabric)（原作者 mrjesen，Apache-2.0）的核心行为搬到 1.7.10：
**不输入文字的时候把输入法摘掉，点到文本框的那一刻再装回去。**

原版 1.7.10 里输入法一直挂在游戏窗口上，中文/日文/韩文输入法会吞掉 `W A S D`、`E`、`T` 这些按键，导致
角色不动、物品栏打不开。这个模组让键盘在“玩游戏”时保持纯英文直通，在“打字”时恢复输入法。

## 行为

| 场景 | 输入法状态 |
| --- | --- |
| 没有打开任何界面 | 摘除（按键直达游戏） |
| 打开物品栏 / 机器 GUI / 任务书 / 设置等无文本框界面 | 摘除 |
| 点击聊天栏、NEI 搜索框、GT 机器里的文本框、告示牌、书与笔 | 恢复 |
| 界面关闭 / 失去焦点 | 摘除 |
| 游戏窗口不在前台（Alt-Tab） | 完全不碰，不会影响别的程序 |

默认按 `F6` 可以手动翻转当前状态（可在“控制”里改键；配置里设 `toggleKeyCode = 0` 可关掉快捷键）。

## 原理

1. **平台层**：Windows 上用 JNA 调用 `imm32` / `user32`（随模组打包，无需另装）。
   - 默认策略 `detach`：`ImmAssociateContext(hwnd, NULL)` 把 IME 从游戏窗口上摘下来，原来的 HIMC 存起来，
     需要打字时再 `ImmAssociateContext(hwnd, 保存的 HIMC)` 装回去。这正是 IMBlocker 在新版本上的做法。
   - 备选策略 `openstatus`：不动关联关系，只 `ImmSetOpenStatus` 关闭/打开输入法，对少数“摘除也照样吃键”的
     输入法更温和。
   - 窗口句柄优先用 `GLFWNativeWin32.glfwGetWin32Window` 精确获取（GTNH 2.7+ 是 lwjgl3ify，即真正的
     LWJGL3/GLFW 窗口）；拿不到时退回 `GetForegroundWindow()` 并校验进程 ID，保证只动自己的窗口。
2. **焦点判定**：
   - Mixin 注入 `GuiTextField#setFocused(Z)V` —— 1.7.10 生态里几乎所有文本框（包括 NEI 的搜索框，它内部就包了一个
     `GuiTextField`、AE2 的 `MEGuiTextField`、任务书、GT 的输入框）最终都会走到这个方法，一条 hook 全覆盖。
   - 兜底再对当前界面做一次对象图扫描，找出没有继承 `GuiTextField` 的自制输入控件（鸭子类型：有 `isFocused()` 或
     `focused` 字段 + `getText()`），只在新界面打开时扫一次，之后每 tick 只做几次虚调用。
   - `alwaysImeScreens` 里预置了 `GuiChat`、`GuiSleepMP`、`GuiEditSign`、`GuiScreenBook`，这些界面本身没有
     `GuiTextField` 但确实需要打字。
3. **每 tick**（20Hz）重新判断一次，只在状态翻转时才真正调用系统 API，避免刷屏式调用。

## 安装

把构建出的 `imblocker-<version>.jar` 丢进 `mods/`，需要 **UniMixins**（GTNH 自带）。仅客户端模组，服务器不用装。

## 配置

`config/imblocker.cfg`

```ini
general {
    # 总开关
    B:enabled=true

    # detach: 摘除 IME 上下文（推荐，IMBlocker 同款）
    # openstatus: 只关闭/打开输入法
    S:mode=detach

    # 每多少个 tick 检查一次焦点，1 = 50ms
    I:checkIntervalTicks=1

    # 切换快捷键的 LWJGL 键码，F6 = 64，0 = 关闭
    I:toggleKeyCode=64

    # 打印每次状态翻转，排查问题用
    B:debug=false

    # 没有 GuiTextField 但需要输入法的界面
    S:alwaysImeScreens <
        net.minecraft.client.gui.GuiChat
        net.minecraft.client.gui.GuiSleepMP
        net.minecraft.client.gui.inventory.GuiEditSign
        net.minecraft.client.gui.GuiScreenBook
     >

    # 额外当作输入框处理的类名（可写片段），比如某个模组自制搜索框
    S:extraTextInputClasses <
     >
}
```

某个模组的输入框打字时输入法没恢复？把 `debug=true` 打开，看日志里 `screen -> xxx` 的类名，然后：
先加进 `extraTextInputClasses`（如果它是自制控件），或者告诉我它的类名，我给它补一条适配。

## 构建

GTNH 的新构建链对 JDK 有点挑剔，先把两个坑说清楚：

- **跑 Gradle 必须 JDK 25**（仓库里 `gradle/gradle-daemon-jvm.properties` 写死 25，`gtnhgradle` 插件本身也要求 JVM ≥ 25）；
- **`compileApiJava` 源码集要 JDK 8 工具链**，Gradle 默认会自己去 foojay/GitHub 自动下载，国内直连基本必挂。

所以本地要备好 JDK 8 + 25，并显式告诉 Gradle 它们在哪（`<path>` 换成自己的路径）：

```bash
export JAVA_HOME=/path/to/jdk-25
export JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT \
  -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 \
  -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890"
./gradlew build \
  "-Dorg.gradle.java.installations.paths=/path/to/jdk8,/path/to/jdk-17,/path/to/jdk-25"
```

产物在 `build/libs/imblocker-<version>.jar`（分发用，已 reobf + 打包 JNA；`-dev.jar` 只给开发环境用）。
首次构建要拉 Forge 10.13.4.1614 + MCP stable_12 并反编译，约 5 分钟；之后增量构建几十秒。

## 兼容性 / 已知限制

- **只支持 Windows**。Linux（fcitx/ibus）和 macOS 上模组会加载但什么都不做。Linux 想用输入法得靠
  IngameIME / IMEInputBackport 这类走 XIM/IBus 的实现。
- 极少数纯 TSF 输入法（个别版本的搜狗/QQ 拼音）可能不理会“摘除上下文”，这时把 `mode` 改成 `openstatus` 试试。
- 1.7.10 本身不支持输入法**候选框跟随光标 / 内联预编辑**，这个模组不解决那部分——那是 IMEInputBackport /
  IngameIME 的活。两者同时装可能会抢 IME 状态，建议二选一。
- 表情符号等需要代理对的字符在 1.7.10 里依旧不可能，属于原版限制。
- 与 `InputFix` 类模组同用时，本模组在打字瞬间会把 IME 装回去，理论上不冲突，但没实测。

## 许可与致谢

- 行为与思路来自 IMBlocker（mrjesen，Apache-2.0），本仓库是针对 1.7.10 的独立实现。
- 本项目同样以 Apache-2.0 发布。
- 构建骨架来自 [GTNewHorizons/ExampleMod1.7.10](https://github.com/GTNewHorizons/ExampleMod1.7.10)。
