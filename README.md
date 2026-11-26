# 字镜 · GlyphLens

<img src="src/main/resources/logo.svg" width="64" alt="GlyphLens">

独立的 Halo 字形转换插件，处理单字繁简与正异字对应，支持默认转换、多字候选、词典消歧，以及超级管理员字库管理。

**当前为开发快照 `0.1.0-dev.20261126`，尚未替换博客原插件。** 接着开发请先读 [接续说明](docs/HANDOFF.md)。

## 转换边界

- 原创转换内核独立实现，不使用 CHAR 转换器或其汇总字库。前端复用维护者此前以 MIT 发布的页面与交互。
- 通用表处理与方向无关的字形，例如竖排标点；简体、繁体表分别补充方向关系，加载时合并为最终索引。
- **默认转换**取已收录候选中的首选；**多字候选**显示全部合法候选，例如 `【發|髮】`。前端默认选多字候选。
- `乾/干` 等有依据的兼用关系已录入，保留原字作为候选；来源冲突和未证实的补充候选单列核查。
- 查不到就保留原字和原始编码；替换只基于原文计算一次，不作连锁替换，不对全文做宽泛的 NFKC。
- 词典只能在现有字形候选中消歧。词汇转换能力单独预留，当前未启用。

## 已实现

- 公开页面、文本/UTF-8 文件转换、复制和下载，沿用 LemonSlice 博客的布局与三态外观切换。
- Unicode 码点分段索引及候选池，提前生成两种模式的结果。词典采用多模式匹配索引，优先最长词条，同长冲突保留候选。
- 通用、简体、繁体三表管理；同一资源版本保护三表，支持多选停用、Unicode 十六进制输入、来源查看、历史和导出。
- 超级管理员权限由服务端校验 `super-role`；待核查关系可在后台审阅。
- 词典消歧编辑及反馈队列；公开反馈只提交单字、建议和说明，不自动上传整段原文，也不直接改字库。
- 从旧插件读取两份自定义字库并原子迁移的接口；原记录、备注和历史保留，旧资源不修改。

## 数据快照

来源包括《通用规范汉字表》及其附表、Unicode Unihan、教育部《異體字字典》当前正字/异体索引、OpenCC 字符表。仅处理字符对应事实和来源标识，不复制释义或字形图片。

本次取得 1,044 页官方索引；去重后正字记录 29,921 条、异体记录 74,383 条，能定位到 Unicode 的正异关系 24,327 条。未编码、仅有图片或私用字码等 50,056 条另列清单，不强行赋码。

当前通用表 39,711 条，简体方向 11,262 条、繁体方向 11,189 条；合并后分别为 50,973、50,900 条，**包括保持原字的记录**。待核查来源项分别为 5,925、5,813 条，不等同于这些记录全部未生效；已确定候选可用，补充候选待审阅。

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

GitHub Actions 会测试、构建并上传 `glyphlens-dev` 安装包制品。

## 入口与许可

规划前台 `/tools/glyphlens/`，后台 `/console/glyphlens-dictionary`。旧 `/tools/char/` 入口预留跳转。博客工具菜单计划为「私厨 / FRP / 字镜」，待新插件线上验证后切换。

原创代码采用 [MIT](LICENSE)。资料及第三方组件保留各自原有条款和署名，不能将全部来源资料一概宣称为 MIT；详见 [来源与许可](NOTICE.md)。仓库不含账号、令牌、Cookie、SSH 密钥、服务器实时修正历史或原始网页/PDF 缓存。
