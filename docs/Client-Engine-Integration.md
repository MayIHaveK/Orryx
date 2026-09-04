# Orryx 客户端引擎集成文档

Orryx 对 DragonCore（龙之核心）、GermPlugin（萌芽引擎）、ArcartX、MayDMZAnimation 和
MayDMZParticle 提供可选兼容支持，包括触发器、Kether 动作和扩展功能。

---

## 一、DragonCore（龙之核心）

### 触发器（7个）

| 触发器                             | 说明      | 参数                                              |
|---------------------------------|---------|-------------------------------------------------|
| `DragonKeyPressTrigger`         | 按键按下    | `key`（按键）, 特殊键 `Keys`                           |
| `DragonKeyReleaseTrigger`       | 按键释放    | `key`（按键）, 特殊键 `Keys`                           |
| `DragonPacketTrigger`           | 自定义数据包  | `identifier`（包名）, `data`（数据）, 特殊键 `Identifier`  |
| `DragonSlotTrigger`             | 槽位更新    | `identifier`（槽位名）, `item`（物品）, 特殊键 `Identifier` |
| `DragonEntityJoinWorldTrigger`  | 实体加入客户端 | `uuid`, `entity`                                |
| `DragonEntityLeaveWorldTrigger` | 实体离开客户端 | `uuid`, `entity`                                |
| `DragonCacheLoadTrigger`        | 龙核缓存加载  | —                                               |

### Kether 动作

#### 时装系统

- `dragoncore armourers send` — 设置临时时装
- `dragoncore armourers clear` — 清除临时时装
- `dragoncore armourers update` — 更新时装

#### 粒子特效

- `dragoncore effect send` — 发送暴雪粒子
- `dragoncore effect remove` — 移除暴雪粒子
- `dragoncore effect clear` — 清除暴雪粒子

#### 动画系统

- `dragoncore animation set player` — 设置玩家动作
- `dragoncore animation remove player` — 移除玩家动作
- `dragoncore animation set entity` — 设置实体动作
- `dragoncore animation remove entity` — 移除实体动作
- `dragoncore animation set item` — 设置手持物品动作
- `dragoncore animation set block` — 设置方块动作

#### 音效系统

- `dragoncore sound send` — 播放音乐
- `dragoncore sound stop` — 停止音乐

#### UI 系统

- `dragoncore gui` — 打开 GUI
- `dragoncore hud` — 打开 HUD
- `dragoncore function gui` — 运行 GUI 方法
- `dragoncore function animation` — 运行动作控制器方法
- `dragoncore function headtag` — 运行 headTag 方法

#### 模型系统

- `dragoncore model set` — 设置实体模型
- `dragoncore model remove` — 移除实体模型
- `dragoncore modelEffect create` — 创建实体模型特效
- `dragoncore modelEffect remove` — 移除实体模型特效

#### 其他

- `dragoncore papi send` — 发送同步 placeholder 数据
- `dragoncore papi delete` — 删除 placeholder 数据
- `dragoncore headtag set/remove` — 设置/移除 headTag
- `dragoncore view` — 设置视角（1/2/3人称）
- `dragoncore title` — 设置窗口标题
- `dragoncore bindEntity` — 虚拟绑定实体位置
- `dragoncore invisibleHand` — 隐藏玩家手持武器
- `dragoncore slot` — 获取槽位内物品

---

## 二、GermPlugin（萌芽引擎）

### 触发器（3个）

| 触发器                       | 说明      | 参数                                                                        |
|---------------------------|---------|---------------------------------------------------------------------------|
| `GermKeyDownTrigger`      | 按键按下    | `key`, `keyBinding`（含 `name`/`index`/`defaultKey`/`category`）, 特殊键 `Keys` |
| `GermKeyUpTrigger`        | 按键释放    | `key`, `keyBinding`, 特殊键 `Keys`                                           |
| `GermClientLinkedTrigger` | 客户端连接完成 | `ip`, `machineCode`, `modVersion`, `qq`                                   |

### Kether 动作

#### 时装系统

- `germplugin armourers send` — 设置临时基岩时装
- `germplugin armourers clear` — 清除临时基岩时装

#### 特效系统

- `germplugin effect send` — 发送 Effect 特效
- `germplugin effect remove` — 移除 Effect 特效
- `germplugin effect clear` — 清除 Effect 特效

#### 动画系统

- `germplugin animation set entity` — 设置实体动作（自动识别玩家/怪物）
- `germplugin animation stop entity` — 停止实体动作
- `germplugin animation remove entity` — 移除实体动作
- `germplugin animation set item` — 设置玩家物品动作
- `germplugin animation stop item` — 停止玩家物品动作
- `germplugin animation remove item` — 移除玩家物品动作
- `germplugin animation set block` — 设置方块动作
- `germplugin animation stop block` — 停止方块动作
- `germplugin animation remove block` — 移除方块动作

#### 音效系统

- `germplugin sound send` — 播放音乐
- `germplugin sound stop` — 停止音乐

#### UI 系统

- `germplugin gui` — 打开萌芽 GUI
- `germplugin hud` — 打开萌芽 HUD

#### 其他

- `germplugin view` — 设置视角（FIRST_PERSON / THIRD_PERSON / THIRD_PERSON_REVERSE / CURRENT_PERSON）
- `germplugin slot` — 获取槽位内物品

> 注意：GermPlugin 动画桥接不支持单独移除动画，使用基岩版皮肤（`GermSkinBedrock`）。

---

## 三、ArcartX

### 触发器（10个）

| 触发器                              | 说明      | 参数                              |
|----------------------------------|---------|---------------------------------|
| `ArcartXKeyPressTrigger`         | 按键按下    | `key`, 特殊键 `Keys`               |
| `ArcartXKeyReleaseTrigger`       | 按键释放    | `key`, 特殊键 `Keys`               |
| `ArcartXSimpleKeyPressTrigger`   | 简单按键按下  | `key`, 特殊键 `Keys`               |
| `ArcartXSimpleKeyReleaseTrigger` | 简单按键释放  | `key`, 特殊键 `Keys`               |
| `ArcartXKeyGroupPressTrigger`    | 按键组按下   | `group`（按键组ID）, 特殊键 `Groups`    |
| `ArcartXMouseClickTrigger`       | 鼠标点击    | `button`（鼠标按键）, `action`（动作类型）  |
| `ArcartXCustomPacketTrigger`     | 自定义数据包  | `id`, `data`, `args`, 特殊键 `Ids` |
| `ArcartXEntityJoinTrigger`       | 实体加入客户端 | `uuid`, `entity`                |
| `ArcartXEntityLeaveTrigger`      | 实体离开客户端 | `uuid`, `entity`                |
| `ArcartXClientChannelTrigger`    | 客户端通道连接 | —                               |

### Kether 动作

#### 动画系统

- `arcartx animation set` — 设置实体动画（支持速度、过渡时间、持续时间）
- `arcartx animation default` — 设置实体默认动画状态

#### 音效系统

- `arcartx sound send` — 播放音效
- `arcartx sound stop` — 停止音效

#### UI 系统

- `arcartx ui open` — 打开 UI
- `arcartx ui close` — 关闭 UI
- `arcartx ui run` — 运行 UI 脚本

#### 模型系统

- `arcartx model set` — 设置实体模型（支持缩放）

#### 变量系统

- `arcartx variable set` — 设置服务端变量
- `arcartx variable remove` — 移除服务端变量

#### 其他

- `arcartx packet` — 发送自定义数据包
- `arcartx shake` — 屏幕震动
- `arcartx title` — 设置窗口标题

### Glimmer 脚本引擎集成

ArcartX 独有的 Glimmer 脚本集成，命名空间为 `Orryx`。

#### 静态函数（`OrryxGlimmerFunctions`）

| 分类  | 函数                                                                                        |
|-----|-------------------------------------------------------------------------------------------|
| 法力值 | `getMana`, `getMaxMana`, `setMana`, `giveMana`, `takeMana`                                |
| 精力值 | `getSpirit`, `getMaxSpirit`, `setSpirit`, `giveSpirit`, `takeSpirit`                      |
| 技能  | `castSkill`, `getSkillLevel`, `isSkillLocked`, `getSkillCooldown`                         |
| 状态  | `isSuperBody`, `isInvincible`, `isSilence`, `setSuperBody`, `setInvincible`, `setSilence` |
| 职业  | `getJob`, `getJobLevel`, `getJobExperience`, `getJobMaxExperience`                        |
| 其他  | `getPoint`                                                                                |

#### 玩家对象（`OrryxPlayerObject`）

- 构造：`OrryxPlayer(playerName)`
- 包含上述所有方法的对象形式，额外支持 `getJobMaxLevel`, `getExperience`, `getMaxExperience`, `getGroup`, `getFlag`

---

## 四、MayDMZAnimation

Orryx 通过 MayDMZAnimation 的独立公共 API 和 Bukkit Services 接入，不访问组合图编译器、执行器、
资源传输或协议实现。`MayDMZAnimation` 是软依赖，未安装、未完成激活或 API 链接失败时 Orryx 仍可正常
加载；`maydmz available` 返回 `false`，其他动作安全返回 `unavailable`、`false` 或 `0`。

语句注册在 Orryx 命名空间，主关键字为 `maydmz`，别名为 `dmzanimation`。

### 组合动作

- `maydmz available`：API 服务当前是否可用。
- `maydmz combo assign <连击ID> [they <玩家容器>]`：分配主动连击；调用时不播放，第一次物理输入才开始。
- `maydmz combo clear [they <玩家容器>]`：清除主动连击并释放旧输入绑定；没有管理员自定义默认组合技时，下一次攻击恢复 DragonMineZ 原生平 A。
- `maydmz combo compare-and-set <预期ID或空字符串> <新ID或空字符串> [they <玩家容器>]`：仅当显式分配仍等于预期值时原子替换；并发变化返回 `conflict`。
- `maydmz combo current [they <玩家容器>]`：查询当前分配，未分配时值为空字符串。
- `maydmz combo exists <连击ID>`：连击目录是否包含该 ID。
- `maydmz action exists <动作ID>`：动作目录是否包含该 ID。
- `maydmz action start <动作ID> [priority <整数> [policy <drop|replace|force>]] [they <玩家容器>]`：立即启动动作，调用成功即播放入口节点。
- `maydmz action signal <信号> [channel <通道>] [they <玩家容器>]`：向运行中的动作发送时间窗信号。
- `maydmz action stop [channel <通道>] [they <玩家容器>]`：正常停止动作。
- `maydmz action cancel <原因> [channel <通道>] [they <玩家容器>]`：以取消原因结束动作。
- `maydmz action running [channel <通道>] [they <玩家容器>]`：返回匹配的运行中动作数量。

`action start` 返回一个 `Map<玩家UUID, 结果>`。成功值为 `started` 或 `pending`；拒绝值与
MayDMZAnimation 的 `ActionRejection` 小写名一致，例如 `unknown_action`、`no_permission`、
`priority_busy`。未写 `priority` 时完全沿用动作 JSON 自己的仲裁规则。

```text
maydmz combo assign "maydmz:rapid_tap_demo" they @self
maydmz combo compare-and-set "maydmz:rapid_tap_demo" "" they @self
maydmz action start "maydmz:skill_attack" priority 100 policy replace they @self
maydmz action signal "hit_confirm" channel "upper_body" they @self
maydmz action cancel "skill_interrupted" they @self
```

### 直接播放

- `maydmz playback play <动画ID> [mode <once|loop|hold>] [speed <浮点>] [duration <tick>] [transition <tick>] [they <玩家容器>]`
- `maydmz playback play-handle <动画ID> [mode <once|loop|hold>] [speed <浮点>] [duration <tick>] [transition <tick>] [they <玩家容器>]`
- `maydmz playback stop [transition <tick>] [they <玩家容器>]`
- `maydmz playback stop-instance <实例号> [transition <tick>]`

`play` 保持兼容，返回成功提交的玩家数量；`play-handle` 返回
`Map<玩家UUID字符串, 播放实例号Long>`，可交给 `stop-instance` 精确结束这一段播放而不干扰同一玩家的
其他通道。按玩家 `stop` 会结束该玩家由直接播放 API 跟踪的普通实例，不接管 Combo 所有的通道。
直接播放适合单个动画片段；带输入窗口、连段、notify 和服务端状态的技能应使用 `action start`。

JavaScript 中可以组合播放与显式结束；下面的 `stop` 会停止目标玩家当前的普通播放实例：

```javascript
return kether.run('maydmz playback play-handle "maydmz.smoke.upper_wave" mode loop speed 1.0 duration 200 transition 4.0 they @self')
    .thenCompose(function(handles) {
        var instance = handles.get(player.getUniqueId().toString());
        return scheduler.later(function() {
            return kether.run('maydmz playback stop-instance ' + String(instance) + ' transition 4.0');
        }, 40);
    });
```

### JavaScript 技能示例

内置的 `skills/MayDMZAnimation-JavaScript示例.yml` 使用 `ScriptEngine: JAVASCRIPT`，从 JS 调用
`kether.run(...)` 并返回组合后的 `CompletableFuture`，不会阻塞主线程。它先执行 `maydmz available`，可用时
分配 `maydmz:rapid_tap_demo`，调用时不会播放动画，随后由玩家第一次及连续点击攻击键推进四段；只有本示例
实际改变了分配时，才会在 200 tick 后通过 API 的原子 compare-and-set 安全恢复此前分配，避免覆盖玩家或其他插件稍后作出的修改。
此前为空分配时会执行 clear；随 MayDMZAnimation 发布的示例均非默认组合技，因此会恢复 DragonMineZ 原生平 A。
缺失插件或分配被拒绝时向玩家显示安全降级结果。示例使用公开 `ComboAssignmentService`，Orryx 不接触按键协议、
组合图解释器或资源传输内部实现。测试命令：

```text
/or skill cast <玩家名> MayDMZAnimation-JavaScript示例
/or skill cast <玩家名> MayDMZAnimation-JavaScript普通播放示例
```

第二个示例循环播放 `maydmz.smoke.upper_wave`，保存 `play-handle` 返回的实例号，40 tick 后用
`stop-instance` 精确结束并输出结果；`duration 200` 只是停止语句未能执行时的安全上限。

### 性能与带宽

Orryx 不启动轮询任务，也不自行发送 DragonMineZ 插件消息；只有脚本实际执行上述语句时才调用
MayDMZAnimation。Java 8 兼容桥只在公开 `api` 接口上解析方法并按当前插件实例缓存元数据，不反射混淆后的
provider 实现类；Bukkit service provider 仍在每次调用时重新取得，因此插件延迟激活或重载后不会持有旧运行时。
连招按键采样、图遍历和本机播放由 DragonMineZ 客户端即时决定；服务端仅校验当前分配与批次内容，并给旁观者
转发时使用自己的顺序号。`they` 选中多名玩家时会在主线程逐名调用，批量技能应控制选择器规模；资源传输、
观察者半径和广播限流均由 MayDMZAnimation 自己负责。

---

## 五、MayDMZParticle

Orryx 只通过 MayDMZParticle 的稳定公共 API 接入，不引用资源目录、传输会话、内容哈希、协议帧、
缓存或授权实现。`MayDMZParticle` 是软依赖；没有安装、尚未完成激活或 API 链接失败时 Orryx 仍正常
加载，`maydmzparticle available` 返回 `false`，其余语句返回安全空值。

语句主关键字为 `maydmzparticle`，别名为 `dmzparticle`。所有播放/停止操作在主线程执行。

### 查询语句

- `maydmzparticle available`：公共播放服务当前是否可用。
- `maydmzparticle exists <效果ID>`：当前 last-good 目录是否包含效果。
- `maydmzparticle effects`：返回当前效果 ID 列表。
- `maydmzparticle active`：返回服务端跟踪的活动播放实例数。

### 播放语句

```text
maydmzparticle play <效果ID> [bone <骨骼或socket>] [duration <tick>]
    [offset <x,y,z>] [rotation <pitch,yaw,roll>] [scale <x,y,z>] [they <玩家容器>]

maydmzparticle play-at <效果ID> [duration <tick>]
    [offset <x,y,z>] [rotation <pitch,yaw,roll>] [scale <x,y,z>] [they <玩家容器>]
```

`play` 把效果附着到目标玩家；`bone` 为空时使用实体根，推荐跨模型使用
`socket:right_hand` 等语义 socket。原始骨骼可写 `bone:right_arm2`，精确模型定位器可写
`locator:right_hand_item/locator3`。`play-at` 会在每名目标玩家执行语句时的当前位置建立固定世界锚点，
随后不会跟随玩家移动。`duration 0` 表示由效果自身或显式停止决定。两者都返回
`Map<玩家UUID字符串, 播放句柄UUID字符串>`；提交失败时对应值为空字符串。

```text
maydmzparticle play "dmz:example_bone_sparks" bone "socket:right_hand" duration 100 offset "0,-0.15,0" rotation "0,0,25" scale "1.2,1.2,1.2" they @self
maydmzparticle play "dmz:example_bone_sparks" bone "bone:right_arm2" duration 100 they @self
maydmzparticle play "dmz:example_bone_sparks" bone "locator:right_hand_item/locator3" duration 100 they @self
maydmzparticle play-at "dmz:example_burst" duration 60 scale "1.3,1.3,1.3" they @self
```

### 停止语句

- `maydmzparticle stop <播放句柄UUID>`：按句柄停止一个实例，返回布尔值。
- `maydmzparticle stop-entity [they <玩家容器>]`：停止目标玩家关联的全部实例，返回停止数量。
- `maydmzparticle stop-all`：停止 MayDMZParticle 当前全部实例，返回停止数量。

### JavaScript 技能示例

内置 `skills/MayDMZParticle-JavaScript示例.yml` 是 Nashorn ES5.1 示例。它用
`kether.run(...)` 检查可用性，依次验证实体根、`socket:right_hand`、`bone:right_arm2`、
`locator:right_hand_item/locator3` 和固定世界位置五类锚点，并覆盖 offset/rotation/scale。随后从播放结果
Map 读取句柄，依次执行 locator 句柄停止、世界句柄停止与 `stop-entity` 清理。测试命令：

```text
/or skill cast <玩家名> MayDMZParticle-JavaScript示例
```

旧服若已存在同名文件，Orryx 不会覆盖管理员修改的版本；需要查看新版范例时可对照 JAR 内资源。

### 性能、带宽与权限边界

Orryx 不轮询 MayDMZParticle，不复制粒子资源，也不自行发送 DragonMineZ 插件消息。Java 8 兼容桥只在
`ParticlePlaybackService` 等公共 API 类型上解析并缓存方法元数据；实际 service 每次重新发现，因此延迟激活
或重载不会长期持有旧 provider。MayDMZParticle 只发送内容哈希资源和效果级 PLAY/STOP，发射器推进、逐粒子
模拟、骨骼/socket/locator 最终姿态跟随和渲染全部留在客户端；
Orryx 的伤害、冷却和命中仍必须由服务端技能逻辑判定，不能把客户端粒子当作玩法权威状态。

---

## 六、原有客户端引擎功能对比

| 功能          |  DragonCore   | GermPlugin |     ArcartX      |
|-------------|:-------------:|:----------:|:----------------:|
| 触发器数量       |       7       |     3      |        10        |
| 按键事件        |    ✅ 按下/释放    |  ✅ 按下/释放   |  ✅ 按下/释放/简单/组合   |
| 鼠标事件        |       ❌       |     ❌      |        ✅         |
| 自定义数据包      |       ✅       |     ❌      |        ✅         |
| 实体加入/离开     |       ✅       |     ❌      |        ✅         |
| 槽位系统        |       ✅       |     ✅      |        ❌         |
| 动画系统        | ✅ 玩家/实体/物品/方块 | ✅ 实体/物品/方块 |       ✅ 实体       |
| 音效系统        |       ✅       |     ✅      |        ✅         |
| 粒子特效        |    ✅ 暴雪粒子     |  ✅ Effect  |        ❌         |
| UI 系统       |   ✅ GUI/HUD   | ✅ GUI/HUD  | ✅ open/close/run |
| 时装系统        |       ✅       |   ✅ 基岩版    |        ❌         |
| 模型系统        |     ✅ 完整      |     ❌      |       ✅ 基础       |
| 视角控制        |       ✅       |     ✅      |        ❌         |
| Placeholder |       ✅       |     ❌      |        ❌         |
| 脚本引擎        |       ❌       |     ❌      |    ✅ Glimmer     |
| 变量系统        |       ✅       |     ❌      |        ✅         |
| 屏幕震动        |       ✅       |     ❌      |        ✅         |
