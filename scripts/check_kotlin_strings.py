#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
检查 Kotlin 源码里被真实换行打断的字符串字面量。

存在的理由：用 Python 脚本生成/拼接 Kotlin 代码时，
源码里的 \\n 会被 Python 当成转义处理成真实换行，
导致 Kotlin 字符串字面量跨行断开——而 Kotlin 不允许，
编译会报一大串 "Expecting a top level declaration"，
错误信息完全指不到真正原因。

这个问题已经犯过两次（都是使用说明页），所以加个检查。
用法：python3 scripts/check_kotlin_strings.py
退出码 0 = 没问题，1 = 发现问题
"""
import os
import re
import sys

SKIP_SUFFIX = ('+', ',', '(', '{', '&&', '||', '?', ':')


def scan(root):
    issues = []
    for dp, dn, fn in os.walk(root):
        if '.git' in dp:
            continue
        for f in fn:
            if not f.endswith('.kt'):
                continue
            p = os.path.join(dp, f)
            with open(p, encoding='utf-8') as fh:
                for i, line in enumerate(fh, 1):
                    st = line.rstrip()
                    if '"' not in st:
                        continue
                    # 三引号 raw string（SQL 多行查询）是合法的，跳过
                    if '"""' in st:
                        continue
                    if '//' in st:
                        continue
                    if st.lstrip().startswith('*'):
                        continue
                    # 先去掉字符字面量（如 it == '"'）再计数，否则误报
                    without_chars = re.sub(r"'\\?.'", '', st)
                    # 再去掉转义的引号
                    unescaped = without_chars.replace('\\"', '')
                    if unescaped.count('"') % 2 == 0:
                        continue
                    if st.endswith(SKIP_SUFFIX):
                        continue
                    issues.append((p, i, st.strip()[:80]))
    return issues


if __name__ == '__main__':
    root = sys.argv[1] if len(sys.argv) > 1 else 'app/src/main/java'
    found = scan(root)
    if found:
        print('发现 %d 处疑似被换行打断的字符串：' % len(found))
        for p, n, txt in found:
            print('  %s:%d  %s' % (p, n, txt))
        sys.exit(1)
    print('OK 未发现被换行打断的字符串')
    sys.exit(0)
