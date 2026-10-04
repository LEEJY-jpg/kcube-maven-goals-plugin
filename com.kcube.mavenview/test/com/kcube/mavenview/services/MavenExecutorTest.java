package com.kcube.mavenview.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenExecutorTest
{
	@TempDir
	Path tmp;

	@Test
	void findsWrapperInParentDirectory() throws Exception
	{
		Assumptions.assumeFalse(System.getProperty("os.name").toLowerCase().contains("win"));
		File mvnw = Files.createFile(tmp.resolve("mvnw")).toFile();
		mvnw.setExecutable(true);
		File module = Files.createDirectories(tmp.resolve("a/b")).toFile();
		assertEquals(mvnw, MavenExecutor.findWrapper(module));
	}

	@Test
	void ignoresNonExecutableWrapper() throws Exception
	{
		Assumptions.assumeFalse(System.getProperty("os.name").toLowerCase().contains("win"));
		Files.createFile(tmp.resolve("mvnw")).toFile().setExecutable(false);
		// 임시 디렉터리 상위(/tmp 등)에 mvnw가 있을 가능성은 사실상 없다.
		assertNull(MavenExecutor.findWrapper(tmp.toFile()));
	}
}
