#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd -P)"
cd "$project_dir"

if ! command -v java >/dev/null 2>&1; then
  echo "JDK 17 belum ditemukan. Instal JDK 17 lalu jalankan kembali skrip ini."
  exit 1
fi

gradle_tasks=()
if command -v dpkg-deb >/dev/null 2>&1; then
  gradle_tasks+=(packageDeb)
fi
if command -v rpmbuild >/dev/null 2>&1; then
  gradle_tasks+=(packageRpm)
fi

if [ "${#gradle_tasks[@]}" -eq 0 ]; then
  echo "Tidak ditemukan pembuat paket .deb atau .rpm."
  echo "Instal dpkg-deb untuk .deb, atau rpmbuild (biasanya paket rpm-build) untuk .rpm."
  exit 1
fi

echo "Membuat paket Orbit Control untuk Linux: ${gradle_tasks[*]}"
bash ./gradlew "${gradle_tasks[@]}"

echo
echo "Paket tersedia di build/compose/binaries/main/"
