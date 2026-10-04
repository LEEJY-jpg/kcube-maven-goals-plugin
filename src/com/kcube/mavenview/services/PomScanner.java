package com.kcube.mavenview.services;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * 프로젝트 폴더에서 등록할 만한 pom.xml을 자동으로 찾는다(UI 의존성 없음).
 * <p>
 * 폴더 루트에 pom.xml이 있으면 그것 하나만 돌려준다(하위 모듈은 트리의 Modules 폴더로 보이므로). 없으면 하위 폴더를 {@value #MAX_DEPTH}단계까지 탐색하되
 * {@code target}, {@code node_modules}, {@code .git} 등은 건너뛰고, 다른 후보의 {@code <modules>}로 이미 포함되는 pom은 제외한다.
 */
public final class PomScanner
{
	/** 루트로부터 탐색할 최대 하위 폴더 깊이. */
	public static final int MAX_DEPTH = 4;

	/** 탐색에서 건너뛰는 폴더 이름(빌드 산출물, 의존성, VCS, IDE 설정 등). */
	private static final Set<String> SKIP_DIRS = Set.of(
		"target",
		"bin",
		"build",
		"out",
		"node_modules",
		"src",
		"WEB-INF");

	/** 유틸리티 클래스이므로 인스턴스를 만들 수 없다. */
	private PomScanner()
	{
	}

	/** projectDir 아래에서 등록할 pom.xml 후보를 경로순으로 반환한다. 없으면 빈 목록. */
	public static List<File> find(File projectDir)
	{
		List<File> result = new ArrayList<>();
		if (projectDir == null || !projectDir.isDirectory())
			return result;
		File root = new File(projectDir, "pom.xml");
		if (root.isFile())
		{
			result.add(root);
			return result;
		}
		List<File> found = new ArrayList<>();
		scan(projectDir, 1, found);
		found.sort((a, b) -> a.getPath().compareTo(b.getPath()));

		// 다른 후보의 <modules>로 이미 포함되는 pom은 중복이므로 제외한다.
		Set<String> covered = new HashSet<>();
		for (File pom : found)
			collectModules(pom, covered, new HashSet<>());
		for (File pom : found)
		{
			if (!covered.contains(canonical(pom)))
				result.add(pom);
		}
		return result;
	}

	/** dir 하위 폴더를 재귀적으로 훑어 pom.xml을 모은다. 폴더에서 pom.xml을 찾으면 그 폴더 아래는 더 내려가지 않는다(모듈은 부모가 담당). */
	private static void scan(File dir, int depth, List<File> found)
	{
		if (depth > MAX_DEPTH)
			return;
		File[] children = dir.listFiles(File::isDirectory);
		if (children == null)
			return;
		Arrays.sort(children);
		for (File child : children)
		{
			String name = child.getName();
			if (name.startsWith(".") || SKIP_DIRS.contains(name))
				continue;
			File pom = new File(child, "pom.xml");
			if (pom.isFile())
				found.add(pom);
			else
				scan(child, depth + 1, found);
		}
	}

	/** pom이 선언한 모듈들(재귀)의 정규 경로를 covered에 모은다. 순환은 visited로 막는다. */
	private static void collectModules(File pom, Set<String> covered, Set<String> visited)
	{
		if (!visited.add(canonical(pom)))
			return;
		for (File module : declaredModules(pom))
		{
			covered.add(canonical(module));
			collectModules(module, covered, visited);
		}
	}

	/** pom의 {@code <modules><module>}이 가리키는 하위 pom 파일 목록을 읽는다. 읽을 수 없으면 빈 목록. */
	static List<File> declaredModules(File pom)
	{
		List<File> modules = new ArrayList<>();
		try
			{
			Document d = MavenPomParser.newSecureDocumentBuilder().parse(pom);
			for (Node n = d.getDocumentElement().getFirstChild(); n != null; n = n.getNextSibling())
			{
				if (n instanceof Element e && e.getTagName().equals("modules"))
				{
					for (Node m = e.getFirstChild(); m != null; m = m.getNextSibling())
					{
						if (m instanceof Element me && me.getTagName().equals("module"))
						{
							String path = me.getTextContent().trim();
							if (path.isEmpty())
								continue;
							File target = new File(pom.getParentFile(), path);
							if (target.isDirectory())
								target = new File(target, "pom.xml");
							if (target.isFile())
								modules.add(target);
						}
					}
				}
			}
		}
		catch (Exception ignored)
		{
			// 깨진 pom은 모듈이 없는 것으로 취급한다.
		}
		return modules;
	}

	/** 비교에 쓸 정규 경로. 실패하면 절대 경로로 대체한다. */
	private static String canonical(File f)
	{
		try
		{
			return f.getCanonicalPath();
		}
		catch (IOException e)
		{
			return f.getAbsolutePath();
		}
	}
}
