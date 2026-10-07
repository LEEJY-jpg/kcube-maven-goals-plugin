package com.kcube.mavenview.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.m2e.actions.MavenLaunchConstants;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

import com.kcube.mavenview.Messages;
import com.kcube.mavenview.model.MavenGoal;

/**
 * 트리에서 더블클릭한 goal을 실제로 실행하는 서비스.
 * <p>
 * 두 가지 실행 방식을 지원한다: 외부 {@code mvn} 프로세스를 띄우는 방식({@link #runExternal})과 Eclipse에 내장된 m2e Maven 런타임을 쓰는
 * 방식({@link #runEmbedded}). 어떤 방식을 쓸지는 뷰 툴바의 체크박스 상태({@code useExternalMvn})로 결정된다.
 */
public final class MavenExecutor
{
	/** 유틸리티 클래스이므로 인스턴스를 만들 수 없다. */
	private MavenExecutor()
	{
	}

	/**
	 * 주어진 노드를 실행한다. LIFECYCLE phase 등은 노드 이름 자체가 goal 문자열이고, EXECUTION 노드는 {@link MavenGoal#getGoal()}에
	 * "group:artifact:goal@executionId" 형태가 들어있다.
	 */
	public static void run(File pom, MavenGoal goal, boolean useExternalMvn)
	{
		run(pom, goalString(goal), useExternalMvn);
	}

	/** 노드에서 Maven에 전달할 실행 문자열을 꺼낸다. EXECUTION은 goal 필드, 그 외(phase 등)는 노드 이름이다. */
	public static String goalString(MavenGoal goal)
	{
		return goal.getType() == MavenGoal.Type.EXECUTION ? goal.getGoal() : goal.getName();
	}

	/** 실행 문자열(예: "clean install")로 직접 실행한다. 즐겨찾기/최근 실행 목록에서 다시 실행할 때 쓴다. */
	public static void run(File pom, String goalString, boolean useExternalMvn)
	{
		if (useExternalMvn)
		{
			runExternal(pom, goalString);
		}
		else
		{
			runEmbedded(pom, goalString);
		}
	}

	// m2e 자체 런치 설정으로 실행한다("Run As > Maven Build"와 동일한 경로).
	// 따라서 Eclipse에 내장된 Maven 런타임이 쓰이고, Console도 Eclipse가 직접 관리한다.
	private static void runEmbedded(File pom, String goalString)
	{
		try
		{
			ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
			ILaunchConfigurationType type = launchManager.getLaunchConfigurationType(
				MavenLaunchConstants.LAUNCH_CONFIGURATION_TYPE_ID);
			String name = launchManager.generateLaunchConfigurationName("Maven - " + goalString.replaceAll("[\\\\/]", "_"));
			ILaunchConfigurationWorkingCopy wc = type.newInstance(null, name);
			wc.setAttribute(MavenLaunchConstants.ATTR_POM_DIR, pom.getParentFile().getAbsolutePath());
			wc.setAttribute(MavenLaunchConstants.ATTR_GOALS, goalString);
			wc.launch(ILaunchManager.RUN_MODE, new NullProgressMonitor());
		}
		catch (Exception e)
		{
			log(IStatus.ERROR, "Failed to launch embedded Maven goal " + goalString, e);
		}
	}

	/** 실행 중인 외부 mvn 프로세스. 키는 "pom경로\tgoal"이며 같은 실행의 중복 시작을 막고 Stop에 쓰인다. */
	private static final Map<String, Process> RUNNING = new ConcurrentHashMap<>();

	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

	static
	{
		// Eclipse가 실제로 종료될 때 fork된 mvn이 자식으로 남아있지 않도록 회수한다(단, 뷰를 닫는 것은 포함 안 함).
		try
		{
			Runtime.getRuntime().addShutdownHook(new Thread(MavenExecutor::stopAll, "kcube-maven-shutdown"));
		}
		catch (IllegalStateException ignored)
		{
			// JVM이 이미 종료 중이면 추가할 수 없다.
		}
	}

	/** 실행 중인 외부 mvn이 있는지 여부. */
	public static boolean hasRunning()
	{
		return !RUNNING.isEmpty();
	}

	/** 실행 중인 외부 mvn 프로세스(자식 포함)를 모두 중단한다. 중단한 개수를 돌려준다. */
	public static int stopAll()
	{
		int count = 0;
		for (Process p : RUNNING.values())
		{
			p.descendants().forEach(ProcessHandle::destroy);
			p.destroy();
			count++;
		}
		return count;
	}

	/**
	 * pom 위치에서 부모 디렉터리로 거슬러 올라가며 Maven Wrapper(mvnw / Windows는 mvnw.cmd)를 찾는다. 멀티 모듈에서는 wrapper가 루트에만 있으므로
	 * 상위까지 탐색한다. 없으면 null.
	 */
	static File findWrapper(File pomDir)
	{
		String name = WINDOWS ? "mvnw.cmd" : "mvnw";
		for (File dir = pomDir; dir != null; dir = dir.getParentFile())
		{
			File candidate = new File(dir, name);
			if (candidate.isFile() && (WINDOWS || candidate.canExecute()))
				return candidate;
		}
		return null;
	}

	// 외부 Maven(프로젝트의 mvnw 우선, 없으면 preference/PATH의 mvn)을 호출해 실행한다.
	private static void runExternal(File pom, String goalString)
	{
		String runKey = pom.getAbsolutePath() + "\t" + goalString;
		// 실행마다 콘솔을 보여준다. 같은 이름의 콘솔이 있으면 비우고 재사용해 콘솔이 무한정 늘지 않게 한다.
		MessageConsole console = consoleFor("Maven - " + goalString);
		ConsolePlugin.getDefault().getConsoleManager().showConsoleView(console);
		if (RUNNING.containsKey(runKey))
		{
			console.newMessageStream().println(Messages.get("console.already.running"));
			return;
		}
		console.clearConsole();

		// mvn 프로세스는 오래 걸릴 수 있으므로 UI 스레드를 막지 않도록 Job으로 실행한다(Progress 뷰에 표시되고 취소할 수 있다).
		new ExternalMvnJob(console, pom, goalString, runKey).schedule();
	}

	/** 외부 mvn 프로세스를 실행하고 출력을 콘솔로 중계하는 Job. 취소하면 mvn과 그 자식 프로세스를 중단한다. */
	private static final class ExternalMvnJob extends Job
	{
		private final MessageConsole console;
		private final File pom;
		private final String goalString;
		private final String runKey;
		private volatile Process process;
		private volatile boolean cancelled;

		ExternalMvnJob(MessageConsole console, File pom, String goalString, String runKey)
		{
			super("Maven - " + goalString);
			this.console = console;
			this.pom = pom;
			this.goalString = goalString;
			this.runKey = runKey;
		}

		@Override
		protected IStatus run(IProgressMonitor monitor)
		{
			try (MessageConsoleStream out = console.newMessageStream())
			{
				String loginShellPath = loginShellPath();

				List<String> command = new ArrayList<>();
				File wrapper = findWrapper(pom.getParentFile());
				command.add(wrapper != null ? wrapper.getAbsolutePath() : findMaven(loginShellPath));
				command.add("-f");
				command.add(pom.getAbsolutePath());
				// goalString은 "clean install -DskipTests"처럼 옵션이 섞인 명령행일 수 있어 인자별로 나눠 전달한다.
				command.addAll(RunOptions.tokenize(goalString));

				ProcessBuilder pb = new ProcessBuilder(command);
				pb.directory(pom.getParentFile());
				pb.redirectErrorStream(true);
				if (loginShellPath != null && !loginShellPath.isBlank())
				{
					pb.environment().put("PATH", loginShellPath);
				}
				Process started = pb.start();
				process = started;
				MavenExecutor.RUNNING.put(runKey, started);
				try
				{
					if (cancelled)
						stop(started);
					// 프로세스 출력을 한 줄씩 그대로 콘솔에 중계한다.
					try (BufferedReader r = new BufferedReader(
						new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8)))
					{
						String line;
						while ((line = r.readLine()) != null)
							out.println(line);
					}
					int exit = started.waitFor();
					out.println(Messages.get(cancelled ? "console.cancelled" : "console.finished", exit));
				}
				finally
				{
					MavenExecutor.RUNNING.remove(runKey);
				}
				return cancelled ? Status.CANCEL_STATUS : Status.OK_STATUS;
			}
			catch (Exception e)
			{
				try (MessageConsoleStream err = console.newMessageStream())
				{
					err.println(Messages.get("console.error", e.getMessage()));
				}
				catch (Exception ignored)
				{
					// 콘솔마저 쓸 수 없으면 아래 로그로만 남긴다.
				}
				log(IStatus.WARNING, "Failed to run external Maven goal " + goalString, e);
				return Status.OK_STATUS;
			}
		}

		/** Progress 뷰에서 취소하면 실행 중인 mvn 프로세스 트리를 중단한다. */
		@Override
		protected void canceling()
		{
			cancelled = true;
			Process p = process;
			if (p != null)
				stop(p);
		}

		private static void stop(Process p)
		{
			p.descendants().forEach(ProcessHandle::destroy);
			p.destroy();
		}
	}

	/** 이름이 같은 기존 콘솔을 찾아 돌려주고, 없으면 새로 만들어 등록한다. */
	private static MessageConsole consoleFor(String name)
	{
		IConsoleManager manager = ConsolePlugin.getDefault().getConsoleManager();
		for (IConsole existing : manager.getConsoles())
		{
			if (existing instanceof MessageConsole mc && name.equals(mc.getName()))
				return mc;
		}
		MessageConsole console = new MessageConsole(name, null);
		manager.addConsoles(new IConsole[] {console});
		return console;
	}

	// GUI로 띄운 앱의 PATH에는 항상 들어 있지는 않은 mvn의 일반적인 설치 위치들.
	// preference에도, 로그인 셸의 PATH에도 mvn이 없을 때 마지막 수단으로 확인한다.
	private static final String[] FALLBACK_MVN_LOCATIONS = {
		"/opt/homebrew/bin/mvn",
		"/usr/local/bin/mvn",
		System.getProperty("user.home") + "/.sdkman/candidates/maven/current/bin/mvn",};

	/**
	 * mvn 실행 파일 경로를 우선순위대로 찾는다: 1) 사용자가 명시적으로 설정한 preference, 2) 로그인 셸 PATH 안에서 실제로 존재/실행 가능한 mvn, 3) 흔히 쓰이는
	 * Homebrew/SDKMAN 설치 경로. 모두 실패하면 경고를 남기고 bare "mvn"으로 폴백한다.
	 */
	private static String findMaven(String loginShellPath)
	{
		String configured = Platform.getPreferencesService().getString(
			"com.kcube.mavenview",
			"maven.executable",
			"",
			null);
		if (configured != null && !configured.isBlank())
			return configured;

		if (loginShellPath != null)
		{
			for (String dir : loginShellPath.split(File.pathSeparator))
			{
				File candidate = new File(dir, WINDOWS ? "mvn.cmd" : "mvn");
				if (candidate.isFile() && candidate.canExecute())
					return candidate.getAbsolutePath();
			}
		}
		for (String candidate : FALLBACK_MVN_LOCATIONS)
		{
			if (new File(candidate).canExecute())
				return candidate;
		}

		log(
			IStatus.WARNING,
			"Could not locate an mvn executable via PATH, login shell PATH, "
				+ "or common install locations; falling back to bare \"mvn\" (will likely fail)",
			null);
		return "mvn";
	}

	private static final int LOGIN_SHELL_TIMEOUT_SECONDS = 5;

	private static volatile String cachedLoginShellPath;

	/**
	 * Finder/`open`으로 띄운 Eclipse는 launchd의 최소 PATH만 상속받고, 로그인 셸이 구성하는 PATH는 받지 못한다
	 * (Homebrew, sdkman, nvm 등). 그래서 터미널에서는 "mvn"이 찾아져도 여기서는 실패하는 경우가 많다. 사용자의 로그인
	 * 셸에 PATH를 한 번만 물어봐 캐시해 두고, 이후 모든 외부 Maven 실행에 재사용한다.
	 */
	private static synchronized String loginShellPath()
	{
		if (cachedLoginShellPath != null)
			return cachedLoginShellPath;
		if (WINDOWS)
			return null; // Windows는 GUI 앱도 시스템 PATH를 그대로 상속받는다.
		String marker = "__KCUBE_PATH__:";
		Process p = null;
				// 공통 ForkJoinPool을 오염시키지 않도록 전용 스레드로 읽어, 타임아웃시 그 스레드만 정리한다.
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor();
		try
				{
			String shell = System.getenv().getOrDefault("SHELL", "/bin/zsh");
			p = new ProcessBuilder(shell, "-ilc", "echo " + marker + "$PATH").redirectErrorStream(true).start();
			final Process proc = p; // lambda 캡처용 불변 참조
					// 셸 초기화 파일이 입력을 기다리며 멈출 수 있으므로 시간 제한을 둔다.
			byte[] bytes = pool.submit(() -> proc.getInputStream().readAllBytes())
					.get(LOGIN_SHELL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			String out = new String(bytes, StandardCharsets.UTF_8);
			int idx = out.lastIndexOf(marker);
			if (idx >= 0)
				{
				String rest = out.substring(idx + marker.length());
				int nl = rest.indexOf('\n');
				cachedLoginShellPath = (nl >= 0 ? rest.substring(0, nl) : rest).trim();
				}
			}
		catch (TimeoutException e)
			{
			log(IStatus.WARNING, "Login shell did not report PATH within " + LOGIN_SHELL_TIMEOUT_SECONDS + "s", null);
			}
		catch (Exception e)
			{
			log(IStatus.WARNING, "Failed to read PATH from login shell", e);
			}
		finally
			{
			if (p != null)
				p.destroyForcibly();
			pool.shutdownNow();
			}
		return cachedLoginShellPath;
	}

	/** Eclipse Error Log에 이 플러그인 이름으로 메시지를 기록한다. */
	private static void log(int severity, String message, Throwable e)
	{
		Platform.getLog(MavenExecutor.class).log(new Status(severity, "com.kcube.mavenview", message, e));
	}
}
