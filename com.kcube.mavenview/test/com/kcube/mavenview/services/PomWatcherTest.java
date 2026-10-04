package com.kcube.mavenview.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kcube.mavenview.model.MavenGoal;

class PomWatcherTest
{
	@TempDir
	Path tmp;

	private static final String POM = "<project><modelVersion>4.0.0</modelVersion><artifactId>%s</artifactId>%s</project>";

	@Test
	void detectsRootAndModulePomChanges() throws Exception
	{
		Path mod = Files.createDirectories(tmp.resolve("mod"));
		File modPom = Files.writeString(mod.resolve("pom.xml"), POM.formatted("mod", "")).toFile();
		File rootPom = Files.writeString(
			tmp.resolve("pom.xml"),
			POM.formatted("root", "<modules><module>mod</module></modules>")).toFile();

		MavenGoal root = MavenPomParser.parseProject(rootPom);
		Map<String, MavenGoal> projects = new LinkedHashMap<>();
		projects.put(rootPom.getAbsolutePath(), root);
		PomWatcher w = new PomWatcher();
		w.remember(rootPom.getAbsolutePath(), root);

		assertTrue(w.changed(projects).isEmpty());

		Files.writeString(modPom.toPath(), POM.formatted("mod", "<name>changed</name>"));
		modPom.setLastModified(modPom.lastModified() + 5000);
		assertEquals(List.of(rootPom.getAbsolutePath()), w.changed(projects));

		w.remember(rootPom.getAbsolutePath(), root);
		assertTrue(w.changed(projects).isEmpty());
	}

	@Test
	void unrememberedProjectIsNotReported() throws Exception
	{
		File pom = Files.writeString(tmp.resolve("pom.xml"), POM.formatted("a", "")).toFile();
		Map<String, MavenGoal> projects = Map.of(pom.getAbsolutePath(), MavenPomParser.parseProject(pom));
		assertTrue(new PomWatcher().changed(projects).isEmpty());
	}
}
