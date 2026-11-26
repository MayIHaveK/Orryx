# 从 Blockbench 制作工程到 Orryx 正式技能

## 0.7：动作、范围、伤害与打断已连通

当前 `project/frost-hammer.dmzstudio` 与 `exports/skill.studio.json` 是 v2 工程，包含真实原特效、
人物动作和命中窗口。普通施放入口仍为 `/orryx skill tryCast AxPreviewTest codex_studio_real`。
编辑器“实机预览”不会扣血；正式施放由服务端命中检测并调用 DMZ 精确伤害 API。

在工作台选中 `frost_impact`，即可编辑圆形/矩形/扇形、判定高度、偏移、提前预警、伤害与动作绑定。
当前霜锤在 0.8 秒命中，持续 0.15 秒，半径 3 格、前移 2.6875 格，每目标一次、打击攻击值 20。
固定位置特效启用“按施放朝向锁定特效”，转身施放时与范围一起旋转。人物动画过渡支持 1–40 tick。
修改后先保存到服务器；本地完整工程另存 `.dmzstudio`，离线草稿可以明确导入当前服务器基线再保存。

实测：全新客户端首次播放包含动作、模型和挂点粒子；0/90 度正式施放均正确命中。
尸壳目标从 100 降至约 80.32（保留护甲结算），范围外及后方维持 100。
提前停止动作、施放者受伤以及编辑器预览三种场景均不伤测试目标；结束后五类运行计数全部归零。
新准备协议在图形编译和纹理安装完成后启动时间轴；首次仍有一次性准备等待，不冒称零延迟。

无 PNG 录制的单实例热态采样：范围渲染 CPU 中位数 0.0401 ms、峰值 0.0539 ms；
复杂模型渲染中位数 4.1774 ms、峰值 5.8539 ms。未做多人压力验收或复杂断层地形投影验收。
Mod 全量检查有 3 项无关失败（安卓渲染层清单、安卓配置注释、天空贴图数量），本轮是私有联调验收，
不能冒称完整发行验收。详细证据、安装哈希与具体场景见 `references/verification-0.7.json`。

- 实际游戏录像：`previews/frost-hammer-combat-0.7.mp4` / `.gif`。
- 原生工作台范围编辑：`previews/studio-sector-editor-0.7.png`。
- 人物、霜锤和命中范围合成预览：`previews/studio-impact-range-0.7.png`。
- 原特效几何、贴图、动画和子粒子闭包保持不变；旧 v1 完整工程保留为 `project/frost-hammer-v0.6.1.dmzstudio`。


Blockbench 负责制作。正式施放由 Orryx 读取服务器保存的工程，调用 MayDMZAnimation 与 MayDMZParticle；
不需要 Blockbench 在线，也不需要打开编辑器会话。服务器可以在远程，Blockbench 与 Minecraft 客户端在同机即可。

## 三类文件

| 文件 | 放置位置 | 作用 |
| --- | --- | --- |
| 技能工程 JSON | `plugins/Orryx/studio/<工程ID>.json` | 时间轴、动作/特效资源 ID、挂点、变换、事件 |
| 技能入口 YML | `plugins/Orryx/skills/<技能ID>.yml` | 技能类型、等级、消耗、冷却及正常施放入口 |
| 动作与特效资源 | `MayDMZAnimation/animations`、`MayDMZParticle/particles` | 两个插件分别加载和传输真实资源 |

`.dmzstudio` 是本地可继续编辑的制作工程，`.dmzfx` 是可安装的特效闭包；它们不是技能入口 YML。
工作台“保存到服务器”只保存时间轴，“接入 Orryx”可导出完整技能 YML 并显示安装步骤。
导出入口默认无消耗、无冷却，供安全接入；服主按正式技能需要调整。

## 已有技能如何引用

在 Kether 技能 Actions 中调用：

```text
dmzstudio run "frost_hammer"
```

在 JavaScript 技能中返回该执行的 Future，使取消跟随原技能生命周期：

```javascript
function main() {
    return kether.run('dmzstudio run "frost_hammer"');
}
```

`run` 等待这次施放结束；原技能被取消时也会取消对应实例。`play` 返回施放 UUID，适合需要自己管理多个并行施放的脚本；
用 `dmzstudio stop <UUID>` 只停止该玩家对应实例。不要用全局停止去清理单个技能。

## 玩家真正使用

1. 保存工程，安装资源，放入技能 YML，然后 `/orryx reload`；动作/特效目录按各自插件命令重载。
2. 将 `<技能ID>` 加入相应职业配置的 `Options.Skills`。通过现有职业/技能流程初始化玩家技能记录、学习、解锁及升级。
   只写 YML 不等于玩家已拥有该技能。
3. 沿用 Orryx 的技能栏和按键绑定。已有绑定命令为：
   `/orryx skill bindKey <玩家> <技能ID> <技能组> <按键名>`；组和按键使用服务器注册值及命令补全。
4. 使用 `/orryx skill tryCast <玩家> <技能ID>` 检查玩家路径，它包含等级、前置、冷却、消耗等检查。
   `/orryx skill cast <玩家> <技能ID>` 是管理员直接验收入口，默认跳过学习和消耗检查；不要混称两者。

本机示例已加入 `dmz-slot-lab` 测试职业，测试角色的技能记录已初始化。当前直接使用：

```text
/orryx skill tryCast AxPreviewTest codex_studio_real
```

当前工程含服务端命中窗口，正式施放会造成伤害；额外位移、状态等战斗规则可以继续使用 Orryx 服务端脚本。
需要额外按时间点执行脚本时，在工程加入 `CUE` 事件轨，并提供
`plugins/Orryx/scripts/studio/<工程ID>/<事件名>.ks`。正式施放执行事件；编辑器预览不执行战斗事件。
事件脚本需要已保存到服务器且通过编译，缺失时正式施放明确拒绝。

## 保存与预览的区别

“实机预览”播放工作台当前草稿；正式技能读取服务器保存版本。调整后先保存，再用技能施放验证。
正在执行的施放持有启动快照，不会被中途的编辑覆盖。

本地预览采用 Minecraft 世界坐标：人物原始 Bedrock 几何通过 `(180 - yaw)` 转换到游戏朝向，
模型文件中的骨骼关键帧保留。世界固定特效使用世界轴；跟随根部的特效随人物朝向旋转；骨骼挂点继承其实际父变换。
“预览人物朝向”只用于观察，不改工程或源动作。取消“固定在施放位置”后才使用跟随实体模式；这会改变技能行为，按设计选择。
复杂遮罩、控制器、IK、游戏粒子及光照仍以实机为准。

## 历史验证（2026-10-27，Studio 0.6.1）

原生工作台验证朝向选择、聚焦人物、技能导出和接入说明；MCP 的 `capture_scene` 直接使用原生 Preview 渲染，
所得图片是场景截图，不冒充整窗口 UI 截图。工作台断开时，通过上述 `tryCast` 在 yaw 0 和 90 均播放成功：
各观察到 1 动作层、1 模型、最多 68 粒子；结束后归零。保留原来测试角色职业与其他技能。
0.6.1 当时尚未解决首遍图形编译晚到；0.7 已新增独立图形准备确认，详见上方记录。


### 防御结算前的伤害公式

范围的 `damageFormula` 留空时继续使用 `damage`；填写例如 `{STR} * 1.5 + 20` 或 `{MELEE_DAMAGE} + %orryx_蓝量% * 0.1` 后使用公式。DMZ 内置六项生效属性为 STR、AGI、TEC、CON、WIL、SPI；BASE_ 前缀读取基础加点。HP、KI、STAMINA 及 MAX_ 前缀读取资源与上限，MELEE_DAMAGE、KI_DAMAGE 是当前派生攻击力，RELEASE 是释放百分数。

公式在接受施放时冻结，整次施放内每个变量只读取一次。PlaceholderAPI 必须已启用，相应扩展必须返回纯数字；缺失、不合法、除零、溢出会拒绝施放。支持 +、-、*、/、括号；不支持脚本或把占位符返回值当代码执行。上限为 512 字符、128 运算节点、16 层嵌套、最终攻击值 1e12。正式命中仍走 DMZ 原生战斗和无敌帧，本地范围预览不模拟伤害。


构建验证同时检查生产执行器的 JVM 接口实现。修改 Kotlin 带默认实现的执行器接口后，如果命中旧增量缓存，使用 `gradlew.bat --no-daemon build "-Pkotlin.incremental=false" --rerun-tasks` 重建；不要部署触发该检查的旧字节码。这个检查用于防止单纯配置/运行时测试通过、真实 Bukkit 执行器仍触发 AbstractMethodError 的情况。
