package com.kcube.mavenview.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PomRegistryTest
{
	@TempDir
	Path tmp;

	private static final String POM = "<project><modelVersion>4.0.0</modelVersion><artifactId>%s</artifactId></project>";

	private File pom(String dir, String artifactId) throws Exception
	{
		Path d = Files.createDirectories(tmp.resolve(dir));
		return Files.writeString(d.resolve("pom.xml"), POM.formatted(artifactId)).toFile();
	}

	@Test
	void registersInOrderAndLooksUpByKey() throws Exception
	{
		PomRegistry registry = new PomRegistry();
		File b = pom("b", "b");
		File a = pom("a", "a");
		assertTrue(registry.register(b));
		assertTrue(registry.register(a));
		assertTrue(registry.contains(a));
		assertEquals(List.of(PomRegistry.key(b), PomRegistry.key(a)), registry.keys(), "등록 순서 유지");
		assertNotNull(registry.get(PomRegistry.key(a)));
		assertEquals(2, registry.roots().size());
	}

	@Test
	void unparsablePomIsNotRegisteredAndKeepsPreviousEntry() throws Exception
	{
		PomRegistry registry = new PomRegistry();
		File a = pom("a", "a");
		assertTrue(registry.register(a));
		Files.writeString(a.toPath(), "<project><unclosed>");
		assertFalse(registry.register(a));
		assertNotNull(registry.get(PomRegistry.key(a)), "파싱 실패해도 기존 등록 유지");
	}

	@Test
	void removeReportsWhetherItWasRegistered() throws Exception
	{
		PomRegistry registry = new PomRegistry();
		File a = pom("a", "a");
		registry.register(a);
		assertTrue(registry.remove(PomRegistry.key(a)));
		assertFalse(registry.remove(PomRegistry.key(a)));
		assertNull(registry.get(PomRegistry.key(a)));
	}

	@Test
	void pruneMissingDropsDeletedPoms() throws Exception
	{
		PomRegistry registry = new PomRegistry();
		File a = pom("a", "a");
		File b = pom("b", "b");
		registry.register(a);
		registry.register(b);
		assertFalse(registry.pruneMissing());
		assertTrue(a.delete());
		assertTrue(registry.pruneMissing());
		assertEquals(List.of(PomRegistry.key(b)), registry.keys());
	}

	@Test
	void changedReportsOnlyModifiedProjects() throws Exception
	{
		PomRegistry registry = new PomRegistry();
		File a = pom("a", "a");
		File b = pom("b", "b");
		registry.register(a);
		registry.register(b);
		assertTrue(registry.changed().isEmpty());
		Files.writeString(a.toPath(), POM.formatted("a-changed"));
		a.setLastModified(a.lastModified() + 5000);
		assertEquals(List.of(PomRegistry.key(a)), registry.changed());
		registry.register(a);
		assertTrue(registry.changed().isEmpty(), "다시 파싱하면 변경 없음");
	}

	@Test
	void brokenPomIsRetriedOnlyWhenTheFileChangesAgain() throws Exception
	{
		PomRegistry registry = new PomRegistry();
		File a = pom("a", "a");
		registry.register(a);
		Files.writeString(a.toPath(), "<project><unclosed>");
		a.setLastModified(a.lastModified() + 5000);
		assertEquals(List.of(PomRegistry.key(a)), registry.changed());
		assertFalse(registry.register(a));
		assertTrue(registry.changed().isEmpty(), "실패한 같은 파일 상태로는 다시 변경으로 잡히지 않는다(2초마다 재시도/로그 방지)");
		Files.writeString(a.toPath(), POM.formatted("a-fixed"));
		a.setLastModified(a.lastModified() + 5000);
		assertEquals(List.of(PomRegistry.key(a)), registry.changed(), "고쳐서 저장하면 다시 감지된다");
		assertTrue(registry.register(a));
		assertTrue(registry.changed().isEmpty());
	}

	@Test
	void brokenModulePomIsShownAndRecoversWhenFixed() throws Exception
	{
		Files.createDirectories(tmp.resolve("root/mod"));
		File mod = Files.writeString(tmp.resolve("root/mod/pom.xml"), "<project><unclosed>").toFile();
		File root = Files.writeString(tmp.resolve("root/pom.xml"),
			"<project><modelVersion>4.0.0</modelVersion><artifactId>root</artifactId><modules><module>mod</module></modules></project>")
			.toFile();
		PomRegistry registry = new PomRegistry();
		assertTrue(registry.register(root));
		var modules = com.kcube.mavenview.services.MavenPomParser.children(registry.get(PomRegistry.key(root))).stream()
			.filter(n -> n.getName().equals("Modules")).findFirst().orElseThrow();
		var placeholder = com.kcube.mavenview.services.MavenPomParser.children(modules);
		assertEquals(1, placeholder.size(), "깨진 모듈도 표시용 노드로 남는다");
		assertTrue(placeholder.get(0).getName().contains("mod"));
		assertTrue(registry.changed().isEmpty());

		Files.writeString(mod.toPath(), POM.formatted("mod"));
		mod.setLastModified(mod.lastModified() + 5000);
		assertEquals(List.of(PomRegistry.key(root)), registry.changed(), "모듈 pom을 고치면 루트 프로젝트가 다시 읽힐 대상이 된다");
		registry.register(root);
		var fixed = com.kcube.mavenview.services.MavenPomParser.children(
			com.kcube.mavenview.services.MavenPomParser.children(registry.get(PomRegistry.key(root))).stream()
				.filter(n -> n.getName().equals("Modules")).findFirst().orElseThrow());
		assertEquals("mod", fixed.get(0).getName(), "고친 뒤에는 정상 모듈로 표시된다");
	}

	@Test
	void symlinkAndRealPathAreTheSameProjectButOriginalPathIsKept() throws Exception
	{
		org.junit.jupiter.api.Assumptions.assumeFalse(System.getProperty("os.name").toLowerCase().contains("win"));
		File real = pom("real", "app");
		Path link = Files.createSymbolicLink(tmp.resolve("link"), tmp.resolve("real"));
		File viaLink = link.resolve("pom.xml").toFile();

		assertEquals(PomRegistry.key(real), PomRegistry.key(viaLink), "같은 파일이면 같은 키");
		PomRegistry registry = new PomRegistry();
		assertTrue(registry.register(viaLink));
		assertTrue(registry.contains(real), "실제 경로로도 이미 등록된 것으로 본다");
		assertTrue(registry.register(real));
		assertEquals(1, registry.keys().size(), "중복 등록되지 않는다");
		assertEquals(viaLink, registry.fileOf(registry.keys().get(0)), "실행/저장에는 처음 등록한 원래 경로를 쓴다");
		assertEquals(real.getCanonicalFile(), new File(registry.keys().get(0)));
	}

	@Test
	void keyOfMissingFileFallsBackToAbsolutePathAndIsNotCached() throws Exception
	{
		File missing = new File(tmp.toFile(), "later/pom.xml");
		assertEquals(missing.getAbsolutePath(), PomRegistry.key(missing));
		File created = pom("later", "x");
		assertEquals(created.getCanonicalPath(), PomRegistry.key(created), "생긴 뒤에는 정규 경로로 계산한다");
	}
}
