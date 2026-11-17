#!/usr/bin/env bash
# Запуск из корня проекта ПОСЛЕ распаковки этого набора поверх проекта:
#   unzip -o cpp-module-kit.zip && bash apply_cpp_module.sh
set -euo pipefail

SRC=app/src/main/cpp
DST=cpp/src/main/cpp

[ -d "$SRC" ] || { echo "Нет $SRC (уже перенесено?)"; exit 1; }
[ ! -e "$DST" ] || { echo "$DST уже существует"; exit 1; }

mkdir -p cpp/src/main
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git mv "$SRC" "$DST"
else
  mv "$SRC" "$DST"
fi

# Пути в скриптах и README
sed -i 's#app\\src\\main\\cpp\\tgvoip#cpp\\src\\main\\cpp\\tgvoip#g; s#app/src/main/cpp#cpp/src/main/cpp#g' tools/setup_tgvoip.ps1
[ -f tools/setup_tgvoip.sh ] && sed -i 's#app/src/main/cpp#cpp/src/main/cpp#g' tools/setup_tgvoip.sh || true
sed -i 's#app/src/main/cpp/#cpp/src/main/cpp/#g' README.md

echo "Готово. Дальше: ./gradlew clean :app:assembleDebug"
