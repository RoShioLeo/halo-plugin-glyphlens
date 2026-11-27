# 字镜默认字库

默认字库版本 **1.0.0**，由 `VERSION` 单独维护，与插件版本无关。当前定稿包含 12,718 个原字、12,787 条映射和 18 组上下文规则。

- `data/`：默认单字映射、消歧词典、来源及参考索引。
- `research/`：可复现输入、来源清单与编译定稿。
- `scripts/`：字库生成、校验和打包程序，只依赖 Python，不依赖 Halo 或插件构建。
- `releases/`：每个已定稿字库版本的数据校验清单。
- `dist/`：独立生成的字库 ZIP，不纳入源码。

以下目录和命令用于源码仓库中的 `dictionary/`。下载的 ZIP 是已生成的运行数据包，在后台“导入字库”中选择即可；自定义映射及包格式见随包的 `FORMAT.md`。

在源码的此目录运行：

```bash
python scripts/restore_research.py
python scripts/prepare_catalog.py --cache .research --output data
python scripts/verify_catalog.py --rebuilt data
python scripts/package_dictionary.py
```

输出 `dist/GlyphLens-Dictionary-1.0.0.zip`。同版本内容须与定稿清单一致；吸收新的手动修正、词典或来源数据时先更新 `VERSION`，核对后用 `python scripts/package_dictionary.py --write-manifest` 固化新版本。

自定义导入格式见[格式文档](https://github.com/RoShioLeo/halo-plugin-glyphlens/blob/main/docs/DICTIONARY-IMPORT.md)。
