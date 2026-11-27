# 字镜开发与发布接续说明

当前版本 **0.2.0**，插件 ID `lemonslice-glyphlens`。前台 `/tools/glyphlens/`，后台 `/console/glyphlens-dictionary`。

## 当前范围

- 用户明确移除转繁体及其字库。原通用表和简体表已合并，前后台、词典、反馈、API 均不再区分方向。
- 默认字库 `data/dictionary.json`：12,656 条有效映射、68 条多候选；`data/reviews.json`：62 项待审。没有 `common.json`、`v2s.json` 或 `v2t.json`。
- 发布前实际启用的简体映射和本次确认结果进入默认数据。`research/maintainer-decisions.json` 保存 163 项确认关系及 5 项不采纳关系，不包含账户或后台操作历史。
- 特别保留顺序：彷／仿、画／划、劄／札；麼仅转么；乾沿用后台的干／乾。
- 参考资料仅补空缺；待审候选不能指向未核实的繁体字。不得为了清理待审而激活原先冲突的参考映射。

## 运行与后台

默认转换取首选；多字候选正文逐处校对，复制下载输出所选正文。未知字符、多码点字形和原始换行保留。词典仅从已有候选消歧，不翻译词汇。

统一 API：`/apis/console.api.glyphlens.lemonslice.cloud/v1alpha1/dictionary`；子路径 `entries`、`batch`、`history`、`export`。只允许超级管理员管理。公开字库 `/tools/glyphlens/dictionary.json`。旧方向接口及 CHAR 迁移接口已移除。

配置名为 `lemonslice-glyphlens-dictionary`、`lemonslice-glyphlens-lexicon`、`lemonslice-glyphlens-feedback`。字库读写同一资源并检查版本。停用不转换，启用恢复保存的候选与备注，重置才恢复默认关系。

## 重新安装与验证

用户明确授权升版后卸载重装，并允许清空旧数据；不迁移 0.1.x 方向字库、审核或修改历史。当前有效修正必须先在默认字库验证后再移除旧版。旧 `lemonslice-char` 数据不得再次导入。

不改文章、主题、私厨或 LensMD。GitHub 源码、Release 附件及服务器安装包需一致。

Java 21、Node 24，Gradle Wrapper 9.4.0。构建 `./gradlew build --no-daemon --console=plain`，产物 `build/libs/GlyphLens-0.2.0.jar`。验证 Node/DOM、Python 策略、Java 权限/存储测试和可复现数据；安装后检查默认字库、空修正/历史、旧方向接口 404、前后台资源与 JAR 字节一致。
