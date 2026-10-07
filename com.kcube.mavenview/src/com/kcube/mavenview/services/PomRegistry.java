package com.kcube.mavenview.services;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.runtime.IStatus;

import com.kcube.mavenview.model.MavenGoal;

/**
 * 등록된 pom.xml(프로젝트)들의 모델. 파싱된 프로젝트 트리, 파일 변경 감시, 영속화를 한곳에서 관리하며 UI(SWT/JFace)에 의존하지 않는다.
 * <p>
 * 프로젝트의 <b>식별</b>은 pom.xml의 정규(canonical) 경로({@link #key(File)})로 한다. 심볼릭 링크나 대소문자·{@code ..} 표기가 달라도 같은 파일이면
 * 같은 프로젝트로 취급해 중복 등록을 막는다. 반면 파싱·실행·저장에는 사용자가 등록한 <b>원래 경로</b>({@link #fileOf})를 그대로 쓴다. 정규 경로로
 * 바꿔 쓰면 워크스페이스 프로젝트의 위치와 달라져 "Update Maven Project" 대상 매칭 등이 어긋날 수 있기 때문이다. 등록한 순서를 유지한다.
 */
public final class PomRegistry
{
	private final Map<String, MavenGoal> projects = new LinkedHashMap<>();
	/** 키(정규 경로) → 처음 등록한 원래 pom 파일. */
	private final Map<String, File> files = new LinkedHashMap<>();
	private final PomWatcher watcher = new PomWatcher();

	/** 절대 경로 → 정규 경로. 노드를 그릴 때마다 키가 필요한데 매번 파일 시스템을 조회하지 않도록 캐시한다. */
	private static final Map<String, String> KEY_CACHE = new ConcurrentHashMap<>();

	/**
	 * 프로젝트 식별 키: pom.xml의 정규(canonical) 경로. 파일이 없거나 정규화에 실패하면 절대 경로를 쓴다(이 경우는 캐시하지 않아 파일이
	 * 생기면 다시 계산한다).
	 */
	public static String key(File pomFile)
	{
		String absolute = pomFile.getAbsolutePath();
		String cached = KEY_CACHE.get(absolute);
		if (cached != null)
			return cached;
		if (!pomFile.exists())
			return absolute;
		try
		{
			String canonical = pomFile.getCanonicalPath();
			KEY_CACHE.put(absolute, canonical);
			return canonical;
		}
		catch (IOException e)
		{
			return absolute;
		}
	}

	/** 키 캐시를 비운다. 심볼릭 링크 대상이 바뀌었을 수 있는 새로고침 때 부른다. */
	public static void clearKeyCache()
	{
		KEY_CACHE.clear();
	}

	/** 해당 키로 처음 등록된 원래 pom 파일. 등록되지 않은 키(예: 저장된 즐겨찾기 항목)는 키 경로 그대로의 파일. */
	public File fileOf(String key)
	{
		return files.getOrDefault(key, new File(key));
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

	/**
	 * pom.xml 하나를 파싱한 결과. {@code root}가 null이면 실패이고 {@code error}에 원인이 있다.
	 */
	public record Parsed(File file, MavenGoal root, Exception error)
	{
	}

	/**
	 * pom.xml을 파싱만 한다(레지스트리 상태는 건드리지 않는다). 파일을 읽는 느린 작업이라 UI 스레드 밖(Job)에서 불러도 안전하며,
	 * 결과는 UI 스레드에서 {@link #commit}으로 반영한다.
	 */
	public static Parsed parse(File pomFile)
	{
		try
		{
			return new Parsed(pomFile, MavenPomParser.parseProject(pomFile), null);
		}
		catch (Exception e)
		{
			return new Parsed(pomFile, null, e);
		}
	}

	/**
	 * 파싱 결과를 레지스트리에 반영한다. 레지스트리 상태를 바꾸므로 한 스레드(UI)에서만 호출한다.
	 *
	 * @param onlyIfRegistered true면 그사이 등록이 해제된 프로젝트는 되살리지 않고 무시한다(재파싱용)
	 * @return 반영했으면 true. 파싱이 실패했거나 무시했으면 false
	 */
	public boolean commit(Parsed parsed, boolean onlyIfRegistered)
	{
		String key = key(parsed.file());
		if (onlyIfRegistered && !projects.containsKey(key))
			return false;
		if (parsed.root() != null)
		{
			projects.put(key, parsed.root());
			files.putIfAbsent(key, parsed.file());
			watcher.remember(key, parsed.root());
			return true;
		}
		PluginLog.log(IStatus.ERROR, "Failed to parse " + parsed.file(), parsed.error());
		// 기존 트리를 유지하되 지문은 지금 파일 상태로 갱신한다. 그러지 않으면 깨진 pom이 계속 "변경됨"으로 잡혀
		// 2초마다 같은 실패와 로그, 화면 갱신이 반복된다. 파일이 다시 바뀌면 그때 재시도한다.
		MavenGoal existing = projects.get(key);
		if (existing != null)
			watcher.remember(key, existing);
		return false;
	}

	/** pom.xml을 파싱해 (교체) 등록한다. 파싱과 반영을 같은 스레드에서 차례로 한다. 파싱에 실패하면 기존 등록은 그대로 두고 로그만 남긴다. */
	public boolean register(File pomFile)
	{
		return commit(parse(pomFile), false);
	}

	/** 등록을 해제한다. 실제로 등록돼 있었으면 true. */
	public boolean remove(String key)
	{
		if (projects.remove(key) == null)
			return false;
		files.remove(key);
		watcher.forget(key);
		return true;
	}

	/** 파일이 사라진 pom.xml을 모두 등록 해제한다. 하나라도 있었으면 true. */
	public boolean pruneMissing()
	{
		boolean changed = false;
		for (String key : keys())
		{
			if (!fileOf(key).isFile())
				changed |= remove(key);
		}
		return changed;
	}

	/** 현재 등록 상태의 복사본. 다른 스레드에서 {@link #changed(Map)}에 넘겨 파일 I/O를 UI 밖에서 하기 위해 쓴다. */
	public Map<String, MavenGoal> snapshot()
	{
		return new LinkedHashMap<>(projects);
	}

	/** 마지막 파싱 이후 파일이 바뀐 프로젝트 키들. */
	public List<String> changed()
	{
		return changed(projects);
	}

	/** 주어진 스냅샷 기준으로 마지막 파싱 이후 파일이 바뀐 프로젝트 키들. 파일 시스템을 읽으므로 UI 스레드 밖에서 호출해도 된다. */
	public List<String> changed(Map<String, MavenGoal> snapshot)
	{
		return watcher.changed(snapshot);
	}

	/** 현재 등록 목록을 workspace preference에 저장한다. 실패하면 경고 로그만 남긴다. */
	public void persist()
	{
		try
		{
			// 식별 키(정규 경로)가 아니라 사용자가 등록한 원래 경로를 저장한다.
			PomRegistryStore.save(files.values().stream().map(File::getAbsolutePath).toList());
		}
		catch (Exception e)
		{
			PluginLog.log(IStatus.WARNING, "Failed to persist registered pom.xml list", e);
		}
	}

	/**
	 * preference에 저장된 pom.xml 중 지금도 있는 파일들을 반환한다(같은 파일을 다른 경로로 저장해 둔 중복은 하나만). 사라진 파일은 경고만 남긴다.
	 * 파싱은 하지 않으므로 호출 측이 {@link #parse}/{@link #commit}으로 등록한다.
	 */
	public List<File> savedFiles()
	{
		List<File> result = new ArrayList<>();
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (String path : PomRegistryStore.load())
		{
			File pomFile = new File(path);
			if (!pomFile.isFile())
				PluginLog.log(IStatus.WARNING, "Previously registered pom.xml no longer exists: " + path, null);
			else if (seen.add(key(pomFile)))
				result.add(pomFile);
		}
		return result;
	}

	/** preference에 저장된 pom.xml 목록을 읽어 바로 등록한다(동기). */
	public void loadSaved()
	{
		savedFiles().forEach(this::register);
	}

	/** 등록 내용을 모두 비운다. */
	public void clear()
	{
		projects.clear();
		files.clear();
	}
}
