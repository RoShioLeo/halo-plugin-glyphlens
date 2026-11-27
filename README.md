# 字镜 · GlyphLens

<img src="src/main/resources/logo.svg" width="64" alt="字镜 GlyphLens">

面向 Halo 的字形转换工具，将繁体、异体字转换为常用简体，支持正文点选校对与统一字库维护。

[在线体验](https://www.lemonslice.cloud/tools/glyphlens/) · [下载安装包](https://github.com/RoShioLeo/halo-plugin-glyphlens/releases/latest) · [问题反馈](https://github.com/RoShioLeo/halo-plugin-glyphlens/issues)

## 功能

- **字形转换**：输入文字或上传 UTF-8 文本，转换为常用简体，支持复制和下载结果。
- **点选校对**：初始使用默认转换。切到多字候选后，正文标记有多个写法的字，点选候选并跳到下一处；同字在不同位置可独立选择。
- **正文导出**：复制、下载只输出选定的正文，尚未校对的位置沿用首选字。文件在多字候选模式下先展示，再手动下载。
- **词典消歧**：结合词条选择合适的字形，减少一字多义带来的误替换。
- **字库维护**：所有字形和竖排标点使用同一字库，支持来源查看、审核、批量启停、修改记录和导出。新增、编辑统一填写原字和有序候选，支持十六进制 Unicode 码。
- **字形反馈**：提交有问题的字及建议，经处理后再调整字库。

## 安装与使用

需要 **Halo 2.26.0 或以上版本**。下载 Release 中的 JAR，在 Halo 后台的「插件」页面安装并启用。

- 前台入口：`/tools/glyphlens/`
- 后台入口：「工具 → 字镜」；字库、词典和反馈管理仅对超级管理员开放。

未收录的字保留原样，转换不会反复替换同一段结果。字镜处理字形对应，不做地区词汇翻译。

0.2.0 合并旧通用表和简体表，移除转繁体及其字库。发布前已确认的 163 项修正直接进入默认字库；5 项不采纳的补充关系不再进入待审。默认包含 12,656 条映射，保留 62 项转简体补充关系待审。

从 0.1.x 切换到 0.2.0 请卸载旧版后重新安装。本版使用新的单字库数据结构，不导入旧方向字库或历史记录；如其他站点有自定义资料，请先自行导出。

## 数据来源

| 来源 | 用途 |
| --- | --- |
| [《通用规范汉字表》（2013）及附表](https://www.moe.gov.cn/srcsite/A19/s229/201310/t20131015_159487.html) | 规范字等级、繁体与异体对应。 |
| [Unicode Unihan 17.0.0](https://www.unicode.org/Public/17.0.0/ucd/Unihan.zip) | Unicode 字符关系与兼容字处理。 |
| [OpenCC 通用字符表](https://github.com/BYVoid/OpenCC/tree/fae2a0a911c30764e669a7d8b0591cd8e852934a/data/dictionary) | 补充繁简字符对应。 |
| [中国台湾地区教育主管部门《異體字字典》](https://dict.variants.moe.edu.tw/) | 筛选后补充缺少的异体字对应，并为人工填写提供参考。 |

现有来源优先，参考资料只补空缺。自动补充限定为对应明确的异体字，目标范围为简体一级、二级字及繁体常用、次常用字。

收录规则、候选排序和字库统计见[字库说明](docs/DICTIONARY.md)；人工确认关系见[默认修正清单](research/maintainer-decisions.json)，数据版本与校验值见[来源清单](research/source-manifest.json)。

## 开发

需要 Java 21、Node.js 24；运行数据校验还需 Python 3。仓库提供 Gradle Wrapper，构建时安装 pnpm。

```bash
pnpm --dir ui install --frozen-lockfile
node --test tests/*.test.mjs
python scripts/test_reference_policy.py
./gradlew build --no-daemon --console=plain
```

Windows 使用 `gradlew.bat`。安装包输出至 `build/libs/`。

校对只保留在当前页面，不自动写入字库；切换默认/多字视图保留选择，修改原文后重新生成。候选开启时可用数字键 1–9 选择、方向键跳转，Esc 关闭候选。

字库已随仓库保存，普通构建无需抓取数据。复现字库：

```bash
python scripts/restore_research.py
python scripts/prepare_catalog.py --cache .research --output .research/rebuilt
python scripts/verify_catalog.py --rebuilt .research/rebuilt
```

## 许可

项目代码采用 [MIT](LICENSE)。数据资料保留各自许可及署名，包括 [Unicode 数据许可](licenses/Unicode-LICENSE.txt)、OpenCC 的 Apache-2.0 许可及其他来源条款，详见 [来源声明](NOTICE.md)。
