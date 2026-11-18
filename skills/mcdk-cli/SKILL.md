---
name: mcdk-cli
description: Use MCDK's command-line interface to launch and drive a NetEase Minecraft Bedrock (网易我的世界中国版 / ModPC) development client — start a session, read logs incrementally, hot-reload Python scripts, call in-game tools (reload_game, capture_game_window, mc_input, mc_profiler, jsonui_debugger), and stop the game. Load this whenever the task involves starting/debugging the game via MCDK (mcdk run/logs/call/wait/stop), finding the mcdk.exe path for a project, or interacting with a running game session without configuring an external MCP client.
---

# MCDK CLI（网易 MC 开发端命令行驱动）

用 MCDK 自带的 `mcdk.exe` 启动和控制网易我的世界中国版（ModPC）开发端。**不要**把外部 MCP 客户端当作与游戏交互的入口——CLI 的 `logs` / `call` 已经够用，且它是会话记录驱动的。`mcdk.exe` 是 Windows x64 程序；本技能假设 Windows + PowerShell。

> 术语澄清：这里说的「不用 MCP」指 **agent 不需要配置任何外部 MCP 客户端**。`mcdk logs` / `mcdk call` 走的是 MCDK **内部**的 MCP 服务，这个服务必须保持开启，CLI 才能读到日志和调用游戏内工具。不要为了「不用 MCP」去关闭 `mcp_server_config.enabled`。

## 一、找到 mcdk.exe（必须先做）

按顺序尝试，命中即停，并在心里记住实际路径（后续每条命令都要用它）：

1. **项目配置**：从当前目录向上找最近的 `.git`（找不到就用 cwd）作为项目根 `<project>`，读取 `<project>/.mcdev.json` 的顶层 `mcdk_path` 字段。MC Studio Helper 插件启动过一次后会自动写入它，值是可直接执行的绝对路径（`/` 分隔）。
2. **字段缺失或文件不存在**时依次回退：
   - 项目里 IDE 运行配置填写的 mcdk 路径（若可见）；
   - 插件缓存：`<本地应用数据>/mcs-helper/mcdk-1.6.5/mcdk.exe`（IntelliJ `PathManager.getPluginTempPath()` 下，常见在 `%LOCALAPPDATA%\Temp\` 之类目录，可用 `Get-ChildItem <temp> -Recurse -Filter mcdk.exe` 定位）；
   - VS Code 扩展：`$env:USERPROFILE\.vscode\extensions\dofes.mcdev-tools*\bin\native\windows\x64\mcdk.exe`；
   - PATH：`Get-Command mcdk -ErrorAction SilentlyContinue`；
   - 让用户提供路径。
3. 校验：`Test-Path <path>` 且文件名是 `mcdk.exe`。若路径含空格或中文，调用时一律 `& "<完整路径>" ...`。

详情与 `.mcdev.json` 其余字段见 `references/mcdev-json.md`。

## 二、黄金工作流（启动 → 调试 → 停止）

```powershell
$mc = "<上一步找到的 mcdk.exe 绝对路径>"
$proj = "<项目根，含 .mcdev.json 的目录>"
$s = "dev"   # 会话名，字母/数字/_/-，不超过 64 字符

& $mc ps --project $proj                       # 先看有没有现成会话
& $mc run --project $proj --session $s --detach --reuse   # 有相同配置就复用；不要反复重启
& $mc wait --project $proj --session $s        # 阻塞到玩家进入存档（默认 120s）

& $mc logs --project $proj --session $s        # 看最新 50 行，末行 [logs] next=N 记下游标 N
# 改完代码：
& $mc call reload_scripts --project $proj --session $s
& $mc logs --project $proj --session $s --since N --errors   # 只看新增的错误

# 整轮工作结束后：
& $mc stop --project $proj --session $s
```

要点：

- **不要每个小测试都重启游戏**：用 `mcdk run --detach --reuse` 启动一次，之后靠 `call reload_scripts` 迭代。`--hot-reload`（保存文件自动热更）默认关闭，通常不需要打开——它会让同工程多个会话一起更新、模块重复执行。
- **日志用游标增量读**：先 `mcdk logs` 拿 `[logs] next=N`，后续用 `--since N`。加 `--errors`、`--grep`、`--regex`、`--limit` 过滤。一次会话的日志会增长到很大，不要整段读。`--json` 输出结构化 `{lines, next, skipped, more}`；`skipped>0` 表示游标后的部分行已被挤出内存缓冲，`more=true` 表示达到 `--limit`，要用新游标继续读。
- **等待某行出现**：`mcdk logs --wait-for <正则> --timeout <秒> [--since N]`。要避免漏掉触发前的输出，先把触发前的游标传给 `--since`。
- **脚本重载互相看到新旧版本**而报错时，不要往代码里加 `getattr`/`try` 兜底；改成重载存档：记下 `mcdk ps` 里的 `readyCount=R`，执行 `& $mc call reload_game ...`，再 `& $mc wait --after R ...`。
- **停止**：只结束本次会话用 `mcdk stop --session <s>`；`--all` 会结束本项目全部会话，慎用。
- **会话名可省略**当且仅当项目只有一个运行中的会话；多会话必须显式 `--session`。

完整命令选项、退出码、Lane 语义见 `references/mcdk-cli-reference.md`。

## 三、调用游戏内工具（mcdk call）

```powershell
& $mc call <tool> --project $proj --session $s --args '{...json...}'
& $mc help <tool>              # 查看某工具的完整参数
```

常用工具：

| 工具 | 用途 |
|---|---|
| `reload_scripts` | 同步重载改过的 py，返回重载/失败模块；改 Python 代码后用 |
| `reload_game` | 重载整个游戏/存档；配合 `wait --after <readyCount>` |
| `read_logs` / `get_latest_logs` / `get_latest_error_logs` / `get_log_range` | 读日志；优先 `read_logs`（游标增量），CLI 的 `mcdk logs` 更方便 |
| `execute_code` | 在游戏环境里执行代码 |
| `jsonui_debugger` | 只读分析 JSON UI 运行时状态，`--args '{ "cmd": "/help" }'` 看命令 |
| `capture_game_window` | 截图；用 `--image out.jpg` 存文件，谨慎使用（较重） |
| `mc_input` | 键鼠输入；`--args '{ "cmd": ... }'`，先 `/help`、`/state` |
| `mc_profiler` | Python CPU/内存/Native 性能分析；先 `/help` |

每个工具的参数细节见 `references/mcdk-tools.md`，不确定时**先跑 `mcdk help <tool>`**，不要凭记忆构造 `--args`。`--timeout`（默认 60s）按工具耗时调整；脚本化时用 `--json` / `--output` 和退出码判断结果。

## 四、PowerShell 调用约定与坑

- 路径/参数含空格或中文一律加引号：`& $mc run --set "world_name=冒烟测试"`。
- 每条命令都应设超时（工具的 `--timeout`、`wait` 的 `--timeout`），脚本里逐步检查 `$LASTEXITCODE`。
- 退出码：`0` 成功；`2` 用法错误（看 stderr 指向的 help）；`3` 配置错误/找不到会话；`4` 启动失败（如 MCP 端口被占用）；`5` 游戏非 `mcdk stop` 结束；`6` 等待超时；`7` 工具调用返回错误。
- 多步、重复或需要判断结果的测试，写 Python/PowerShell 脚本把命令串起来，放工程目录，每步设超时。
- **`--background` 模式**：游戏窗口不激活、不抢鼠标；此模式 `mc_input` 只能 `delivery='post'`，转视角用 `turn` 步骤（按角度），不能用 `look`。
- **Lane 隔离**（`--session`）：数据/存档/日志在 `<project>/.mcdev/lanes/<会话名>`。删除用 `mcdk lane remove`，**绝不要手动删目录**——里面有指向真实游戏数据的目录联接，PowerShell `Remove-Item -Recurse` 会顺着联接删到真实存档。
- 无参 `mcdk` 是给编辑器用的交互式启动，可能重置存档；自动化永远显式用子命令。

## 五、参考文件（按需加载）

- `references/mcdk-cli-reference.md` — `help/run/lane/ps/games/wait/stop/logs/call` 完整选项、退出码、Lane 级别与生命周期。
- `references/mcdk-tools.md` — 所有 `mcdk call` 工具的用途、参数入口与注意事项。
- `references/mcdev-json.md` — `.mcdev.json` 字段（含 `mcdk_path`）、会话记录、Lane 目录结构、与插件/MCP 的关系。
