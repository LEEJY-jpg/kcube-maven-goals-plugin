package com.kcube.mavenview.services;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;

/** 뷰 설정(실행 방식, 즐겨찾기/최근 실행, 마지막 실행 옵션)을 workspace preference에 저장/복원한다. */
public final class ViewPreferences
{
	private static final String PREFS_NODE = "com.kcube.mavenview";
	private static final String PREF_USE_EXTERNAL_MVN = "useExternalMvn";
	private static final String PREF_FAVORITES = "favoriteGoals";
	private static final String PREF_RECENT = "recentGoals";
	private static final String PREF_OPT_SKIP_TESTS = "runOptions.skipTests";
	private static final String PREF_OPT_OFFLINE = "runOptions.offline";
	private static final String PREF_OPT_UPDATE = "runOptions.updateSnapshots";
	private static final String PREF_OPT_PROFILES = "runOptions.profiles";
	private static final String PREF_OPT_EXTRA = "runOptions.extraArgs";

	private ViewPreferences()
	{
	}

	private static IEclipsePreferences node()
	{
		return InstanceScope.INSTANCE.getNode(PREFS_NODE);
	}

	private static void flush(IEclipsePreferences prefs, String what)
	{
		try
		{
			prefs.flush();
		}
		catch (Exception e)
		{
			PluginLog.log(IStatus.WARNING, "Failed to persist " + what, e);
		}
	}

	/** true면 외부 mvn, false면 Eclipse 내장 Maven으로 실행한다. */
	public static boolean useExternalMvn()
	{
		return node().getBoolean(PREF_USE_EXTERNAL_MVN, false);
	}

	public static void setUseExternalMvn(boolean value)
	{
		IEclipsePreferences prefs = node();
		prefs.putBoolean(PREF_USE_EXTERNAL_MVN, value);
		flush(prefs, "mvn execution mode");
	}

	/** 즐겨찾기/최근 실행 목록을 읽어 history에 채운다(최근 실행은 최대 개수까지만). */
	public static void loadHistory(GoalHistory history)
	{
		IEclipsePreferences prefs = node();
		history.load(prefs.get(PREF_FAVORITES, ""), prefs.get(PREF_RECENT, ""));
	}

	public static void saveHistory(GoalHistory history)
	{
		IEclipsePreferences prefs = node();
		prefs.put(PREF_FAVORITES, history.serializeFavorites());
		prefs.put(PREF_RECENT, history.serializeRecents());
		flush(prefs, "favorites/recent goals");
	}

	/** 마지막으로 사용한 실행 옵션. */
	public static RunOptions loadRunOptions()
	{
		IEclipsePreferences prefs = node();
		return new RunOptions(
			prefs.getBoolean(PREF_OPT_SKIP_TESTS, false),
			prefs.getBoolean(PREF_OPT_OFFLINE, false),
			prefs.getBoolean(PREF_OPT_UPDATE, false),
			prefs.get(PREF_OPT_PROFILES, ""),
			prefs.get(PREF_OPT_EXTRA, ""));
	}

	public static void saveRunOptions(RunOptions options)
	{
		IEclipsePreferences prefs = node();
		prefs.putBoolean(PREF_OPT_SKIP_TESTS, options.skipTests());
		prefs.putBoolean(PREF_OPT_OFFLINE, options.offline());
		prefs.putBoolean(PREF_OPT_UPDATE, options.updateSnapshots());
		prefs.put(PREF_OPT_PROFILES, options.profiles());
		prefs.put(PREF_OPT_EXTRA, options.extraArgs());
		flush(prefs, "run options");
	}
}
