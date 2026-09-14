#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# 按「保留最近 N 个」清理 GitHub Actions 制品
#
# GitHub 原生只支持按天数保留（retention-days），做不到「只留最新 N 个」。
# 配额又是账号级共享的（Free 计划 500MB，与 Packages 共用），
# 每次 push 都产包的项目几天就打满，之后所有上传步骤会被服务端直接拒绝。
#
# 用法：
#   cleanup-artifacts.sh --pattern 'lifelog-v*' --keep 5 [--older-than 0] [--dry-run]
#
# 参数：
#   --pattern     制品名 glob，必填（如 'lifelog-v*'、'*-preview-*'）
#   --keep        保留最近几个，必填（0 表示全部删除）
#   --older-than  只清理 N 天前的，默认 0（不限，只看 keep）
#   --dry-run     只列出不删除
#   --repo        owner/name，默认取当前仓库
#
# 依赖：gh、jq（GitHub 托管 runner 自带）
# 权限：需要 GH_TOKEN 且具备 actions: write
# ---------------------------------------------------------------------------
set -euo pipefail

PATTERN=""; KEEP=""; OLDER_DAYS=0; DRY_RUN=false
REPO="${GITHUB_REPOSITORY:-}"

die() { echo "❌ $*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --pattern)    PATTERN="$2";    shift 2 ;;
    --keep)       KEEP="$2";       shift 2 ;;
    --older-than) OLDER_DAYS="$2"; shift 2 ;;
    --dry-run)    DRY_RUN=true;    shift   ;;
    --repo)       REPO="$2";       shift 2 ;;
    *)            die "未知参数：$1" ;;
  esac
done

[[ -n "$PATTERN" ]] || die "缺少 --pattern"
[[ -n "$KEEP"    ]] || die "缺少 --keep"
[[ -n "$REPO"    ]] || die "缺少 --repo（或设置 GITHUB_REPOSITORY）"
command -v gh >/dev/null || die "找不到 gh"
command -v jq >/dev/null || die "找不到 jq"

mb() { awk -v b="$1" 'BEGIN { printf "%.1f", b/1048576 }'; }

echo "仓库：$REPO"
echo "匹配：$PATTERN    保留最近：$KEEP 个    仅清理 ${OLDER_DAYS} 天前：$( [[ $OLDER_DAYS -gt 0 ]] && echo 是 || echo 否 )"
[[ "$DRY_RUN" == true ]] && echo "模式：预演（不实际删除）"
echo ""

CUTOFF=0
[[ $OLDER_DAYS -gt 0 ]] && CUTOFF=$(date -u -d "-${OLDER_DAYS} days" +%s)

# API 不支持按名字过滤，只能全量拉回本地匹配；--paginate 自动翻页
mapfile -t ROWS < <(
  gh api --paginate "repos/${REPO}/actions/artifacts" \
     --jq '.artifacts[] | select(.expired==false)
           | "\(.created_at)\t\(.id)\t\(.size_in_bytes)\t\(.name)"' \
  | sort -r
)

# 先按 glob 过滤，再按时间倒序（sort -r 已保证），跳过前 KEEP 个
matched=0; del_cnt=0; del_bytes=0
LOG=""

while IFS=$'\t' read -r created id size name; do
  [[ -z "${id:-}" ]] && continue
  # shellcheck disable=SC2254
  case "$name" in
    $PATTERN) ;;
    *) continue ;;
  esac
  matched=$((matched + 1))

  if [[ $matched -le $KEEP ]]; then
    printf '  [保留] %s  %6.1fMB  %s\n' "${created:0:16}" "$(mb "$size")" "$name"
    continue
  fi

  if [[ $OLDER_DAYS -gt 0 ]]; then
    ts=$(date -u -d "$created" +%s)
    [[ $ts -gt $CUTOFF ]] && { printf '  [未满期] %s  %s\n' "${created:0:16}" "$name"; continue; }
  fi

  mb=$(mb "$size")
  if [[ "$DRY_RUN" == true ]]; then
    printf '  [可删] %s  %6.1fMB  %s\n' "${created:0:16}" "$mb" "$name"
  else
    if gh api -X DELETE "repos/${REPO}/actions/artifacts/${id}" >/dev/null 2>&1; then
      printf '  [已删] %s  %6.1fMB  %s\n' "${created:0:16}" "$mb" "$name"
    else
      printf '  [失败] %s  %s（id=%s）\n' "${created:0:16}" "$name" "$id"
    fi
  fi
  del_cnt=$((del_cnt + 1)); del_bytes=$((del_bytes + size))
done <<< "$(printf '%s\n' "${ROWS[@]}")"

MB=$(mb "$del_bytes")
echo ""
if [[ "$DRY_RUN" == true ]]; then
  echo "预演结果：命中 $matched 个，可删 $del_cnt 个，可释放 ${MB} MB"
else
  echo "清理完成：命中 $matched 个，删除 $del_cnt 个，释放 ${MB} MB"
fi

# 写进 Job Summary，运行页直接可见，不用翻日志
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "### 制品清理：\`$PATTERN\`"
    echo ""
    echo "- 保留最近：$KEEP 个"
    echo "- 命中：$matched 个"
    echo "- $( [[ $DRY_RUN == true ]] && echo 可删 || echo 已删 )：$del_cnt 个"
    echo "- $( [[ $DRY_RUN == true ]] && echo 可释放 || echo 已释放 )：**${MB} MB**"
  } >> "$GITHUB_STEP_SUMMARY"
fi
