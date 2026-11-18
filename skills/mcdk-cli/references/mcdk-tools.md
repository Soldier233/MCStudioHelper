# `mcdk call` 游戏内工具参考

工具列表来自 `mcdk --help` 实测（MCDK v1.6.5）。调用格式：

```powershell
& $mc call <tool> --project $proj --session $s --args '{...}' [--timeout 60]
& $mc help <tool>              # 查某工具的完整参数（优先于凭记忆构造 --args）
```

`--args` 必须是 JSON 对象。`mc_input` / `mc_profiler` 这类工具采用 `op:'/...'` + `args:{...}` 的嵌套结构，务必先 `/help`。

---

## reload_scripts（改 Python 后首选）

> Synchronously reloads the Python modules whose source files changed since the last reload, and reports which modules were reloaded and which failed (with tracebacks).

同步重载自上次以来源文件改动的 Python 模块，返回成功重载与失败的模块（含 traceback）。改完 py 用这个，不要重启游戏。

自动热更新默认关闭（`run` 不加 `--hot-reload`），所以每次改完代码要显式调用。返回里若有失败模块，先看 traceback 修代码再重载。

## reload_game（多模块重载失败时用）

> Reloads the running game environment. For Python changes try reload_scripts first. Reload the game when many modules changed together and in-place reload fails because of reload order, or when a full engine-side refresh is needed. The player leaves and re-enters the world.

重载整个游戏环境：玩家会离开并重新进入存档。CLI 中配合 `mcdk wait --after <readyCount>`：

```powershell
# 先记下 mcdk ps 里的 readyCount=R
& $mc call reload_game --project $proj --session $s
& $mc wait --after R --project $proj --session $s
```

一次改了很多 py 时，模块逐个重载可能读到彼此的新旧版本而报错——这不是代码问题，此时重载存档，不要往代码里加 `getattr`/`try` 兜底。

## read_logs / get_latest_logs / get_log_range / get_latest_error_logs（日志）

- `read_logs`：游标 + 可选过滤增量读日志。**优先于 `get_latest_logs`**：长会话日志远超值得读取的量，只读新行、只匹配需要的行。CLI 的 `mcdk logs` 就是它的便捷封装，日常用 `mcdk logs` 即可。
- `get_latest_logs`：返回最近若干条日志。
- `get_log_range`：按索引返回指定范围的最近日志。
- `get_latest_error_logs`：只读 stderr 错误日志。可能不全（如 JSON 解析错误不在此），完整日志用 `get_latest_logs`。

## execute_code

> Executes provided code in the game environment.

在游戏环境里执行给定代码。参数格式先 `mcdk help execute_code`。

## jsonui_debugger

> Analyze native Minecraft JSON UI runtime state through a command string. Pass the command in the `cmd` argument; use `cmd="/help"` to list available commands and usage.

只读分析 JSON UI 运行时状态，支持屏幕列表、节点查找、浅层树、layout 快照、基于运行时布局/渲染数据的 HTML-like 伪输出（仅供布局参考）。默认只读且有 depth/node 限制避免大转储。

```powershell
& $mc call jsonui_debugger --project $proj --session $s --args '{ "cmd": "/help" }'
```

**UI 结构/布局/节点可见性/JSON UI 校验优先用它，而不是截图。**

## capture_game_window（截图，谨慎）

> Captures the Minecraft game window client area as a JPEG screenshot and returns base64-encoded image data. Defaults to the whole client area at 480p.

把图片解码存文件：加 `--image out.jpg`。这是较重的视觉检查手段，可能打断基于代码/日志的调试。UI 结构问题先用 `jsonui_debugger`；确定性问题先用日志和文件检查；只有明确需要视觉确认或日志无法回答时才截图。

## mc_input（键鼠输入）

> Drives the Minecraft game window through keyboard and mouse input. One call can run a whole ordered sequence: clicks, long presses, drags, wheel, camera motion, text and waits. Use `capture='end'` and `logs='end'` to attach a screenshot and recent logs. Call `/help` first; `/state` reports window geometry and whether the game holds the pointer.

输入采用 `{op:'/...', args:{...}}`。坐标默认与 `capture_game_window` 共用的 0.0–1.0 百分比空间。

- 先 `mc_input --args '{ "op": "/help" }'`，再 `/state` 看窗口几何和指针状态。
- `delivery='post'` 直接把输入投到游戏窗口，不抢前台/真鼠标，可多实例并存；此模式**不能用 `look`**，转视角用 `turn` 步骤（按角度）。
- 成功只表示输入已投递到系统队列，不代表游戏已响应——用 `capture_game_window` 或日志验证。

**`--background` 模式下只允许 post。**

## mc_profiler（性能分析）

> Profiles Minecraft Python CPU, Python memory, and optional Native CPU through one bounded command tool. Native profiles can correlate instrumented Python-facing and engine C++ Tracy zone hierarchies. Call `/help` first.

一次有界的命令式分析。每次采集有服务端 deadline；临时内存结果 20 分钟空闲后过期，Markdown/SVG 报告需显式导出。结果过滤分页，同类采集可做有界对比。输入同样是 `{op:'/...', args:{...}}`，先 `/help`。

---

## 不确定时

任何工具参数拿不准，先跑：

```powershell
& $mc help <tool>
```

或用工具的 `/help` 操作（如 `mc_input --args '{ "op": "/help" }'`、`jsonui_debugger --args '{ "cmd": "/help" }'`）。
