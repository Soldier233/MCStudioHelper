# MC Studio Helper 插件食用指北

**环境要求：** PyCharm 2025.1 及以上版本  
**断点调试额外要求：** ModPC 开发包 3.7.0.222545 及以上版本 + LSP4IJ 插件（随本插件自动安装）

---

## 一、插件安装

前往 **PyCharm 插件市场**，搜索 `MC Studio Helper` 并安装：

![插件市场搜索](./img/plugin-market-search.jpg)

安装完成后重启 PyCharm 即可。本插件会自动捆绑安装 **LSP4IJ** 插件，该插件用于后续的断点调试功能。

---

## 二、添加运行配置

1. 打开你的开发项目，点击右上角 **Debug 按钮旁的更多操作按钮**，进入配置管理界面：

   ![进入配置管理](./img/open-run-config.jpg)

2. 点击左上角的 **＋** 按钮，添加一个新的运行配置（该选项位置较靠下，需要向下滚动查找）：

   ![添加运行配置](./img/add-run-config.jpg)

3. 根据提示填写相应的配置信息：

   ![填写配置信息](./img/fill-config-info.png)

   - **mcdk 路径**：可留空，插件会使用内置的 MCDK（当前为 v1.6.5）
   - **日志等级**：选择控制台日志的详细程度
   - **客户端模式**：默认自动，可选择主客户端或额外客户端
   - **Debug 附加端口**：默认 `5678`，需与 LSP4IJ 附加配置一致
   - **CLI 会话名**：留空不隔离；填写后启用 Lane 隔离，数据与存档进入项目 `.mcdev/lanes/<会话名>`
   - 点击 **打开 .mcdev.json**，在文件编辑器中设置游戏程序、组件和世界参数

---

## 三、mcdev.json 设置

### 项目配置入口

直接打开项目根目录的 `.mcdev.json`，即可使用可视化编辑器；需要查看或编辑原始 JSON 时使用 IDE 自带的 `Text` 编辑器。Run 页面提供「打开 .mcdev.json」和「生成默认配置」入口；文件不存在时会生成包含全部内置默认字段的 JSON。已有文件及尚未保存的修改不会因打开或生成操作被覆盖。

游戏配置统一保存在项目根目录的 `.mcdev.json`，缺少的字段自动使用内置默认值。首次使用只需选择游戏程序，组件目录默认为当前项目。打开已有文件或切换编辑页不会修改文件；直接编辑某项只保存对应字段，其他字段和未知配置保持原值。

### 可视化与 JSON 编辑

可视化界面直接编辑，无需额外开关。启动与组件、玩家与世界、常用游戏规则默认展开；点击「高级设置」可编辑热更新、按键、窗口、皮肤、实验功能和调试服务。字段的 JSON 名称可通过悬停提示查看。

世界种子留空表示随机生成；窗口大小、位置留空表示不固定。组件目录支持增删及单独开关启用、热更新，也兼容带有额外字段的对象。世界设置提供「打开存档目录」和「存档移至回收站」操作。

`pack_link_mode` 决定组件包的链接位置：`global` 写入全局包目录（旧行为，默认），`world` 写入存档目录，多个实例同时运行时互不影响。`plugins` 是 MCDK 1.6.5 的 C++ 插件声明，不出现在可视化界面中，会原样透传，请在 `Text` 编辑器中维护。

IDE 的 `Text` 编辑器支持直接粘贴完整 JSON、语法高亮、撤销和重做。格式或值无效时，可视化编辑器会提示在 `Text` 中修正；两个编辑器使用同一个 IDE 文档，并遵循 IDE 的保存及自动保存行为。

快捷键内置 **101 项静态按键**，显示数值、KEY 名称和中文说明，支持鼠标负键码、自定义数字和「不绑定」。按键数据不需要联网。JSON 中按键仍保存为数字字符串，空字符串表示不绑定；`reload_key_global` 控制按键是否在所有游戏界面生效。

### Run 与多客户端

所有 Run 都读取当前项目 `.mcdev.json`。Run 仅保存启动器路径、控制台日志等级、客户端模式、Debug 端口和 CLI 会话名，游戏设置在项目配置中统一编辑。

- **自动**：没有游戏时部署世界并启动；已有游戏时作为额外客户端启动。
- **主客户端**：以 MCDK 自动化命令行（`mcdk run`）启动，部署项目配置的世界和组件。`pack_link_mode` 为 `global`（默认）时已有游戏运行需先关闭，避免重新部署共享组件影响它；设为 `world` 时组件链接写在本次存档内，可在其他实例运行期间启动（各实例应使用不同的 `world_folder_name`）。
- **额外客户端**：直接启动另一个游戏客户端，适合联机测试；MCDK v1.6.5 此模式不创建 / 更新世界或重新加载组件包，需要在游戏中加入已有世界。

更换世界和组件设置后，先关闭原主客户端，再重新启动。多个 Debug 客户端应使用不同端口，各自的 LSP4IJ 附加配置填写对应端口。

每次启动生成独立配置快照，已知资源路径以项目根目录解析，未知字段原样传递。启动和保存 Run 不改写项目文件；进程退出后清理快照。

### 命令行与 AI 驱动

主客户端与自动模式使用 `mcdk run` 前台启动：游戏日志仍然输出到同一个控制台，停止按钮结束的也是同一次启动。与此同时会话记录写在项目 `.mcdev/sessions/`，因此可以在项目目录用命令行（或让 AI）直接驱动这次运行，不需要再配置 MCP 客户端：

```powershell
mcdk ps                          # 查看运行中的会话
mcdk wait                        # 等到进入存档
mcdk logs                        # 读最新日志，末行给出游标 [logs] next=N
mcdk logs --since N --errors     # 只看游标之后的新错误
mcdk call reload_scripts         # 同步重载改过的 py，返回成功与失败的模块
mcdk stop                        # 结束游戏
```

- **MCDK 路径从哪里来**：插件每次启动（生成默认配置时也会）把实际使用的 `mcdk.exe` 绝对路径写入项目根目录 `.mcdev.json` 的顶层 `mcdk_path` 字段（`/` 分隔、可直接执行）。命令行或 AI 只要打开项目的 `.mcdev.json` 就能拿到它，例如 `& ((Get-Content .mcdev.json -Raw | ConvertFrom-Json).mcdk_path) ps`。这个字段是插件维护的元数据，不会传给 MCDK 游戏配置；留空或失效时按常见安装位置回退探测即可。
- 留空 **CLI 会话名**时使用默认会话 `default`，不隔离，存档仍在原来的位置；`mcdk logs` / `stop` 会自动选中唯一运行中的会话。
- 填写会话名后启用 Lane 隔离：包目录、存档、日志都在项目 `.mcdev/lanes/<会话名>` 下，多开互不冲突；此时「打开存档目录」仍指向真实数据目录，会话存档需要到该 Lane 目录查看。
- **MCP 服务默认开启**（`.mcdev.json` 的 `mcp_server_config.enabled` 默认为 `true`），随游戏进程启停，端口默认 `19133`。这就是 MCDK 上游文档里的默认端口，外部 MCP 客户端可以照文档直接连 `http://localhost:19133/sse`。
- 插件启动前会检查这个端口：空闲就用它，被别的实例或程序占用则改用一个空闲端口并在控制台打印实际端口。这样多开时不会因为 19133 冲突让 MCDK 直接中止启动。
- CLI 的 `logs` / `call` 不需要你填端口：MCDK 把实际端口写进会话记录（`.mcdev/sessions/*.json`），命令行自己按记录连接。
- 要多开且想让每个实例都有固定端口，就在各自的 `.mcdev.json` 里写死不同的 `mcp_server_config.server_port`。想彻底关掉，把 `mcp_server_config.enabled` 设为 `false`（此时 CLI 的 `logs` / `call` 仍可用，插件启动时会强制打开本次会话的 MCP）。
- 后台运行、`--detach`、`--reuse`、`--lane` 级别等更细的自动化参数仍可直接用 `mcdk run` 手动调用，插件的启动参数与之等价，可以互相参考。
- 会话记录位于项目 `.mcdev/` 下，建议把它加入项目的 `.gitignore`，避免误提交。

### 给 AI Agent 用的 Skill（免 MCP 客户端）

仓库 `skills/mcdk-cli/` 是一个 DSH skill：agent 加载后，会先从项目 `.mcdev.json` 的 `mcdk_path` 找到 MCDK，再用 `mcdk run / wait / logs / call / stop` 驱动这次运行——启动、游标增量读日志、热重载脚本、调用游戏内工具（截图、输入、性能分析、JSON UI 调试）、停止游戏，全程不需要在 AI 工具里配置 MCP 客户端。

安装方式（任选其一，详见 `skills/README.md`）：

```powershell
# 对当前 mod 工程生效，随工程提交
Copy-Item -Recurse "<本仓库>\skills\mcdk-cli" "<你的工程>\.dsh\skills\mcdk-cli"
# 或对所有工程生效
Copy-Item -Recurse "<本仓库>\skills\mcdk-cli" "$env:USERPROFILE\.dsh\skills\mcdk-cli"
```

> **注意**：skill 免的是「外部 MCP 客户端」，不是 MCDK 自己的 MCP 服务。`mcdk logs` / `call` 正是通过 MCDK 内置 MCP 读取日志和调用工具的，所以不要把 `.mcdev.json` 的 `mcp_server_config.enabled` 关掉。

## 四、运行

完成配置后，点击运行按钮即可通过 **MCDK** 启动游戏。插件会生成独立的配置快照，再以 `mcdk run` 执行内置 `mcdk.exe`，项目文件不会被改写。游戏正常启动的同时，会打开日志输出界面：

![日志输出界面](./img/log-output.jpg)

控制台同时输出 **MCDK 日志**和 **游戏日志**。游戏日志由 MCDK 通过 PIPE/Safaia 采集后写到同一条控制台流；默认 `log_protocol` 为 Safaia。日志等级「默认」会过滤引擎噪声，「全部」保留系统日志。

### 常用操作

| 操作 | 快捷键 / 方式 |
| :-: | :-: |
| 热重载脚本 | 由 MCDK 调试 Mod 提供，默认快捷键见 `.mcdev.json` 的 `debug_options` |
| 重载整个存档 | 由 MCDK 调试 Mod 提供，默认快捷键见 `.mcdev.json` 的 `debug_options` |
| 跳转到错误代码 | 点击错误日志中的文件链接，PyCharm 自动跳转 |

![热重载脚本](./img/hot-reload.jpg)
![跳转到错误代码](./img/jump-to-error.jpg)

---

## 五、断点调试

> 由于 PyCharm 对 DAP（Debug Adapter Protocol）调试协议的原生支持有限，此部分配置稍显复杂，请按步骤操作。

### 4.1 前置检查

在使用断点调试之前，请确保满足以下条件：

1. 使用 **3.7.0.222545** 及以上版本的 ModPC 开发包
2. 已通过 PyCharm **以 Debug 模式** 启动过「网易组件开发」配置（会向 MCDK 注入 `MCDEV_PTVSD_*`）
3. 在日志开头能看到如下提示信息：

   ![调试就绪提示](./img/debug-ready-hint.png)

### 4.2 创建调试配置

1. 再次进入运行配置界面，新增一个 **调试适配器协议** 类型的运行配置，然后点击序号 ③ 处的 `创建新服务器`：

   ![创建新服务器](./img/create-debug-server.jpg)

2. 在打开的页面中，点击 `选择模板`，添加一个 **Python** 模板：

   ![选择 Python 模板](./img/select-python-template.jpg)

3. 进入配置页面后，确保以下设置正确：

   | 配置项 | 值 |
   | :-: | :-: |
   | 调试模式 | 附加 |
   | 地址 | `127.0.0.1` |
   | 端口号 | `5678` |

   ![调试配置详情](./img/debug-config-detail.jpg)

4. **DAP 参数（JSON）** 填写如下内容：

```json
{
  "name": "Attach",
  "type": "python",
  "request": "attach",
  "redirectOutput": true,
  "connect": {
    "host": "127.0.0.1",
    "port": 5678
  },
  "pathMappings": [
    {
      "localRoot": "${workspaceFolder}",
      "remoteRoot": "${workspaceFolder}"
    }
  ],
  "debugOptions": [
    "ShowSpecialMembers",
    "ShowPrivateMembers",
    "ShowFunctionMembers",
    "ShowBuiltinMembers"
  ]
}
```

### 4.3 启动调试

保存配置后，务必以 **调试模式（Debug）** 运行该配置。

> **提示：** 首次运行时会自动下载必要的前置库，请耐心等待。

![以调试模式运行](./img/run-debug-mode.jpg)

当调试窗口自动跳转到如下界面时，说明调试程序已成功连接到游戏：

![调试连接成功](./img/debug-connected.png)

### 4.4 设置断点

在 PyCharm 中设置断点需要 **点击两次**：

1. 第一次点击行号左侧的空白处
2. 第二次点击行内，断点才会生效

![设置断点](./img/set-breakpoint.jpg)

一切就绪，现在可以愉快地进行断点调试了 🎉

![断点调试效果](./img/breakpoint-debugging.jpg)

**快捷键提示：** 按 **F9** 可以继续运行程序。

