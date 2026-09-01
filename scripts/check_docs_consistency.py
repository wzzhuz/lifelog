#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
检查文档里是否残留与当前实现矛盾的说法。

存在的理由：改代码时往往只改代码，文档里的「为什么不用 Room」
这类段落会一直留着，直到某天被人读到才发现已经错了半年。
本项目已经发生过一次（README 和技术设计里都写着不用 Room，
而代码早就迁移到 Room 了）。

这是关键词级的粗筛，不是语义理解。
它能拦住「明确的技术选型被推翻但文档没改」这类问题，
拦不住更细微的措辞偏差。

用法：python3 scripts/check_docs_consistency.py
退出码 0 = 没问题，1 = 发现可疑
"""
import os
import re
import sys

# 一句话里出现这些词，说明是在讲历史，不是在陈述现状 → 不算矛盾
# 否则「初版刻意避开 Room，后来迁移了」这种正确叙述也会被误报
HISTORICAL = ('初版', '已迁移', '曾', '早期', '过去', '旧', '之前', '原来', '当时')

# (正则, 说明, 适用的文件)
# 文件用前缀匹配：None 表示全部文件
RULES = [
    (r'持久化\s*\|\s*JSON', '技术栈表仍写 JSON 持久化（应为 Room）', None),
    (r'为什么不用\s*Room', '仍保留「为什么不用 Room」章节（该选型已推翻）', None),
    (r'不引入\s*Room|刻意避开\s*Room|刻意不引入.*Room',
     '仍声称刻意不用 Room', None),
    (r'省掉注解处理器（KSP）能显著', '仍以「省掉 KSP」为理由（Room 已引入 KSP）', None),
    (r'模板库?\s*24\s*个|模板里有\s*24', '模板数量仍是 24（实际 34）', None),
    (r'最短间隔', '仍提到「最短间隔」（详情页统计块已无此项）', None),
    (r'完整时间线', '仍称时间线为「完整」（现在是分页加载）', None),
    # 稳定决策已并入 project.md，不该再出现在 docs 里
    (r'为什么不用\s*(Coil|Glide|Hilt|Koin)',
     '该决策已并入 project.md，docs 里不应再单独保留', 'docs/'),
]

# 技术设计/需求设计已拆并进 specs、project.md、README 架构速览，
# 这里只检查仍存在的文档
DOCS = ['README.md', 'docs/使用说明.md', 'openspec/project.md']


def is_historical(line):
    return any(w in line for w in HISTORICAL)


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    found = []
    for d in DOCS:
        p = os.path.join(root, d)
        if not os.path.exists(p):
            continue
        with open(p, encoding='utf-8') as fh:
            for i, line in enumerate(fh, 1):
                for pattern, desc, scope in RULES:
                    if scope and not d.startswith(scope):
                        continue
                    if not re.search(pattern, line):
                        continue
                    if is_historical(line):
                        continue
                    found.append((d, i, desc, line.strip()[:70]))
    if found:
        print('发现 %d 处文档与实现可能矛盾：' % len(found))
        for d, n, desc, txt in found:
            print('  %s:%d  [%s]' % (d, n, desc))
            print('      %s' % txt)
        sys.exit(1)
    print('OK 文档一致性检查通过')
    sys.exit(0)


if __name__ == '__main__':
    main()
