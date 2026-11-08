#!/usr/bin/env bash
# Пошаговые уведомления о сборке в Telegram (через Bot API).
#   tg_step.sh step  "<этап>" "<html-текст>"   тихое сообщение о шаге, запоминает этап (TG_NOTE = доп. строка)
#   tg_step.sh final "<html-текст>"             громкое сообщение (итог)
#   tg_step.sh fail  "<файл лога>"              сообщение о падении с последними ошибками
# Нужны секреты TG_BOT_TOKEN и TG_CHAT_ID. Без них скрипт ничего не делает.
# Скрипт никогда не роняет сборку.
set -u

mode="${1:-}"
[ "$#" -gt 0 ] && shift

TG_HOST="api.telegram.org"
esc() { sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g'; }

short_sha="${GITHUB_SHA:-}"
short_sha="${short_sha:0:7}"
footer="<code>${short_sha}</code> · <a href=\"${RUN_URL:-}\">лог сборки</a>"

send() { # $1 = html-текст, $2 = тихо (true/false)
  if [ "${TG_ENABLED:-true}" != "true" ]; then
    echo "Telegram: уведомления выключены для этой сборки"
    return 0
  fi
  if [ -z "${TG_BOT_TOKEN:-}" ] || [ -z "${TG_CHAT_ID:-}" ]; then
    echo "Telegram: TG_BOT_TOKEN / TG_CHAT_ID не заданы — пропуск"
    return 0
  fi
  local resp="${RUNNER_TEMP:-/tmp}/tg_resp.json"
  local code
  code=$(curl -sS -m 20 -o "$resp" -w '%{http_code}' \
    "https://${TG_HOST}/bot${TG_BOT_TOKEN}/sendMessage" \
    --data-urlencode "chat_id=${TG_CHAT_ID}" \
    --data-urlencode "text=$1" \
    --data-urlencode "parse_mode=HTML" \
    --data-urlencode "disable_web_page_preview=true" \
    --data-urlencode "disable_notification=$2") || code="curl-error"
  echo "Telegram: $code"
  if [ "$code" != "200" ]; then cat "$resp" 2>/dev/null; echo; fi
  return 0
}

case "$mode" in
  step)
    stage="${1:-}"
    text="${2:-}"
    if [ -n "${GITHUB_ENV:-}" ]; then echo "TG_STAGE=${stage}" >> "$GITHUB_ENV"; fi
    note=""
    if [ -n "${TG_NOTE:-}" ]; then
      note=$'\n'"<i>$(printf '%s' "$TG_NOTE" | head -n 1 | cut -c1-200 | esc)</i>"
    fi
    send "${text}${note}"$'\n'"${footer}" true
    ;;
  final)
    send "${1:-}"$'\n'"${footer}" false
    ;;
  fail)
    log="${1:-}"
    msg="❌ <b>Сборка упала</b> на этапе: $(printf '%s' "${TG_STAGE:-подготовка}" | esc)"
    if [ -n "$log" ] && [ -f "$log" ]; then
      errs=$(sed -e 's/\x1b\[[0-9;]*m//g' "$log" \
        | grep -E 'error:|FAILED|What went wrong|Exception|BUILD FAILED' \
        | sed -E 's#/home/runner/work/[^/]+/[^/]+/##g; s/^[[:space:]]+//' \
        | awk '!seen[$0]++' | head -n 12 | cut -c1-220)
      if [ -n "$errs" ]; then
        msg="${msg}"$'\n\n'"<pre>$(printf '%s' "$errs" | esc)</pre>"
      fi
    fi
    msg="${msg}"$'\n'"Ошибка уже в репо — просто напиши «проверь»."
    send "${msg}"$'\n'"${footer}" false
    ;;
  *)
    echo "usage: tg_step.sh step|final|fail ..."
    ;;
esac
exit 0
