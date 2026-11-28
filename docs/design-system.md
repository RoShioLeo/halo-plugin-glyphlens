# 字镜主题适配

完整规则见[博客主题与界面规范](https://github.com/RoShioLeo/halo-theme-lemonglow/blob/main/docs/design-system.md)。工作区实施记录位于 `../../../docs/theme/plan.md`；本轮统一已于 2026-11-28 获得确认。本页记录字镜的适配关系。

## 角色映射

| 统一角色 | 主题变量 | 字镜现有变量 |
| --- | --- | --- |
| 页面与辅助表层 | `--ls-paper`、`--ls-surface` | `--paper`、`--surface` |
| 主要与辅助文字 | `--ls-ink`、`--ls-muted` | `--ink`、`--muted` |
| 分割线与控件边框 | `--ls-faint`、`--ls-control-border` | `--line`、`--input-border` |
| 品牌、链接与焦点 | `--ls-brand-accent`、`--ls-link`、`--ls-focus-ring` | `--accent` |
| 主按钮背景、文字与悬停 | `--ls-primary-bg`、`--ls-primary-ink`、`--ls-primary-hover` | `--primary`、`--primary-ink`、`--primary-hover` |
| 字体与品牌字重 | `--ls-font-sans` 与品牌字重角色 | `--font-sans`、产品标题及英文眉题 |

核心颜色由 `design/tokens.json` 的同步快照生成，公开页面与 Console 使用各自的 CSS 快照，避免独立修改常量。

## 页面与组件

保留桌面原文与结果双栏、手机单栏、转换、候选和校对的业务结构。普通按钮、输入框、反馈弹窗、帮助浮层、模式选择和外观入口按主规范接入尺寸、间距、圆角及交互状态。

正文中的候选字是内联排版标记，保留逐字定位与文本选择；外围独立操作使用统一的控件和手机热区。公共手机断点统一为 760px。

公开页面入口为 `web/styles.css` 与 `web/appearance.js`，后台入口为 `ui/src/`。保留字镜的产品图标及浅深色图形资源；字库、词典内容和转换规则按其各自文档维护。

构建执行 `node design/generate.mjs --project glyphlens --check`，工作区通过 `node scripts/sync-design-tokens.mjs` 同步三个仓库。生成的 CSS 不手动修改。本轮保持 0.2.4，后续样式修改保持届时的语义版本，可用 build 提前上线，修正记录累计到下次正式 Release。
