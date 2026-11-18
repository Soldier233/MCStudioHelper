# `.mcdev.json`、`mcdk_path` 与会话数据

## 项目根与文件位置

- **项目根 `<project>`**：包含 `.git` 的最近祖先目录；找不到时用 cwd。
- `<project>/.mcdev.json`：游戏配置（世界、组件、调试、MCP 等）+ 插件写入的 `mcdk_path`。
- `<project>/.mcdev/sessions/*.json`：MCDK 会话记录（CLI 的 `logs`/`call`/`stop` 靠它找会话和端口）。
- `<project>/.mcdev/lanes/<会话名>/`：Lane 隔离时的数据/存档/日志目录（默认）。

建议项目 `.gitignore` 加入 `.mcdev/`，避免误提交运行数据。

## `mcdk_path`（本技能新增的字段）

MC Studio Helper 插件启动过一次后，会把**本次实际使用的** `mcdk.exe` 绝对路径写入项目 `.mcdev.json` 的顶层 `mcdk_path` 字段，例如：

```json
{
  "mcdk_path": "D:/Source/MCStudioHelper/src/main/resources/bin/native/windows/x64/mcdk.exe",
  "game_executable_path": "",
  "included_mod_dirs": ["./"],
  "...": "其余字段"
}
```

要点：

- 值是**规范化绝对路径，用 `/` 分隔**，指向一个存在的 `.exe`，agent 可直接 `& "<path>"` 调用。
- 它是插件维护的元数据，**不是 MCDK 设置**：插件不会把它传给 MCDK 启动快照，也不参与世界/组件配置。
- 它不要求填：字段缺失、路径失效、或用了自定义 MCDK 时，agent 按 `SKILL.md` 的回退顺序自行探测。
- 该字段不出现在可视化编辑器的标准表单里（它是扩展字段，原样透传），可直接用 IDE 的 Text 编辑器查看/修改。

## 常见 `.mcdev.json` 字段

字段与默认值来自插件内置 `mcdev/defaults.json`（MCDK 1.6.5）；缺失字段用默认值。

| 字段 | 说明 |
|---|---|
| `mcdk_path` | 插件写入的 mcdk.exe 绝对路径（本技能新增，见上） |
| `game_executable_path` | 游戏程序路径（空值自动检测最新 ModPC）；正式服启动器不可用 |
| `included_mod_dirs` | 组件目录数组（相对路径以项目根为准），支持 `{path, enabled, hot_reload}` 对象 |
| `world_name` / `world_folder_name` | 世界显示名 / 存档目录名（后者必须 ASCII、无路径分隔符） |
| `world_source_path` | 地图源目录，`auto` 自动识别，空值禁用 |
| `world_seed` | 世界种子，null 为随机 |
| `reset_world` | 启动时重置世界 |
| `auto_join_game` | 自动进入游戏 |
| `world_type` / `game_mode` | 0/1/2：旧版有限/无限/超平坦；生存/创造/冒险 |
| `enable_cheats` / `keep_inventory` / `do_weather_cycle` / `do_daylight_cycle` | 游戏规则开关 |
| `pack_link_mode` | `global`（默认，全局包目录）或 `world`（存档内，可多开） |
| `log_protocol` | `0` PIPE（旧）/ `1` Safaia（推荐） |
| `auto_hot_reload_*` | 自动热更新 MOD/UI/Shader/Material/Particle |
| `debug_options.reload_key` 等 | 热重载按键，数字字符串，空串为不绑定；`reload_key_global` 全局生效 |
| `modpc_debugger` / `ptvsd_debugger` | 调试器开关与端口 |
| `experiment_options` / `window_style` / `netease_config` | 实验功能、窗口样式、网易配置 |
| `mcp_server_config.enabled` | MCDK 内部 MCP 服务，默认 `true`；CLI 的 `logs`/`call` 依赖它，**不要为了「不用 MCP」关掉** |
| `mcp_server_config.server_port` | 默认 `19133`，被占用时插件/CLI 回退到空闲端口并写入会话记录 |

`plugins` 是 MCDK 1.6.5 的 C++ 插件声明，不在可视化表单里，原样透传，用 Text 编辑器维护。

## 插件与 CLI 的关系

- 插件用 `mcdk run --project <项目> --config <快照> --hot-reload [--session <名>] --set mcp_server_config.enabled=true --set mcp_server_config.server_port=<实际端口>` 启动，前台输出游戏日志。
- 同一启动同时在 `<project>/.mcdev/sessions/` 写会话记录，因此可以在项目目录（或任意 cwd，只要传 `--project`）用 CLI 驱动同一次运行。
- `--project` 让 MCDK 把会话记录写到项目下，而启动配置快照在临时目录，项目文件不被改写（唯一例外是插件写入的 `mcdk_path`）。

## 多开

- 每个实例用不同 `--session`，Lane 默认在 `.mcdev/lanes/<会话名>`。
- `pack_link_mode=global` 时主客户端会重写全局包目录，已有游戏要先关；多开用 `pack_link_mode=world`（存档内链接），且各实例用不同 `world_folder_name`。
- MCP 端口写进会话记录，CLI 自己按记录连，不需要手填端口；固定端口就在各项目 `.mcdev.json` 写不同的 `mcp_server_config.server_port`。
