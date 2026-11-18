# MCDK CLI 完整参考

来源：内置 MCDK v1.6.5 的 `mcdk --help` / `mcdk help <子命令>` 实测输出。所有子命令均支持 `-h, --help`。调用格式：

```powershell
& "<mcdk.exe 绝对路径>" <SUBCOMMAND> [OPTIONS]
```

`--project DIR` 几乎所有子命令都有：项目目录，相对路径都以它为基准，默认当前目录。强烈建议每次都显式传，这样 `mcdk logs` / `call` / `stop` 才能找到 `<project>/.mcdev/sessions/` 里的会话记录。

---

## run — 启动游戏

```powershell
mcdk run [OPTIONS]
```

全程不询问、不改配置文件。

| 选项 | 说明 |
|---|---|
| `--project DIR` | 项目目录，相对路径基准（默认 cwd） |
| `--config FILE` | 配置文件（默认 `<project>/.mcdev.json`） |
| `--set KEY=VALUE ...` | 覆写配置项，可重复，如 `--set "world_name=冒烟测试"` |
| `--session NAME` | 隔离会话名：独立的包/存档/MCP 端口，可多开。仅 `[A-Za-z0-9_-]`，≤64 字符 |
| `--detach` | 后台运行，游戏进程创建后打印会话信息并返回 |
| `--reuse` | 同名会话已在运行且配置/参数相同时直接复用并返回；不同则报错 |
| `--lane LEVEL` | Lane 级别：`addons`、`worlds`（默认）、`full`，需要 `--session` |
| `--lane-root DIR` | Lane 目录，默认 `<project>/.mcdev/lanes/<会话名>` |
| `--background` | 后台模式：窗口不激活、不抢前台/鼠标；`mc_input` 只能 post，转视角用 `turn` |
| `--hot-reload` | 保存文件即自动热更新。默认关闭：改完代码用 `call reload_scripts` 重载并拿到结果 |

**Lane 级别怎么选**

- `worlds`（默认）：包、存档、日志独享，各会话可用同名存档互不冲突；游戏里改的设置会写回真实设置。
- `full`：整个数据目录独享，从空白/默认设置开始，完全干净可复现；测试会改设置时用。
- `addons`：只独享包目录；存档/设置/日志用联接共享真实数据，存档名带 `__<会话名>` 后缀；只有测试必须用真实存档时才用，别在游戏里删存档。

`--session` 会在游戏入口注入 `mcdev-lane-shim.dll` 把 AppData 指向 Lane（不是沙盒，游戏仍能访问其他路径）；shim 没装上时游戏会被直接结束，不会在未隔离状态下运行。

**生命周期**：`run` 创建或沿用 Lane；`stop` 只结束进程，Lane/存档保留，下次同名 `run` 接着用；`mcdk lane remove` 删除 Lane 及数据；`mcdk ps --prune` 清理已结束会话记录。MCDK 不会自动删除任何数据。

创建 Lane 时默认生成 `.mcdev/lanes/.gitignore`。删除 Lane 必须用 `mcdk lane remove`，不要手动删除——里面有目录联接。

---

## ps — 列出运行中的会话

```powershell
mcdk ps [--project DIR] [--all] [--prune]
```

- `--all`：也列出已结束的会话。
- `--prune`：删除已结束会话的记录和日志（Lane 还在的会话保留记录，先 `lane remove`）。

会话状态：

| 状态 | 含义 |
|---|---|
| `process_started` | 游戏进程已创建，还没进入存档 |
| `game_ready` | 已进入存档，工具可用 |
| `game_unavailable` | 不在存档内，或没有包含调试 MOD |
| `exited` | 已退出，见 `exitCode`；`stopped=true` 表示由 `mcdk stop` 结束（此时 `exitCode=1296254027 = 0x4D43444B = "MCDK"`） |
| `lost` | mcdk 进程已不在，但记录没写完 |

`readyCount` 是进入存档的次数，`reload_game` 后加一；配合 `mcdk wait --after <N>` 使用。

---

## wait — 等待会话进入存档或退出

```powershell
mcdk wait [--project DIR] [--session NAME] [--state ready|exited] [--timeout 120] [--after N]
```

- `--state`（默认 `ready`）：目标状态。
- `--after N`：等到 `readyCount > N` 的那次进入存档。重载存档前记下 `mcdk ps` 的 `readyCount`，传给它就不会把重载前的状态当成已就绪。

---

## logs — 按游标增量读日志

```powershell
mcdk logs [--project DIR] [--session NAME]
          [--since N] [--limit 1..500] [--grep TEXT] [--regex REGEX]
          [-i|--ignore-case] [--errors] [--json]
          [--wait-for REGEX] [--timeout 60]
```

- 不带 `--since` 时输出最新 `--limit`（默认 50）行，用来先看一眼并拿游标；末行 `[logs] next=N`。
- `--since N`：只读上次游标之后的新行。
- `--grep` / `--regex`（`-i` 忽略大小写）：只保留匹配行；regex 为 ECMAScript 语法。
- `--errors`：只读 stderr，它有自己的游标；可能不含全部错误（如 JSON 解析错误），完整日志用默认模式。
- `--json`：输出 `{lines:[{seq,text}], next, skipped, more}`。
  - `skipped=K`：游标后有 K 行在读取前已被挤出内存缓冲（约保留最近 1000 行，`reload_game` 会清空）。
  - `more=true`：达到 `--limit`，用新游标继续读。
- `--wait-for REGEX`：阻塞到出现匹配行，输出后返回；不带 `--since` 时只看命令开始之后的行。要避免漏掉先前触发的输出，先记下触发前的游标再传 `--since`。
- 游标比当前序号还大时视为上一次运行留下的，从头读起。

**推荐节奏**：`logs`（拿 N）→ 改代码/触发动作 → `logs --since N --errors`（看新增错误）。长时间运行的日志很大，不要整段读。

---

## call — 调用运行中会话的工具

```powershell
mcdk call [--project DIR] [--session NAME] <tool>
          [--args JSON] [--args-file FILE] [--output FILE] [--image FILE] [--timeout 60]
```

- `<tool>` 必填；工具列表见 `mcdk --help` 末尾或 `references/mcdk-tools.md`。
- `--args JSON`：工具参数，JSON 对象；`--args-file` 从文件读。
- `--output FILE`：完整结果 JSON 写文件而非 stdout；`--image FILE`：把结果中的图片解码保存（截图用 `.jpg`）。
- `--timeout`：默认 60 秒。

不确定参数时先 `& $mc help <tool>`。工具自身的参数校验失败会返回退出码 7 并在 stderr 写明原因。

---

## stop — 结束会话

```powershell
mcdk stop [--project DIR] [--session NAME] [--all] [--timeout 30]
```

结束会话的游戏进程并等待 mcdk 退出。`--all` 结束本项目全部运行中的会话，慎用。

---

## lane — 管理 Lane 目录

```powershell
mcdk lane remove ...
```

删除 Lane：联接只摘链接，不会删到真实游戏数据。具体选项跑 `mcdk help lane remove`。**永远用这个而不是手动删目录。**

---

## games — 列出本机安装的 ModPC 版本

```powershell
mcdk games [--project DIR]
```

从各磁盘根目录下的 `MCStudioDownload\game\MinecraftPE_Netease\<版本>\Minecraft.Windows.exe` 查找，新版本在前。`configured` 是 `.mcdev.json` 的 `game_executable_path`，`configuredExists` 表示它是否存在。`run` 时配置路径无效会改用最新版本（只影响本次）；指定版本用 `mcdk run --set game_executable_path=<path>`。

---

## help — 查看命令或工具说明

```powershell
mcdk help            # 列出全部子命令与工具
mcdk help run        # 某子命令的完整选项
mcdk help <tool>     # 某工具的完整参数
```

---

## 退出码（所有子命令通用）

| 码 | 含义 |
|---|---|
| 0 | 成功 |
| 2 | 用法错误：命令、参数、工具名或工具顶层参数写错，stderr 会指出对应 help |
| 3 | 配置错误，或找不到可用的会话 |
| 4 | 启动失败（含 MCP 端口被占用、被插件否决） |
| 5 | 游戏不是由 `mcdk stop` 结束的（自行退出、窗口被关闭或崩溃） |
| 6 | 等待超时 |
| 7 | 工具调用返回错误，包括工具自身校验参数失败，原因同时写到 stderr |
