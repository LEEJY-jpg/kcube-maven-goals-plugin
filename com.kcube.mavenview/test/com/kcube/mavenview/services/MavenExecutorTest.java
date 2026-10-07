package com.kcube.mavenview.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

	@Test
	void terminateForceKillsProcessThatIgnoresSigterm() throws Exception
	{
		Assumptions.assumeFalse(System.getProperty("os.name").toLowerCase().contains("win"));
		// TERM을 무시하는 셸(무시 설정은 자식 sleep에도 상속된다). destroy()만으로는 죽지 않는다.
		Process p = new ProcessBuilder("sh", "-c", "trap '' TERM; sleep 30").start();
		java.util.List<ProcessHandle> family = new java.util.ArrayList<>();
		Thread.sleep(300); // 셸이 trap을 설정하고 sleep을 띄울 시간
		p.descendants().forEach(family::add);
		family.add(p.toHandle());
		try
		{
			p.destroy();
			assertTrue(p.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS) == false, "정상 종료 신호만으로는 죽지 않아야 이 테스트가 의미 있다");
			MavenExecutor.terminate(p, 1, true);
			assertTrue(p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS), "grace 시간 후 강제 종료돼야 한다");
			assertTrue(family.stream().noneMatch(ProcessHandle::isAlive), "자식 프로세스도 함께 종료돼야 한다");
		}
		finally
		{
			family.forEach(ProcessHandle::destroyForcibly);
		}
	}
}
