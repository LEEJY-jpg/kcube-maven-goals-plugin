package com.kcube.mavenview.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kcube.mavenview.model.MavenGoal;

class GoalFilterTest
{
	@TempDir
	Path dir;

	private MavenGoal root;
	private MavenGoal lifecycle;
	private MavenGoal plugins;
	private final GoalFilter filter = new GoalFilter();

	@BeforeEach
	void setUp() throws Exception
	{
		File pom = dir.resolve("pom.xml").toFile();
		Files.writeString(
			pom.toPath(),
			"""
				<project><artifactId>my-app</artifactId><build><plugins>
				  <plugin><groupId>org.foo</groupId><artifactId>Foo-Plugin</artifactId>
				    <executions><execution><id>exec1</id><goals><goal>go</goal></goals></execution></executions>
				  </plugin>
				  <plugin><groupId>org.bar</groupId><artifactId>bar-plugin</artifactId></plugin>
				</plugins></build></project>
				""",
			StandardCharsets.UTF_8);
		root = MavenPomParser.parseProject(pom);
		lifecycle = MavenPomParser.children(root).get(0);
		plugins = MavenPomParser.children(root).get(1);
	}

	private Set<MavenGoal> visible(String text)
	{
		return filter.visibleNodes(List.of(root), text);
	}

	@Test
	void phaseMatchShowsOnlyThatPhaseAndItsAncestors()
	{
		Set<MavenGoal> v = visible("compile");
		assertTrue(v.contains(root));
		assertTrue(v.contains(lifecycle));
		// "compile", "process-classes"는 아니지만 "test-compile"도 포함되어 보인다.
		long phases = MavenPomParser.children(lifecycle).stream().filter(v::contains).count();
		assertEquals(2, phases);
		assertFalse(v.contains(plugins));
	}

	@Test
	void pluginMatchShowsAllItsExecutions()
	{
		Set<MavenGoal> v = visible("foo-plugin"); // 소문자 키와 비교하므로 대소문자 무관
		MavenGoal foo = MavenPomParser.children(plugins).get(0);
		assertTrue(v.contains(foo));
		assertTrue(v.contains(MavenPomParser.children(foo).get(0)));
		assertFalse(v.contains(MavenPomParser.children(plugins).get(1)));
		assertFalse(v.contains(lifecycle));
	}

	@Test
	void executionMatchShowsParentPlugin()
	{
		Set<MavenGoal> v = visible("exec1");
		MavenGoal foo = MavenPomParser.children(plugins).get(0);
		assertTrue(v.contains(foo));
		assertTrue(v.contains(plugins));
		assertFalse(v.contains(MavenPomParser.children(plugins).get(1)));
	}

	@Test
	void groupIdAlsoMatches()
	{
		Set<MavenGoal> v = visible("org.bar");
		assertTrue(v.contains(MavenPomParser.children(plugins).get(1)));
	}

	@Test
	void projectNameDoesNotMatch()
	{
		assertTrue(visible("my-app").isEmpty());
	}

	@Test
	void noMatchReturnsEmpty()
	{
		assertTrue(visible("zzz-nothing").isEmpty());
	}

	@Test
	void searchKeyIsLowercaseWithAllFields()
	{
		MavenGoal foo = MavenPomParser.children(plugins).get(0);
		String key = GoalFilter.buildSearchKey(foo);
		assertTrue(key.contains("org.foo:foo-plugin"));
		assertTrue(key.equals(key.toLowerCase()));
		assertTrue(key.contains("\nfoo-plugin"));
	}
}
