# 香港字形资料线索

核查日期：2026-11-27。本页整理候选资料及适用范围，不表示已将这些资料的关系导入字库。

| 资料 | 一手入口 | 对本项目的作用 | 当前核查程度 |
| --- | --- | --- | --- |
| 香港教育局《香港小學學習字詞表》 | [电子检索](https://www.edbchinese.hk/lexlist_ch/) · [官方介绍](https://www.edb.gov.hk/attachment/tc/curriculum-development/kla/chi-edu/resources/primary/lang/curriculum-materials/int_lexical%20list_TC_2026.pdf) | 官方说明指出字头列出建议字形、常见异体字和简化字。适合查具体单字关系；词条内容不直接作为词汇转换规则。 | 已核对首批 21 个指定字，整理 9 项可补来源的关系和 4 项需先辨认字形或方向的项目，见[首批核查表](HK-REVIEW.md)，均未并入默认数据。 |
| 《常用字字形表》（2007 年重排本，2012 年附粤普字音及英文解释）及附录《異體字表》 | [教育局官方说明](https://www.edb.gov.hk/attachment/tc/curriculum-development/kla/chi-edu/resources/primary/lang/curriculum-materials/ziXingBiao_jianJie_TC.pdf) | 重排本以音义完全相同作为检视异体字的标准，附编原版异体字表。适合作为独立字形证据；须区分重排本筛选结果与原版附表。 | 已找到官方说明；尚未取得、逐项核对完整附表，不能据介绍文件生成映射。 |
| 《香港電腦漢字參考字形》（2016） | [共通中文界面官方页面](https://www.ccli.gov.hk/tc/download/reference_glyphs.html) | 尤其可参考附表五“香港字形与大五码／台宋字形的差异”、附表六“大五码问题字符”。用于辨明印刷风格、同一码位不同字样及真正的码位对应。 | 已核实内容目录和用途；并非日常用字的强制规范。尚未将附表资料导入字库。 |
| 《香港電腦漢字楷體字形參考指引》《香港電腦漢字宋體（印刷體）字形參考指引》 | [官方介绍](https://www.ccli.gov.hk/tc/download/glyphs_guidelines.html) | 从部件、笔画及楷宋差异核对字形，帮助解释字体显示差异；不能把部件写法差异直接扩成单字替换。 | 已核实官方介绍；下载页面另有使用条款，未点击接受或下载受条款约束的文件。 |
| 《香港增補字符集—2016》相关数据 | [政府开放数据说明](https://data.gov.hk/tc-data/dataset/hk-dpo-dpo_hp-hong-kong-supplementary-character-set-related-information) · [JSON](https://www.digitalpolicy.gov.hk/open_data/ccli/HKSCS2016.json) · [字段说明](https://www.digitalpolicy.gov.hk/open_data/ccli/hkscs2016-data-dictionary-tc.pdf) | 提供码位、字符、首次收录版本、仓颉码、粤拼与 H-Source 编号。可追查香港编码字源；JSON 本身没有异体到规范字的转换字段。 | 已核实数据结构和字段含义，未当作转换表导入。 |

建议先核对教育局逐字资料和《异体字表》，再以电脑字形资料排除字体风格差异。遇到仍在使用的正字或音义有差别的对应，保留原字选项并交维护者判断，不扩大类推。

首批与默认字库 1.0.0 对照，能够确定码位的拟议对应均已收录，新增映射为 0。首批表格用于补证来源，不是新增字形的待审清单；未确定码位的图片仍保留为研究线索。

`竃 → 灶` 由维护者手工补充，目前仍标“手动维护来源”。维护者明确不采用 2015/03 域名字符工作文件作为该关系来源；该工作文件的表格不进入来源索引。待其他独立文献能够确认此具体关系后，再补充链接、版本和位置。
