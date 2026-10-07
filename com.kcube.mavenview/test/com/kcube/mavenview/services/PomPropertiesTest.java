package com.kcube.mavenview.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kcube.mavenview.model.MavenGoal;

class PomPropertiesTest
{
	@TempDir
	Path dir;

	private PomProperties props(String projectXml) throws Exception
	{
		File pom = Files.writeString(dir.resolve("pom.xml"), projectXml).toFile();
		return PomProperties.of(MavenPomParser.newSecureDocumentBuilder().parse(pom).getDocumentElement(), pom);
	}

	@Test
	void resolvesPropertiesAndNestedReferences() throws Exception
	{
		PomProperties p = props("<project><artifactId>app</artifactId><properties>"
			+ "<base>org.foo</base><grp>${base}.tools</grp></properties></project>");
		assertEquals("org.foo.tools:x", p.resolve("${grp}:x"));
		assertEquals("plain", p.resolve("plain"));
		assertNull(p.resolve(null));
	}

	@Test
	void resolvesProjectAndParentBuiltIns() throws Exception
	{
		PomProperties p = props("<project><parent><groupId>pg</groupId><artifactId>pa</artifactId><version>9</version></parent>"
			+ "<artifactId>app</artifactId><name>My App</name></project>");
		assertEquals("app", p.resolve("${project.artifactId}"));
		assertEquals("pg", p.resolve("${project.groupId}"), "groupId 생략 시 부모 값");
		assertEquals("9", p.resolve("${pom.version}"), "version 생략 시 부모 값");
		assertEquals("pa", p.resolve("${project.parent.artifactId}"));
		assertEquals("My App", p.resolve("${project.name}"));
		assertEquals(dir.toFile().getAbsolutePath(), p.resolve("${project.basedir}"));
	}

	@Test
	void leavesUnknownReferencesAndSurvivesCycles() throws Exception
	{
		PomProperties p = props("<project><artifactId>a</artifactId><properties>"
			+ "<a>${b}</a><b>${a}</b><dollar>$1\\x</dollar></properties></project>");
		assertEquals("${env.HOME}/x", p.resolve("${env.HOME}/x"), "풀 수 없으면 그대로");
		p.resolve("${a}"); // 순환이어도 끝난다
		assertEquals("$1\\x", p.resolve("${dollar}"), "치환값의 $, \\ 는 문자 그대로");
	}

	@Test
	void parserUsesPropertiesForNamePluginsAndModules() throws Exception
	{
		Files.createDirectories(dir.resolve("sub/mod"));
		Files.writeString(dir.resolve("sub/mod/pom.xml"), "<project><artifactId>mod</artifactId></project>");
		File pom = Files.writeString(dir.resolve("pom.xml"), """
			<project><artifactId>app</artifactId><name>${project.artifactId}-web</name>
			<properties><sub.dir>sub</sub.dir><plugin.group>org.foo</plugin.group><exec.id>run</exec.id></properties>
			<modules><module>${sub.dir}/mod</module></modules>
			<build><plugins><plugin><groupId>${plugin.group}</groupId><artifactId>foo-plugin</artifactId>
			  <executions><execution><id>${exec.id}</id><goals><goal>go</goal></goals></execution></executions>
			</plugin></plugins></build></project>
			""").toFile();
		MavenGoal root = MavenPomParser.parseProject(pom);
		assertEquals("app-web", root.getName());
		List<MavenGoal> kids = MavenPomParser.children(root);
		MavenGoal plugins = kids.stream().filter(k -> k.getName().equals("Plugins")).findFirst().orElseThrow();
		MavenGoal plugin = MavenPomParser.children(plugins).get(0);
		assertEquals("org.foo:foo-plugin", plugin.getName());
		assertEquals("org.foo:foo-plugin:go@run", MavenPomParser.children(plugin).get(0).getGoal());
		MavenGoal modules = kids.stream().filter(k -> k.getName().equals("Modules")).findFirst().orElseThrow();
		assertEquals("mod", MavenPomParser.children(modules).get(0).getName(), "${...} 모듈 경로를 풀어 하위 pom을 찾는다");
	}
}
