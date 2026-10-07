package com.kcube.mavenview.services;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;

import com.kcube.mavenview.model.MavenGoal;

/**
 * 등록된 pom.xml(프로젝트)들의 모델. 파싱된 프로젝트 트리, 파일 변경 감시, 영속화를 한곳에서 관리하며 UI(SWT/JFace)에 의존하지 않는다.
 * <p>
 * 키는 pom.xml의 절대 경로({@link #key(File)})이고, 등록한 순서를 유지한다.
 */
public final class PomRegistry
{
	private final Map<String, MavenGoal> projects = new LinkedHashMap<>();
	private final PomWatcher watcher = new PomWatcher();

	/** projects 맵의 키로 쓰는, pom.xml의 절대 경로. */
	public static String key(File pomFile)
	{
		return pomFile.getAbsolutePath();
	}

	/** 등록된 프로젝트 루트들(등록 순서). */
	public Collection<MavenGoal> roots()
	{
		return projects.values();
	}

	/** 등록된 pom.xml 키들의 복사본. */
	public List<String> keys()
	{
		return new ArrayList<>(projects.keySet());
	}

	/** 해당 키로 등록된 프로젝트 루트. 없으면 null. */
	public MavenGoal get(String key)
	{
		return projects.get(key);
	}

	/** 해당 pom.xml이 이미 등록돼 있는지 여부. */
	public boolean contains(File pomFile)
	{
		return projects.containsKey(key(pomFile));
	}

	/** pom.xml을 파싱해 (교체) 등록한다. 파싱에 실패하면 기존 등록은 그대로 두고 로그만 남긴다. */
	public boolean register(File pomFile)
	{
		String key = key(pomFile);
		try
		{
			MavenGoal root = MavenPomParser.parseProject(pomFile);
			projects.put(key, root);
			watcher.remember(key, root);
			return true;
		}
		catch (Exception e)
		{
			PluginLog.log(IStatus.ERROR, "Failed to parse " + pomFile, e);
			return false;
		}
	}

	/** 등록을 해제한다. 실제로 등록돼 있었으면 true. */
	public boolean remove(String key)
	{
		if (projects.remove(key) == null)
			return false;
		watcher.forget(key);
		return true;
	}

	/** 파일이 사라진 pom.xml을 모두 등록 해제한다. 하나라도 있었으면 true. */
	public boolean pruneMissing()
	{
		boolean changed = false;
		for (String key : keys())
		{
			if (!new File(key).isFile())
				changed |= remove(key);
		}
		return changed;
	}

	/** 마지막 파싱 이후 파일이 바뀐 프로젝트 키들. */
	public List<String> changed()
	{
		return watcher.changed(projects);
	}

	/** 현재 등록 목록을 workspace preference에 저장한다. 실패하면 경고 로그만 남긴다. */
	public void persist()
	{
		try
		{
			PomRegistryStore.save(projects.keySet());
		}
		catch (Exception e)
		{
			PluginLog.log(IStatus.WARNING, "Failed to persist registered pom.xml list", e);
		}
	}

	/** preference에 저장된 pom.xml 목록을 읽어 등록한다(파일이 사라졌으면 경고만 남김). */
	public void loadSaved()
	{
		for (String path : PomRegistryStore.load())
		{
			File pomFile = new File(path);
			if (pomFile.isFile())
				register(pomFile);
			else
				PluginLog.log(IStatus.WARNING, "Previously registered pom.xml no longer exists: " + path, null);
		}
	}

	/** 등록 내용을 모두 비운다. */
	public void clear()
	{
		projects.clear();
	}
}
