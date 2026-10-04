package com.kcube.mavenview.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kcube.mavenview.model.MavenGoal;

class MavenPomParserTest
{
	@TempDir
	Path dir;

	private File write(String relative, String xml) throws Exception
	{
		Path p = dir.resolve(relative);
		Files.createDirectories(p.getParent());
		Files.writeString(p, xml, StandardCharsets.UTF_8);
		return p.toFile();
	}

	private static MavenGoal child(MavenGoal parent, String name)
	{
		return MavenPomParser.children(parent).stream().filter(c -> c.getName().equals(name)).findFirst().orElse(null);
	}

	@Test
	void projectNameFallsBackToArtifactIdThenDirectory() throws Exception
	{
		File named = write("a/pom.xml", "<project><name>My App</name><artifactId>app</artifactId></project>");
		File artifactOnly = write("b/pom.xml", "<project><artifactId>only-artifact</artifactId></project>");
		File neither = write("c/pom.xml", "<project></project>");
		assertEquals("My App", MavenPomParser.parseProject(named).getName());
		assertEquals("only-artifact", MavenPomParser.parseProject(artifactOnly).getName());
		assertEquals("c", MavenPomParser.parseProject(neither).getName());
	}

	@Test
	void projectNameIgnoresNestedNameTags() throws Exception
	{
		File pom = write(
			"pom.xml",
			"<project><artifactId>real</artifactId><dependencies><dependency><name>nested</name></dependency></dependencies></project>");
		assertEquals("real", MavenPomParser.parseProject(pom).getName());
	}

	@Test
	void hasLifecyclePhasesInOrder() throws Exception
	{
		MavenGoal root = MavenPomParser.parseProject(write("pom.xml", "<project><artifactId>x</artifactId></project>"));
		MavenGoal lifecycle = child(root, "Lifecycle");
		assertNotNull(lifecycle);
		List<MavenGoal> phases = MavenPomParser.children(lifecycle);
		assertEquals(23, phases.size());
		assertEquals("clean", phases.get(0).getName());
		assertEquals("install", phases.get(21).getName());
		assertEquals("deploy", phases.get(22).getName());
		assertEquals("compile", phases.get(7).getGoal());
		assertEquals(MavenGoal.Type.GOAL, phases.get(7).getType());
	}

	@Test
	void parsesPluginExecutionsWithExecutionId() throws Exception
	{
		File pom = write(
			"pom.xml",
			"""
				<project><artifactId>x</artifactId><build><plugins>
				  <plugin><groupId>org.foo</groupId><artifactId>foo-plugin</artifactId>
				    <dependencies><dependency><artifactId>ignored</artifactId></dependency></dependencies>
				    <executions>
				      <execution><id>run-it</id><goals><goal>go</goal><goal>stop</goal></goals></execution>
				      <execution><goals><goal>bare</goal></goals></execution>
				    </executions>
				  </plugin>
				  <plugin><artifactId>maven-compiler-plugin</artifactId></plugin>
				  <plugin><groupId>no.artifact</groupId></plugin>
				</plugins></build></project>
				""");
		MavenGoal root = MavenPomParser.parseProject(pom);
		MavenGoal plugins = child(root, "Plugins");
		List<MavenGoal> list = MavenPomParser.children(plugins);
		assertEquals(2, list.size(), "artifactId 없는 plugin은 건너뛴다");

		MavenGoal foo = child(plugins, "org.foo:foo-plugin");
		assertArrayEquals(new String[] {"org.foo", "foo-plugin"}, foo.getArguments());
		List<MavenGoal> execs = MavenPomParser.children(foo);
		assertEquals(3, execs.size());
		assertEquals("run-it:go", execs.get(0).getName());
		assertEquals("org.foo:foo-plugin:go@run-it", execs.get(0).getGoal());
		assertEquals("org.foo:foo-plugin:bare", execs.get(2).getGoal());
		assertEquals(MavenGoal.Type.EXECUTION, execs.get(0).getType());

		MavenGoal compiler = child(plugins, "org.apache.maven.plugins:maven-compiler-plugin");
		assertNotNull(compiler, "groupId 생략 시 기본 그룹으로 간주");
	}

	@Test
	void parsesModulesRecursively() throws Exception
	{
		File parent = write(
			"pom.xml",
			"<project><artifactId>parent</artifactId><modules><module>core</module><module>web/app</module><module>missing</module></modules></project>");
		write("core/pom.xml", "<project><artifactId>core</artifactId></project>");
		write(
			"web/app/pom.xml",
			"<project><artifactId>app</artifactId><modules><module>../../core</module></modules></project>");

		MavenGoal root = MavenPomParser.parseProject(parent);
		MavenGoal modules = child(root, "Modules");
		assertNotNull(modules);
		assertEquals(MavenGoal.Type.MODULES, modules.getType());
		List<MavenGoal> mods = MavenPomParser.children(modules);
		assertEquals(2, mods.size(), "존재하지 않는 모듈은 건너뛴다");
		MavenGoal core = mods.get(0);
		assertEquals("core", core.getName());
		assertEquals(MavenGoal.Type.PROJECT, core.getType());
		assertEquals(new File(dir.toFile(), "core/pom.xml"), core.resolvePomFile());
		assertSame(modules, core.getParent());
		assertNotNull(child(core, "Lifecycle"));

		// app 모듈이 이미 파싱된 core를 다시 가리켜도(순환/중복) 무한 재귀하지 않는다.
		MavenGoal app = mods.get(1);
		assertEquals("app", app.getName());
		assertTrue(child(app, "Modules") == null || MavenPomParser.children(child(app, "Modules")).isEmpty());
	}

	@Test
	void moduleCycleDoesNotRecurseForever() throws Exception
	{
		File a = write("pom.xml", "<project><artifactId>a</artifactId><modules><module>b</module></modules></project>");
		write("b/pom.xml", "<project><artifactId>b</artifactId><modules><module>..</module></modules></project>");
		MavenGoal root = MavenPomParser.parseProject(a);
		MavenGoal b = MavenPomParser.children(child(root, "Modules")).get(0);
		assertEquals("b", b.getName());
		assertFalse(MavenPomParser.children(b).stream().anyMatch(c -> c.getType() == MavenGoal.Type.MODULES));
	}

	@Test
	void disposeRemovesChildren() throws Exception
	{
		MavenGoal root = MavenPomParser.parseProject(write("pom.xml", "<project><artifactId>x</artifactId></project>"));
		MavenGoal lifecycle = child(root, "Lifecycle");
		MavenPomParser.dispose(root);
		assertTrue(MavenPomParser.children(root).isEmpty());
		assertTrue(MavenPomParser.children(lifecycle).isEmpty());
	}

	@Test
	void rejectsDoctype() throws Exception
	{
		File pom = write("pom.xml", "<!DOCTYPE project [<!ENTITY x \"y\">]><project><artifactId>x</artifactId></project>");
		try
		{
			MavenPomParser.parseProject(pom);
			org.junit.jupiter.api.Assertions.fail("DOCTYPE이 있는 pom은 거부해야 한다");
		}
		catch (Exception expected)
		{
			// XXE 방지
		}
	}

	private static void assertArrayEquals(String[] expected, String[] actual)
	{
		org.junit.jupiter.api.Assertions.assertArrayEquals(expected, actual);
	}
}
