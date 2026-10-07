package com.kcube.mavenview.views;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.viewers.TreeViewer;

import com.kcube.mavenview.model.MavenGoal;
import com.kcube.mavenview.services.MavenPomParser;
import com.kcube.mavenview.services.PomRegistry;

/**
 * 트리 노드의 펼침 상태를 "경로 문자열"로 저장/복원하기 위한 도우미. 트리를 다시 파싱해 노드 객체가 바뀌어도
 * (pom 경로 + 루트부터의 이름 체인)이 같으면 같은 노드로 취급한다.
 */
final class TreeExpansion
{
	/** 경로에서 pom 경로와 노드 이름들을 구분하는 문자(경로/이름에 나올 수 없는 제어문자). */
	private static final String PATH_NODE_SEP = "\u0001";

	private TreeExpansion()
	{
	}

	/** 노드를 식별하는 문자열(pom 경로 + 가장 가까운 PROJECT부터의 이름 체인)을 만든다. pom을 알 수 없으면 null. */
	static String nodePath(MavenGoal g)
	{
		File pom = g.resolvePomFile();
		if (pom == null)
			return null;
		LinkedList<String> names = new LinkedList<>();
		for (MavenGoal n = g; n != null && n.getType() != MavenGoal.Type.PROJECT; n = n.getParent())
			names.addFirst(n.getName());
		return PomRegistry.key(pom) + PATH_NODE_SEP + String.join(PATH_NODE_SEP, names);
	}

	/** 현재 펼쳐져 있는 노드들의 경로 집합. */
	static Set<String> expandedPaths(TreeViewer viewer)
	{
		Set<String> result = new HashSet<>();
		for (Object o : viewer.getExpandedElements())
		{
			if (o instanceof MavenGoal g)
			{
				String path = nodePath(g);
				if (path != null)
					result.add(path);
			}
		}
		return result;
	}

	/** 주어진 루트들에서 경로가 wanted에 들어 있는 노드를 모두 찾는다. */
	static List<MavenGoal> find(Collection<MavenGoal> roots, Set<String> wanted)
	{
		List<MavenGoal> out = new java.util.ArrayList<>();
		for (MavenGoal root : roots)
			collect(root, wanted, out);
		return out;
	}

	private static void collect(MavenGoal node, Set<String> wanted, List<MavenGoal> out)
	{
		String path = nodePath(node);
		if (path != null && wanted.contains(path))
			out.add(node);
		for (MavenGoal child : MavenPomParser.children(node))
			collect(child, wanted, out);
	}
}
