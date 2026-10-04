package com.kcube.mavenview.services;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.kcube.mavenview.model.MavenGoal;

/**
 * 등록된 프로젝트(하위 모듈 pom 포함)의 pom.xml 수정 여부를 파일 수정 시각/크기로 감지한다.
 * <p>
 * 파싱된 시점의 지문을 {@link #remember}로 기록해 두고, {@link #changed}가 현재 파일 상태와 다른 프로젝트 키를 돌려준다.
 */
public final class PomWatcher
{
	private final Map<String, String> fingerprints = new HashMap<>();

	/** 프로젝트 트리에 속한 모든 pom.xml(루트 + 모듈)의 현재 상태를 지문으로 기록한다. */
	public void remember(String key, MavenGoal root)
	{
		fingerprints.put(key, fingerprint(root));
	}

	/** 더 이상 감시하지 않는다. */
	public void forget(String key)
	{
		fingerprints.remove(key);
	}

	/** 기록된 지문과 현재 pom 파일 상태가 달라진 프로젝트 키 목록. 기록이 없는 프로젝트는 포함하지 않는다. */
	public List<String> changed(Map<String, MavenGoal> projects)
	{
		List<String> result = new ArrayList<>();
		for (Map.Entry<String, MavenGoal> e : projects.entrySet())
		{
			String old = fingerprints.get(e.getKey());
			if (old != null && !old.equals(fingerprint(e.getValue())))
				result.add(e.getKey());
		}
		return result;
	}

	private static String fingerprint(MavenGoal root)
	{
		StringBuilder sb = new StringBuilder();
		append(root, sb);
		return sb.toString();
	}

	private static void append(MavenGoal node, StringBuilder sb)
	{
		if (node.getType() == MavenGoal.Type.PROJECT && node.getPomFile() != null)
		{
			File f = node.getPomFile();
			sb.append(f.getPath()).append('|').append(f.lastModified()).append('|').append(f.length()).append(';');
		}
		for (MavenGoal child : MavenPomParser.children(node))
		{
			// PROJECT와 그 아래 폴더(Modules)만 따라가면 충분하다(goal/execution 노드에는 pom이 없다).
			if (child.getType() == MavenGoal.Type.PROJECT || child.getType() == MavenGoal.Type.MODULES)
				append(child, sb);
		}
	}
}
