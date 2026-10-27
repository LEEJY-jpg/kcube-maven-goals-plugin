package com.kcube.mavenview.services;

import java.util.List;
import java.util.Map;

/**
 * 잘 알려진 플러그인의 대표 goal 목록. pom에 {@code <execution>} 없이 선언만 된 플러그인도(예: maven-war-plugin의 {@code war:exploded})
 * 트리에서 바로 실행할 수 있도록 이 목록의 goal을 함께 보여 준다. 실제 플러그인 디스크립터를 읽지 않으므로, 단독 실행이 의미 있는 goal만 고른다.
 */
final class KnownGoals
{
	/** artifactId → 대표 goal(표시 순서대로). */
	private static final Map<String, List<String>> BY_ARTIFACT = Map.ofEntries(
		Map.entry("maven-clean-plugin", List.of("clean")),
		Map.entry("maven-resources-plugin", List.of("resources", "testResources")),
		Map.entry("maven-compiler-plugin", List.of("compile", "testCompile")),
		Map.entry("maven-surefire-plugin", List.of("test")),
		Map.entry("maven-failsafe-plugin", List.of("integration-test", "verify")),
		Map.entry("maven-jar-plugin", List.of("jar", "test-jar")),
		Map.entry("maven-war-plugin", List.of("war", "exploded", "inplace")),
		Map.entry("maven-ear-plugin", List.of("ear", "generate-application-xml")),
		Map.entry("maven-source-plugin", List.of("jar")),
		Map.entry("maven-javadoc-plugin", List.of("javadoc", "jar")),
		Map.entry("maven-dependency-plugin", List.of("tree", "analyze", "copy-dependencies", "resolve")),
		Map.entry("maven-assembly-plugin", List.of("single")),
		Map.entry("exec-maven-plugin", List.of("java", "exec")),
		Map.entry("spring-boot-maven-plugin", List.of("run", "repackage")),
		Map.entry("jetty-maven-plugin", List.of("run")),
		Map.entry("tomcat7-maven-plugin", List.of("run")),
		Map.entry("versions-maven-plugin", List.of("display-dependency-updates", "display-plugin-updates")));

	/** 유틸리티 클래스이므로 인스턴스를 만들 수 없다. */
	private KnownGoals()
	{
	}

	/** artifactId의 대표 goal 목록. 모르는 플러그인이면 빈 목록. */
	static List<String> of(String artifactId)
	{
		return BY_ARTIFACT.getOrDefault(artifactId, List.of());
	}

	/** 등록된 모든 artifactId → goal 목록(읽기 전용). */
	static Map<String, List<String>> all()
	{
		return BY_ARTIFACT;
	}
}
