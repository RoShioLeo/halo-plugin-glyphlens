# 字镜 · GlyphLens

<img src="src/main/resources/logo.svg" width="64" alt="字镜 GlyphLens">

面向历史文献整理的 Halo 字形转换工具，将繁体、异体字转换为常用简体，支持正文点选校对与统一字库维护。

新字库试运行中，如有问题可通过页面右下角反馈。

[在线体验](https://www.lemonslice.cloud/tools/glyphlens/) · [下载安装包](https://github.com/RoShioLeo/halo-plugin-glyphlens/releases/latest) · [问题反馈](https://github.com/RoShioLeo/halo-plugin-glyphlens/issues)

## 功能

- **字形转换**：输入文字或从 UTF-8 文件导入原文，统一点击「转换」，再复制或下载结果。「示例」载入沈括《梦溪笔谈》的一段原文，并保留出处。
- **点选校对**：首次使用默认转换，此后记住上次使用的模式。多字候选模式持续标记有多个候选的字，直接点击下划线字即可修改，或点击右侧「开始校对」逐处检查；没有多候选字时隐藏该按钮。开始和结束校对使用相同样式，结果标题始终为「转换结果」。左右箭头切换位置，右侧「确认剩余」按当前结果确认其余位置，之后仍可修改。模式旁的问号支持悬停或点击查看操作说明。
- **正文导出**：复制、下载只输出当前正文，尚未校对的位置沿用当前默认候选。文件先导入原文，转换后可继续点选校对，再手动导出。
- **词典消歧**：先查单字，只有多候选字才匹配简体上下文词条。唯一命中作为默认候选，未命中取第一候选；词典命中也需人工校对，手动选择优先。
- **字库维护**：所有字形和竖排标点使用同一字库，支持来源查看、审核、批量启停、修改记录、导入和导出。新增、编辑统一填写原字和有序候选，支持十六进制 Unicode 码。JSON 合并自定义映射，ZIP 更新默认字库，均先预览再确认。
- **字形反馈**：提交有问题的字及建议，经处理后再调整字库。

## 安装与使用

需要 **Halo 2.26.0 或以上版本**。下载 Release 中的 JAR，在 Halo 后台的「插件」页面安装并启用。

- 前台入口：`/tools/glyphlens/`
- 后台入口：「工具 → 字镜」；字库、词典和反馈管理仅对超级管理员开放。

未收录的字保留原样，转换不会反复替换同一段结果。字镜处理字形对应，不做地区词汇翻译。

插件版本 **0.2.3**，独立默认字库版本 **1.0.0**。字库收录 **12,718 个原字、12,787 条映射**，其中 **69 个多候选原字，0 项待审**。225 项人工确认关系直接进入默认字库，5 项不采纳的补充关系继续排除；`䌷` 的候选顺序为 `䌷／绸`，`硷` 仅对应 `碱`。

字库在同仓库的 [`dictionary/`](dictionary/README.md) 中独立维护、复现和打包。站点手动修正不改变默认字库版本，定期审核吸收后再发布新字库；更新默认包保留站点修正。自定义文件示例、字段及导入导出规则见[字库格式文档](docs/DICTIONARY-IMPORT.md)。

默认消歧词典包含 **9 个原字、18 组规则、75 个简体词条**，例如干燥／乾坤、沉没／沈阳、仿佛／彷徨、了解／瞭望。默认规则可在后台编辑或停用，详见[词典说明](docs/LEXICON.md)。

0.2.0 已合并旧通用表和简体表，移除转繁体及其字库。

从 0.1.x 切换到 0.2.0 请卸载旧版后重新安装。本版使用新的单字库数据结构，不导入旧方向字库或历史记录；如其他站点有自定义资料，请先自行导出。

## 字库版本与来源

以下为默认字库 1.0.0 实际录入的数据，由生成程序统计；后台“字库版本”读取同一份统计。

| 来源 | 收录字数 | 映射条数 | 用途 |
| --- | ---: | ---: | --- |
| [《通用规范汉字表》（2013）及附表](https://www.moe.gov.cn/srcsite/A19/s229/201310/t20131015_159487.html) | 3,568 | 3,588 | 规范简体及已明确的繁体、异体对应。 |
| [Unicode 资料](https://www.unicode.org/reports/tr38/) | 762 | 763 | 规范等价别名、确认的字形变体及竖排标点。 |
| [OpenCC 通用字符表](https://github.com/BYVoid/OpenCC/tree/fae2a0a911c30764e669a7d8b0591cd8e852934a/data/dictionary) | 214 | 215 | 已确认的通用字符补充关系。 |
| [中国台湾地区教育主管部门《異體字字典》](https://dict.variants.moe.edu.tw/) | 8,175 | 8,175 | 筛选后补充缺少的异体字对应。 |
| [手动维护来源](dictionary/research/manual-sources.json) | 1 | 1 | 「竃 → 灶」由维护者手工补充，原始文献出处尚待核对。 |
| 保留原字选项 | 45 | 45 | 转换策略，不计作外部资料来源。 |
| **全库总计** | **12,718** | **12,787** | 按全字库去重统计，不直接相加各来源字数。 |

每个“原字 → 候选字”计一条对应，含多候选中的原字选项。同一原字的候选来自不同来源时，各来源原字数会重叠；每条对应只归入一个来源。详情按候选保存原始记录、版本、位置和中间字归并路径。人工确认是独立的审核记录，不替代文献出处；手工添加而尚未核对原始出处的映射标为“手动维护来源”。保留原字另列。

现有来源优先，参考资料只补空缺。自动补充限定为原始对应唯一、目标为规范简体一级或二级字、且不存在来源冲突的异体字。

收录规则、候选排序和字库统计见[字库说明](docs/DICTIONARY.md)；人工确认关系见[默认修正清单](dictionary/research/maintainer-decisions.json)，数据版本与校验值见[来源清单](dictionary/research/source-manifest.json)。

## 开发

需要 Java 21、Node.js 24；运行数据校验还需 Python 3。仓库提供 Gradle Wrapper，构建时安装 pnpm。

```bash
pnpm --dir ui install --frozen-lockfile
node --test tests/*.test.mjs
python dictionary/scripts/test_reference_policy.py
python dictionary/scripts/test_source_provenance.py
./gradlew build --no-daemon --console=plain
```

Windows 使用 `gradlew.bat`。安装包输出至 `build/libs/`。

校对只保留在当前页面，不自动写入字库；切换默认/多字视图保留选择，修改原文后重新生成。候选开启时可用数字键 1–9 选择、方向键跳转，Esc 关闭候选。

字库已随仓库保存，普通构建无需抓取数据。复现字库：

```bash
python dictionary/scripts/restore_research.py --cache .research
python dictionary/scripts/prepare_catalog.py --cache .research --output .research/rebuilt
python dictionary/scripts/verify_catalog.py --rebuilt .research/rebuilt
python dictionary/scripts/package_dictionary.py
```

## 许可

项目代码采用 [MIT](LICENSE)。数据资料保留各自许可及署名，包括 [Unicode 数据许可](licenses/Unicode-LICENSE.txt)、OpenCC 的 Apache-2.0 许可及其他来源条款，详见 [来源声明](NOTICE.md)。

## 致谢

感谢 @小兽KEMONO 提供技术支持。
