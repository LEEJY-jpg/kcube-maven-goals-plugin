package com.kcube.mavenview.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.m2e.actions.MavenLaunchConstants;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

import com.kcube.mavenview.model.MavenGoal;

/**
 * 트리에서 더블클릭한 goal을 실제로 실행하는 서비스.
 * <p>
 * 두 가지 실행 방식을 지원한다: 외부 {@code mvn} 프로세스를 띄우는 방식({@link #runExternal})과 Eclipse에 내장된 m2e Maven 런타임을 쓰는
 * 방식({@link #runEmbedded}). 어떤 방식을 쓸지는 뷰 툴바의 체크박스 상태({@code useExternalMvn})로 결정된다.
 */
public final class MavenExecutor
{
	private MavenExecutor()
	{
	}

	/**
	 * 주어진 노드를 실행한다. LIFECYCLE phase 등은 노드 이름 자체가 goal 문자열이고, EXECUTION 노드는 {@link MavenGoal#getGoal()}에
	 * "group:artifact:goal@executionId" 형태가 들어있다.
	 */
	public static void run(File pom, MavenGoal goal, boolean useExternalMvn)
	{
		String goalString = goal.getType() == MavenGoal.Type.EXECUTION ? goal.getGoal() : goal.getName();
		if (useExternalMvn)
		{
			runExternal(pom, goalString);
		}
		else
		{
			runEmbedded(pom, goalString);
		}
	}

	// Launches through m2e's own launch configuration (same path as "Run As > Maven Build"),
	// so the bundled embedded Maven runtime is used and Eclipse manages the Console itself.
	private static void runEmbedded(File pom, String goalString)
	{
		try
		{
			ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
			ILaunchConfigurationType type = launchManager.getLaunchConfigurationType(
				MavenLaunchConstants.LAUNCH_CONFIGURATION_TYPE_ID);
			String name = launchManager.generateLaunchConfigurationName("Maven - " + goalString);
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

	// Shells out to the external `mvn` on PATH (or the configured "maven.executable" preference).
	private static void runExternal(File pom, String goalString)
	{
		// 실행마다 새 콘솔을 만들어 보여준다 (Eclipse 콘솔 뷰에 결과가 그대로 출력됨).
		ConsolePlugin plugin = ConsolePlugin.getDefault();
		MessageConsole console = new MessageConsole("Maven - " + goalString, null);
		plugin.getConsoleManager().addConsoles(new IConsole[] {console});
		plugin.getConsoleManager().showConsoleView(console);

		// mvn 프로세스는 오래 걸릴 수 있으므로 UI 스레드를 막지 않도록 별도 스레드에서 실행.
		Thread worker = new Thread(() -> {
			try
			{
				String loginShellPath = loginShellPath();

				List<String> command = new ArrayList<>();
				command.add(findMaven(loginShellPath));
				command.add("-f");
				command.add(pom.getAbsolutePath());
				command.add(goalString);

				ProcessBuilder pb = new ProcessBuilder(command);
				pb.directory(pom.getParentFile());
				pb.redirectErrorStream(true);
				if (loginShellPath != null && !loginShellPath.isBlank())
				{
					pb.environment().put("PATH", loginShellPath);
				}
				Process process = pb.start();

				// 프로세스 출력을 한 줄씩 그대로 콘솔에 중계한다.
				try (
					BufferedReader r = new BufferedReader(
						new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
					MessageConsoleStream out = console.newMessageStream())
				{
					String line;
					while ((line = r.readLine()) != null)
						out.println(line);
				}
				int exit = process.waitFor();
				console.newMessageStream().println("Maven finished. Exit code: " + exit);
			}
			catch (Exception e)
			{
				console.newMessageStream().println("ERROR: " + e.getMessage());
			}
		}, "kcube-maven-exec");
		worker.setDaemon(true);
		worker.start();
	}

	// Common install locations for mvn that aren't always on a GUI-launched app's PATH,
	// checked as a last resort when neither the preference nor the login shell's PATH has it.
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
				File candidate = new File(dir, "mvn");
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

	private static volatile String cachedLoginShellPath;

	/**
	 * Eclipse launched via Finder/`open` only inherits launchd's minimal PATH, not the PATH a login shell sets up
	 * (Homebrew, sdkman, nvm, ...), so bare "mvn" often resolves in a Terminal but fails here. Ask the user's own login
	 * shell for its PATH once and reuse it for every external Maven invocation.
	 */
	private static synchronized String loginShellPath()
	{
		if (cachedLoginShellPath != null)
			return cachedLoginShellPath;
		String marker = "__KCUBE_PATH__:";
		try
		{
			String shell = System.getenv().getOrDefault("SHELL", "/bin/zsh");
			Process p = new ProcessBuilder(shell, "-ilc", "echo " + marker + "$PATH").redirectErrorStream(true).start();
			String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			p.waitFor();
			int idx = out.lastIndexOf(marker);
			if (idx >= 0)
			{
				String rest = out.substring(idx + marker.length());
				int nl = rest.indexOf('\n');
				cachedLoginShellPath = (nl >= 0 ? rest.substring(0, nl) : rest).trim();
			}
		}
		catch (Exception ignored)
		{
		}
		return cachedLoginShellPath;
	}

	private static void log(int severity, String message, Throwable e)
	{
		Platform.getLog(MavenExecutor.class).log(new Status(severity, "com.kcube.mavenview", message, e));
	}
}
