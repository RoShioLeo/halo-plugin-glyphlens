# 字镜开发与发布接续说明

当前版本 **0.2.2**，插件 ID `lemonslice-glyphlens`。前台 `/tools/glyphlens/`，后台 `/console/glyphlens-dictionary`。

## 当前范围

- 用户明确移除转繁体及其字库。原通用表和简体表已合并，前后台、词典、反馈、API 均不再区分方向。
- 默认字库 `data/dictionary.json`：12,718 个原字、12,787 条候选对应、69 个多候选字；`data/reviews.json` 为 0 项待审。没有 `common.json`、`v2s.json` 或 `v2t.json`。
- 所有确认结果进入默认数据。`research/maintainer-decisions.json` 保存 225 项确认关系及 5 项不采纳关系，不包含账户或后台操作历史。
- 特别保留顺序：䌷／绸、彷／仿、画／划、劄／札；硷只转碱、麼仅转么；乾沿用后台的干／乾。
- 参考资料仅补空缺；待审候选不能指向未核实的繁体字。不得为了清理待审而激活原先冲突的参考映射。

## 运行与后台

默认采用词典唯一命中的候选，否则取首选；手动选择优先。多字候选模式持续显示下划线标记，点击标记可进入对应位置校对；也可点击结果操作栏最右的主按钮“开始校对”逐处检查。结束校对隐藏工具栏、选字区，保留标记和选择。上一处、下一处使用箭头图标，“确认剩余”置于最右。转换方式旁的圆圈问号支持鼠标悬停、手机点击、键盘聚焦查看说明；页脚致谢右对齐。

词典命中不自动标记已校对，用户逐处选择或“确认剩余”才确认；批量确认保持当前显示字形，仍可修改。复制下载输出所选正文。未知字符、多码点字形和原始换行保留。

词典按原字、候选字和简体上下文词条维护。默认 `data/lexicon.json` 含 9 个原字、18 组规则、75 个词条；默认规则可编辑、停用。先查单字，仅多候选位置查按原字建的上下文索引；上下文允许匹配原字和已确认字形，不连锁替换。长上下文优先，同长度冲突保留人工校对，不作词汇翻译。旧等长结果规则只作兼容读取，显式保存后转为新模型。

资料来源页的各来源原字数、候选对应数由生成程序自动计算，读取默认快照，不含后台实时增删。每个对应只归一类，同字可跨来源。README 与同版本统计一致。页面说明面向历史文献整理、字库试运行；页脚右侧、资料来源页与 README 致谢使用“感谢 @小兽KEMONO 提供技术支持”。

统一 API：`/apis/console.api.glyphlens.lemonslice.cloud/v1alpha1/dictionary`；子路径 `entries`、`batch`、`history`、`export`。只允许超级管理员管理。公开字库 `/tools/glyphlens/dictionary.json`。旧方向接口及 CHAR 迁移接口已移除。

配置名为 `lemonslice-glyphlens-dictionary`、`lemonslice-glyphlens-lexicon`、`lemonslice-glyphlens-feedback`。字库读写同一资源并检查版本。停用不转换，启用恢复保存的候选与备注，重置才恢复默认关系。

## 重新安装与验证

0.2.0 已按用户授权卸载重装并清空旧数据，不导入 0.1.x 方向字库或历史。后续版本正常升级，保留现有统一字库、词典和反馈资源。旧 `lemonslice-char` 数据不得再次导入。

不改文章、主题、私厨或 LensMD。GitHub 源码、Release 附件及服务器安装包需一致。

Java 21、Node 24，Gradle Wrapper 9.4.0。构建 `./gradlew build --no-daemon --console=plain`，产物 `build/libs/GlyphLens-0.2.2.jar`。验证 Node/DOM、Python 策略、Java 权限/存储测试、可复现数据和来源统计；升级后核对默认字库、消歧规则、资源保留和前后台资源哈希。
