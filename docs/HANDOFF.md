# 字镜开发与发布接续说明

当前插件版本 **0.2.3**，默认字库版本 **1.0.0**，插件 ID `lemonslice-glyphlens`。前台 `/tools/glyphlens/`，后台 `/console/glyphlens-dictionary`。

发布与安装使用当前提交的 GitHub CI 产物，核对 JAR、独立字库及校验清单一致后，通过常规插件升级安装。

本地来源索引仅保留实际字形替换的原始资料与归并路径，移除自保留候选的来源记录和重复确认索引。独立的同码位原字映射不收录；合法多候选里的原字、不同 Unicode 码位的规范合一映射保留。默认字库只收确定关系。

README 与后台来源表仅保留“来源 / 收录字数 / 映射条数”三列；手动维护不逐字列举，去掉出处待核和审核说明。公开介绍保持简洁。前台文件导入、示例、模式记忆和校对交互沿用已实现的行为；页脚明确显示“插件 v0.2.3 · 字库 v1.0.0”。

## 当前范围

- 用户明确移除转繁体及其字库。原通用表和简体表已合并，前后台、词典、反馈、API 均不再区分方向。
- 默认字库位于 `dictionary/data/dictionary.json`：12,718 个原字、12,787 条候选对应、69 个多候选字；`dictionary/data/reviews.json` 为 0 项待审。没有 `common.json`、`v2s.json` 或 `v2t.json`。
- `dictionary/research/maintainer-decisions.json` 是编译时的候选定稿输入，不作为资料来源，也不生成运行时审核索引。
- 特别保留顺序：䌷／绸、彷／仿、画／划、劄／札；硷只转碱、麼仅转么；乾沿用后台的干／乾。
- 参考资料仅补空缺；待审候选不能指向未核实的繁体字。不得为了清理待审而激活原先冲突的参考映射。

## 运行与后台

默认采用词典唯一命中的候选，否则取首选；手动选择优先。多字候选模式持续显示下划线标记，点击标记可进入对应位置校对；也可点击结果操作栏最右的主按钮“开始校对”逐处检查。结束校对隐藏工具栏、选字区，保留标记和选择。上一处、下一处使用箭头图标，“确认剩余”置于最右。模式旁的圆圈问号支持鼠标悬停、手机点击、键盘聚焦查看说明；页脚致谢右对齐。

词典命中不自动标记已校对，用户逐处选择或“确认剩余”才确认；批量确认保持当前显示字形，仍可修改。复制下载输出所选正文。未知字符、多码点字形和原始换行保留。

词典按原字、候选字和简体上下文词条维护。默认 `dictionary/data/lexicon.json` 含 9 个原字、18 组规则、75 个词条；默认规则可编辑、停用。先查单字，仅多候选位置查按原字建的上下文索引；上下文允许匹配原字和已确认字形，不连锁替换。长上下文优先，同长度冲突保留人工校对，不作词汇翻译。旧等长结果规则只作兼容读取，显式保存后转为新模型。

“字库版本”页的“收录字数 / 映射条数”由生成程序自动计算，读取默认快照，不含后台实时增删。全库总计按整库去重，不相加各来源字数。每个对应只归一类，同字可跨来源。README 与同版本统计一致。页面说明面向历史文献整理、字库试运行；页脚右侧、字库版本页与 README 致谢使用“感谢 @小兽KEMONO 提供技术支持”。反馈说明提示为“建议提供权威来源，便于审核时参考。”

统一 API：`/apis/console.api.glyphlens.lemonslice.cloud/v1alpha1/dictionary`；子路径 `entries`、`batch`、`history`、`export`、`package`、`import/preview`、`import`。只允许超级管理员管理。公开字库 `/tools/glyphlens/dictionary.json`，默认版本元数据 `/tools/glyphlens/version.json`。旧方向接口及 CHAR 迁移接口已移除。

配置名为 `lemonslice-glyphlens-dictionary`、`lemonslice-glyphlens-lexicon`、`lemonslice-glyphlens-feedback`。字库读写同一资源并检查版本。停用不转换，启用恢复保存的候选与备注，重置才恢复默认关系。

字库源码、研究输入、生成脚本均独立到同仓库的 `dictionary/`。`dictionary/VERSION` 管理默认字库，`dictionary/releases/1.0.0.json` 固化六个数据文件的校验值。同版本不复用不同默认数据；站点修改先留站点，定期审核吸收到下一版。`python dictionary/scripts/package_dictionary.py` 独立生成 `dictionary/dist/GlyphLens-Dictionary-1.0.0.zip`，无需构建插件。

“导入字库”统一识别默认 ZIP、结构化快照 JSON 和裸映射 JSON；先预览，再检查同一文件哈希与配置版本后原子写入。ZIP 更新默认数据及词典，保留站点修正；JSON 只合并明确列出的映射，保留未提供的备注，不改默认版本。导出快照包括停用项保存候选，不含账号、反馈、操作历史。完整格式文档 `docs/DICTIONARY-IMPORT.md` 随 ZIP 以 `FORMAT.md` 提供。默认包保留各来源许可。

香港来源入口与首批 21 字核查见 `docs/HK-SOURCES.md`、`docs/HK-REVIEW.md`。能确定码位的拟议关系均已收录，新增映射为 0；9 项可补原始出处、4 项仍需核查图片身份或证据方向，只是来源补证，不要求维护者重复审核现有关系。香港来源记录未加入默认数据，竃的手动来源状态不变。真正新增、冲突或改变候选顺序的项目再另列确认。

## 重新安装与验证

0.2.0 已按用户授权卸载重装并清空旧数据，不导入 0.1.x 方向字库或历史。后续版本正常升级，保留现有统一字库、词典和反馈资源。旧 `lemonslice-char` 数据不得再次导入。

不改文章、主题、私厨或 LensMD。GitHub 源码、Release 附件及服务器安装包需一致。

Java 21、Node 24，Gradle Wrapper 9.4.0。构建 `./gradlew build --no-daemon --console=plain`，产物 `build/libs/GlyphLens-0.2.3.jar`。验证 Node/DOM、Python 策略、Java 权限/存储测试、可复现数据和来源统计；升级后核对默认字库、消歧规则、资源保留和前后台资源哈希。
