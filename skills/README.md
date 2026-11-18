# MCDK CLI skill（安装说明）

这个目录是 DSH skill 源码，**不会自动安装**。它让 agent 从项目的 `.mcdev.json` 找到 `mcdk.exe`，然后用 MCDK 命令行启动/控制网易我的世界中国版开发端——读日志、热重载、调用游戏内工具（截图/输入/性能分析）、停止游戏，无需配置外部 MCP 客户端。

## 内容

```
mcdk-cli/
├── SKILL.md                       # 路由与黄金工作流（加载 skill 时读这个）
└── references/
    ├── mcdk-cli-reference.md      # 所有子命令的完整选项、退出码、Lane 语义
    ├── mcdk-tools.md              # mcdk call 的游戏内工具
    └── mcdev-json.md              # .mcdev.json 字段、mcdk_path、会话数据、多开
```

## 安装

DSH 的本地 skill 发现根（见 DSH 文档 `docs/subsystems/skills.zh.md`）：

| 范围 | 位置 |
|---|---|
| 项目级（随代码走） | `<git 根>/.dsh/skills/mcdk-cli/` |
| 项目级（agents 约定） | `<git 根>/.agents/skills/mcdk-cli/` |
| 用户级 | `~/.dsh/skills/mcdk-cli/` |

任选其一，把整个 `mcdk-cli/` 目录复制过去即可，例如：

```powershell
# 对当前 mod 工程生效，随工程提交给团队
New-Item -ItemType Directory -Force "<你的工程>\.dsh\skills" | Out-Null
Copy-Item -Recurse "<本仓库>\skills\mcdk-cli" "<你的工程>\.dsh\skills\mcdk-cli"

# 或对所有工程生效
Copy-Item -Recurse "<本仓库>\skills\mcdk-cli" "$env:USERPROFILE\.dsh\skills\mcdk-cli"
```

重启会话（或等待 watcher）后，`mcdk-cli` 会出现在可用 skill 列表中。触发词：`mcdk`、`开发端`、`读日志`、`热重载`、`reload_scripts`、`启动游戏` 等。

## 前提

- Windows + PowerShell；`mcdk.exe` 是 win32 x64 程序。
- 项目里已有 `.mcdev.json`（插件「生成默认配置」可创建）。
- 用 MC Studio Helper 插件启动过一次，`.mcdev.json` 里就有了 `mcdk_path`；否则 skill 会按已知位置回退探测，或让你提供路径。

## 关于 MCP

skill 让 agent **不用配置外部 MCP 客户端**，但 MCDK 自身的 MCP 服务必须保持开启——`mcdk logs` / `mcdk call` 正是通过它读日志和调用工具的。默认 `mcp_server_config.enabled = true`，不要关。
