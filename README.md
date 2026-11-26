# 字镜 · GlyphLens

<img src="src/main/resources/logo.svg" width="64" alt="GlyphLens">

独立的 Halo 字形转换插件，处理单字繁简与正异字对应，支持默认转换、多字候选、词典消歧，以及超级管理员字库管理。

当前版本 **0.1.0**。开发接续与验证记录见[接续说明](docs/HANDOFF.md)。

## 转换边界

- 原创转换内核独立实现，不使用 CHAR 转换器或其汇总字库。前端复用维护者此前以 MIT 发布的页面与交互。
- 通用表处理与方向无关的字形，例如竖排标点；简体、繁体表分别补充方向关系，加载时合并为最终索引。
- **默认转换**取已收录候选中的首选；**多字候选**显示全部合法候选，例如 `【發|髮】`。前端默认选多字候选。
- 基础候选按使用等级排列：简体参考《通用规范汉字表》一级、二级、三级，繁体参考中国台湾地区教育主管部门常用、次常用、罕用字分级。同等级保留原字优先，其余以码点稳定排序；这不是逐字词频排名。自定义修正沿用管理员保存的顺序，词典消歧优先于默认首选。
- `乾/干` 等有依据的兼用关系已录入，保留原字作为候选；来源冲突和未证实的补充候选单列核查。
- 查不到就保留原字和原始编码；替换只基于原文计算一次，不作连锁替换，不对全文做宽泛的 NFKC。
- 带变体选择符、组合附加符或连接符的多码点字形整体保留；字库未定义这些组合时，不转换其基字，词典消歧也不会跨过它们匹配。
- 词典只能在现有字形候选中消歧。词汇转换能力单独预留，当前未启用。

## 已实现

- 公开页面、文本/UTF-8 文件转换、复制和下载，沿用 LemonSlice 博客的布局与三态外观切换。
- Unicode 码点分段索引及候选池，提前生成两种模式的结果。词典采用多模式匹配索引，优先最长词条，同长冲突保留候选。
- 通用、简体、繁体三表管理；同一资源版本保护三表，支持多选停用、Unicode 十六进制输入、来源查看、历史和导出。
- 超级管理员权限由服务端校验 `super-role`；待核查关系可在后台审阅。
- 词典消歧编辑及反馈队列；公开反馈只提交单字、建议和说明，不自动上传整段原文，也不直接改字库。
- 从旧插件读取两份自定义字库并原子迁移的接口；原记录、备注和历史保留，旧资源不修改。

## 数据来源

来源包括《通用规范汉字表》及其附表、Unicode Unihan、中国台湾地区教育主管部门《異體字字典》当前正字/异体索引、OpenCC 字符表。仅处理字符对应事实和来源标识，不复制释义或字形图片。

| 数据来源 | 用途与许可 |
| --- | --- |
| [《通用规范汉字表》（2013）及附表](https://www.moe.gov.cn/srcsite/A19/s229/201310/t20131015_159487.html) | 规范字等级、繁体与异体对应，保留教育部来源及序号。 |
| [Unicode Unihan 17.0.0](https://www.unicode.org/Public/17.0.0/ucd/Unihan.zip) | Unicode 字符关系；保留 [Unicode 数据许可](licenses/Unicode-LICENSE.txt)。 |
| [中国台湾地区教育主管部门《異體字字典》](https://dict.variants.moe.edu.tw/) | 正字与异体索引、常用等级，保留来源及字号供逐条核查。 |
| [OpenCC 字符表](https://github.com/BYVoid/OpenCC/tree/fae2a0a911c30764e669a7d8b0591cd8e852934a/data/dictionary) | 补充字符对应，不引入词组表；BYVoid 及贡献者，Apache-2.0。 |

资料保留各自条款和署名；项目 MIT 许可不替代数据来源的许可。具体版本与校验值见[来源清单](research/source-manifest.json)。

### 数据快照

本次取得 1,044 页官方索引；去重后正字记录 29,921 条、异体记录 74,383 条，能定位到 Unicode 的正异关系 24,327 条。未编码、仅有图片或私用字码等 50,056 条另列清单，不强行赋码。

当前通用表 38,881 条，简体方向 12,092 条、繁体方向 12,019 条；合并后分别为 50,973、50,900 条，**包括保持原字的记录**。两方向排序不同的记录分别保存在方向表中。待核查来源项分别为 5,925、5,813 条，不等同于这些记录全部未生效；已确定候选可用，补充候选待审阅。

- [待核查清单](research/pending-relations.csv)
- [未编码资料清单](research/unencoded-inventory.csv)
- [旧库审查索引](research/legacy-review-index.csv)：指向旧库源码位置，不打包旧汇总字库。
- [来源及校验清单](research/source-manifest.json)、[官方分页校验](research/official-pages-manifest.json)

## 构建

需要 Java 21、Node.js 24。Gradle Wrapper 随仓库提供，构建时安装 pnpm 12.16.1。

```bash
node --test tests/*.test.mjs
./gradlew build --no-daemon --console=plain
```

Windows 在仓库目录使用 `gradlew.bat build --no-daemon --console=plain`。安装包位于 `build/libs/`。

字库已随仓库保存，普通构建不用重新抓取来源。要复现整理结果：

```bash
python scripts/restore_research.py
python scripts/prepare_catalog.py --cache .research --output .research/rebuilt
python scripts/verify_catalog.py --rebuilt .research/rebuilt
```

GitHub Actions 会测试、构建并上传 `glyphlens` 安装包制品。

## 入口与许可

前台 `/tools/glyphlens/`，后台 `/console/glyphlens-dictionary`。旧 `/tools/char/` 入口跳转到字镜。博客工具菜单为「私厨 / FRP / 字镜」。

原创代码采用 [MIT](LICENSE)。数据来源见上文。仓库不含账号、令牌、Cookie、SSH 密钥、服务器实时修正历史或原始网页/PDF 缓存。
