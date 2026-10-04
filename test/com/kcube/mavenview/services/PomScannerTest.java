package com.kcube.mavenview.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PomScannerTest
{
	@TempDir
	Path dir;

	private File pom(String relative, String body) throws Exception
	{
		Path p = dir.resolve(relative);
		Files.createDirectories(p.getParent());
		Files.writeString(p, "<project><artifactId>x</artifactId>" + body + "</project>");
		return p.toFile();
	}

	private List<String> relative(List<File> poms)
	{
		return poms.stream().map(f -> dir.relativize(f.toPath()).toString()).toList();
	}

	@Test
	void rootPomWinsAndModulesAreNotListed() throws Exception
	{
		pom("pom.xml", "<modules><module>core</module></modules>");
		pom("core/pom.xml", "");
		assertEquals(List.of("pom.xml"), relative(PomScanner.find(dir.toFile())));
	}

	@Test
	void findsNestedPomsWhenRootHasNone() throws Exception
	{
		pom("a/pom.xml", "");
		pom("b/sub/pom.xml", "");
		assertEquals(List.of("a/pom.xml", "b/sub/pom.xml"), relative(PomScanner.find(dir.toFile())));
	}

	@Test
	void skipsBuildOutputAndHiddenFolders() throws Exception
	{
		pom("a/pom.xml", "");
		pom("target/x/pom.xml", "");
		pom("node_modules/m/pom.xml", "");
		pom(".git/h/pom.xml", "");
		pom("src/main/pom.xml", "");
		assertEquals(List.of("a/pom.xml"), relative(PomScanner.find(dir.toFile())));
	}

	@Test
	void excludesPomsCoveredByAnotherCandidatesModules() throws Exception
	{
		pom("parent/pom.xml", "<modules><module>child</module><module>../other</module></modules>");
		pom("parent/child/pom.xml", "");
		pom("other/pom.xml", "");
		pom("standalone/pom.xml", "");
		assertEquals(List.of("parent/pom.xml", "standalone/pom.xml"), relative(PomScanner.find(dir.toFile())));
	}

	@Test
	void doesNotDescendIntoFoldersThatAlreadyHavePom() throws Exception
	{
		pom("a/pom.xml", "");
		pom("a/deep/pom.xml", "");
		assertEquals(List.of("a/pom.xml"), relative(PomScanner.find(dir.toFile())));
	}

	@Test
	void respectsMaxDepth() throws Exception
	{
		pom("l1/l2/l3/l4/pom.xml", ""); // pom이 있는 폴더의 깊이 4: 포함
		pom("m1/m2/m3/m4/m5/pom.xml", ""); // 깊이 5: 제외
		List<String> found = relative(PomScanner.find(dir.toFile()));
		assertEquals(List.of("l1/l2/l3/l4/pom.xml"), found);
	}

	@Test
	void moduleCycleDoesNotHang() throws Exception
	{
		pom("a/pom.xml", "<modules><module>../b</module></modules>");
		pom("b/pom.xml", "<modules><module>../a</module></modules>");
		// 서로의 모듈이므로 둘 다 제외된다(순환이라도 무한 루프 없이 끝나야 한다).
		assertTrue(PomScanner.find(dir.toFile()).isEmpty());
	}

	@Test
	void emptyOrMissingDir() throws Exception
	{
		assertTrue(PomScanner.find(dir.toFile()).isEmpty());
		assertTrue(PomScanner.find(new File(dir.toFile(), "nope")).isEmpty());
		assertTrue(PomScanner.find(null).isEmpty());
	}
}
