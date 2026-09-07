# ADR-007 命名与坐标：累土 leitu / cn.youhuale

状态：已接受（2026-09-07）

## 项目名：累土（leitu）

《道德经》："九层之台，起于累土。"——复杂系统起于累积的标准答案。累 = 积累，与"答案库越用越厚"字面共振。

**命名判据（v3 全集）**：
1. 高频经济学——名字出现在每次 import/坐标/文档/prompt，字母×音节×使用次数 = 复利成本（目标 ≤6 字母 ≤2 音节）
2. 语义空间独占性——不撞高频词/人名/习语/活跃项目；项目要能成为该词的第一联想
3. 美感——听到/看到感觉美好
4. 意思直通 foundation 无 gap——名字本义即承载基底，不经过故事转译
5. 意义透明度——词本身的认知成本也是 gap（冷僻词对读者有隐性门槛）

**淘汰记录**（判据面前人人平等，含推荐者自己的候选）：
- Norma：高频人名/歌剧
- Keel：英语常用词（习语 on an even keel）+ keel.sh
- 磐石 Panshi：华为 5G 核心网"磐石"高可靠方案（活跃）+ 华为磐石结构 2.0 + 中科院"磐石 ScienceOne"AI 大模型——赛道正面撞
- 厚土 Houtu：搜索引擎实测将 houtu 混淆为 Hutool（cn.hutool，国内最知名 Java 工具库）——同语言同受众，干扰坐实
- Certus / Cardo / 北辰：意思转译 gap（确定/枢轴/导航基准，非地基本义）

**检查通过**：leitu 在 GitHub / 开源框架 / 软件产品全线无占用；第一联想是《道德经》原句（正锚，非干扰）。

## 坐标

- **groupId：`cn.youhuale`**（youhuale.cn 为受控活跃域；反转后为合法包名段，11 字符）
  - 硬规则：groupId 反转段即 Java 包名段——**连字符、数字开头非法**（ai-as / 58todo / 1gepingguo 等因此出局）
  - DNS 多记录共存：Central 的 TXT 验证记录与域名既有 A/CNAME 记录互不影响，零停机；验证一次性，通过后可移除
- **artifact**：`leitu-core` / `leitu-capability-*` / `leitu-adapter-*` / `leitu-spring-boot-starter-*`
- **包名**：`cn.youhuale.leitu.*`
- **GitHub**：`helloworldtang/leitu`；**文档站（规划）**：`leitu.ai-as.cn`（域名与 groupId 解耦：网站不受包名规则限制）
