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
	/**
	 * 경로 조각(pom 경로, 노드 이름들)을 이어 붙이는 구분자. IMemento(XML)에 저장되므로 출력 가능한 문자여야 한다 — XML에 쓸 수 없는
	 * 제어문자(예: U+0001)는 저장할 때 조용히 삭제되어 경로가 깨진다. 조각 안에 구분자가 있어도 모호하지 않도록 {@link #escape}로 이스케이프한다.
	 */
	private static final char PATH_NODE_SEP = '|';

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
		List<String> segments = new java.util.ArrayList<>();
		segments.add(PomRegistry.key(pom));
		segments.addAll(names);
		return join(segments);
	}

	/** 조각들을 이스케이프해 {@value #PATH_NODE_SEP}로 잇는다. 같은 조각 목록은 항상 같은 문자열이고 서로 다른 목록은 다른 문자열이다. */
	static String join(List<String> segments)
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < segments.size(); i++)
		{
			if (i > 0)
				sb.append(PATH_NODE_SEP);
			sb.append(escape(segments.get(i)));
		}
		return sb.toString();
	}

	/** 백슬래시와 구분자, 그리고 XML에 안전하지 않은 제어문자를 {@code \} 이스케이프로 바꾼다. */
	static String escape(String s)
	{
		StringBuilder sb = new StringBuilder(s.length() + 4);
		for (int i = 0; i < s.length(); i++)
		{
			char c = s.charAt(i);
			if (c == '\\' || c == PATH_NODE_SEP)
				sb.append('\\').append(c);
			else if (c < 0x20)
				sb.append(String.format("\\u%04x", (int) c));
			else
				sb.append(c);
		}
		return sb.toString();
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
