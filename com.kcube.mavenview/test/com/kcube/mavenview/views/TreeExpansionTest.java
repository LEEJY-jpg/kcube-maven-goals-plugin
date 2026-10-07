package com.kcube.mavenview.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.ui.XMLMemento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kcube.mavenview.model.MavenGoal;
import com.kcube.mavenview.services.MavenPomParser;

class TreeExpansionTest
{
	@TempDir
	Path dir;

	/** IMemento(XMLMemento)에 저장했다가 읽어 온다. 저장 과정에서 값이 바뀌면(예: 제어문자 삭제) 복원이 깨지는 것을 잡는다. */
	private static String roundTrip(String value) throws Exception
	{
		XMLMemento w = XMLMemento.createWriteRoot("root");
		w.createChild("e").putString("path", value);
		StringWriter sw = new StringWriter();
		w.save(sw);
		return XMLMemento.createReadRoot(new StringReader(sw.toString())).getChild("e").getString("path");
	}

	@Test
	void pathsSurviveMementoRoundTripForEveryNode() throws Exception
	{
		File pom = Files.writeString(dir.resolve("pom.xml"),
			"<project><artifactId>app</artifactId><build><plugins><plugin><artifactId>foo-plugin</artifactId>"
				+ "<executions><execution><id>x</id><goals><goal>go</goal></goals></execution></executions>"
				+ "</plugin></plugins></build></project>").toFile();
		MavenGoal root = MavenPomParser.parseProject(pom);
		Set<String> seen = new HashSet<>();
		check(root, seen);
		assertTrue(seen.size() > 20, "Lifecycle/Plugins 아래 노드까지 모두 검사했는지");
	}

	private static void check(MavenGoal node, Set<String> seen) throws Exception
	{
		String path = TreeExpansion.nodePath(node);
		assertEquals(path, roundTrip(path), "저장/복원 후에도 같아야 한다: " + path);
		assertTrue(seen.add(path), "노드마다 경로가 달라야 한다: " + path);
		for (MavenGoal child : MavenPomParser.children(node))
			check(child, seen);
	}

	@Test
	void joinIsUnambiguousEvenWhenSegmentsContainSeparatorOrControlChars() throws Exception
	{
		assertNotEquals(TreeExpansion.join(List.of("a|b", "c")), TreeExpansion.join(List.of("a", "b|c")));
		assertNotEquals(TreeExpansion.join(List.of("a\\", "b")), TreeExpansion.join(List.of("a", "\\b")));
		String withControl = TreeExpansion.join(List.of("p\u0001q", "n\nm"));
		assertEquals(withControl, roundTrip(withControl));
		assertTrue(withControl.chars().noneMatch(c -> c < 0x20), "제어문자는 이스케이프돼 남지 않는다");
	}
}
