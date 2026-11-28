# 字镜源码与文档

安装、使用和构建命令见[项目 README](../README.md)。默认字库在同一仓库中独立维护和打包。

| 目录 | 用途 |
| --- | --- |
| `src/main/java/` | Halo 插件、接口、权限、字库与词典存储 |
| `src/main/resources/` | 插件元数据、标识与权限配置 |
| `src/test/` | Java 接口、导入与存储测试 |
| `web/` | 公开转换与正文校对页面 |
| `ui/src/` | Halo Console 的字库、词典、来源和反馈管理 |
| `ui/pnpm-workspace.yaml` | 扁平依赖布局与项目内缓存，兼容 Windows 工作区 |
| `dictionary/data/` | 随插件发布的默认字库、词典与来源数据 |
| `dictionary/research/` | 可复现输入、来源清单与收录决定 |
| `dictionary/scripts/` | 字库复现、校验和打包工具 |
| `tests/` | 转换、校对与公开数据测试 |
| `build/`、`ui/build/`、`dictionary/dist/` | 构建产物，由 Git 忽略 |
| `.research/`、`workplace/` | 本地研究缓存与开发实例数据，由 Git 忽略 |

- [字库项目与版本](../dictionary/README.md)
- [主题规范与字镜适配](design-system.md)
- [收录规则与数据说明](DICTIONARY.md)
- [字库文件格式与导入导出](DICTIONARY-IMPORT.md)
- [词典消歧](LEXICON.md)
- [开发与部署交接记录](HANDOFF.md)
- [香港资料来源](HK-SOURCES.md)与[首批核查](HK-REVIEW.md)

`dictionary/` 使用本仓库的 Git 历史与自身的数据版本；插件版本和字库版本分别维护。站点凭据与线上运行报告由站点工作区管理。
