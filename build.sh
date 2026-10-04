#!/bin/bash
# KCube Maven Goals 플러그인 빌드 스크립트.
#
# 사용법:
#   ./build.sh                 dist/ 에 jar 빌드
#   ./build.sh --install       빌드 후 ECLIPSE_HOME 의 dropins 에 설치
#   ./build.sh --test          단위 테스트(test/)만 컴파일해서 실행 (JUnit 5, Eclipse 번들 사용)
#
# 환경변수(생략 시 자동 탐지):
#   ECLIPSE_HOME  Eclipse.app/Contents/Eclipse 경로 (컴파일 클래스패스 + 설치 대상)
#   JAVA_HOME     JDK 17 이상 (--release 17 로 컴파일)
set -euo pipefail
cd "$(dirname "$0")"

ECLIPSE_HOME="${ECLIPSE_HOME:-}"
if [ -z "$ECLIPSE_HOME" ]; then
	for c in /Applications/Eclipse.app/Contents/Eclipse /Applications/Eclipse_2022.app/Contents/Eclipse; do
		[ -d "$c/plugins" ] && ECLIPSE_HOME="$c" && break
	done
fi
[ -d "${ECLIPSE_HOME:-/nonexistent}/plugins" ] || { echo "ECLIPSE_HOME 을 지정하세요 (…/Eclipse.app/Contents/Eclipse)"; exit 1; }

if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
	JAVA_HOME="$(/usr/libexec/java_home -v 17+ 2>/dev/null || true)"
fi
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"; JAR="${JAVA_HOME:+$JAVA_HOME/bin/}jar"

VERSION="$(sed -n 's/^Bundle-Version: *//p' META-INF/MANIFEST.MF | tr -d "\r" | sed "s/\.qualifier$//")"
OUT="dist/com.kcube.mavenview_${VERSION}.jar"
BUILD="$(mktemp -d)"
trap 'rm -rf "$BUILD"' EXIT

# 크기 0인 손상된 jar 는 제외한다.
CP="$(find "$ECLIPSE_HOME/plugins" -name '*.jar' -size +0 | tr '\n' ':')"
if [ "${1:-}" = "--test" ]; then
	JUNIT_CP="$(find "$ECLIPSE_HOME/plugins" \( -name 'org.junit.jupiter.*.jar' -o -name 'org.junit.platform.*.jar' -o -name 'org.opentest4j_*.jar' -o -name 'org.apiguardian_*.jar' \) -size +0 | tr '\n' ':')"
	echo "compile main + test ..."
	"$JAVAC" --release 17 -encoding UTF-8 -cp "$CP$JUNIT_CP" -d "$BUILD" $(find src test -name '*.java')
	(cd src && find . -type f ! -name '*.java' -exec sh -c 'mkdir -p "$1/$(dirname "$2")" && cp "$2" "$1/$2"' _ "$BUILD" {} \;)
	"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$BUILD:$CP$JUNIT_CP" com.kcube.mavenview.services.TestRunner
	exit $?
fi
echo "compile (--release 17) ..."
"$JAVAC" --release 17 -encoding UTF-8 -cp "$CP" -d "$BUILD" $(find src -name '*.java')
cp -R icons "$BUILD/icons"; cp plugin.xml plugin*.properties "$BUILD/"
(cd src && find . -type f ! -name '*.java' -exec sh -c 'mkdir -p "$1/$(dirname "$2")" && cp "$2" "$1/$2"' _ "$BUILD" {} \;)
mkdir -p dist
"$JAR" --create --file "$OUT" --manifest META-INF/MANIFEST.MF -C "$BUILD" com -C "$BUILD" icons -C "$BUILD" plugin.xml -C "$BUILD" plugin.properties -C "$BUILD" plugin_ko.properties
echo "built: $OUT"

if [ "${1:-}" = "--install" ]; then
	command cp -f "$OUT" "$ECLIPSE_HOME/dropins/"
	echo "installed to $ECLIPSE_HOME/dropins (Eclipse 를 -clean 으로 재시작하세요)"
fi
