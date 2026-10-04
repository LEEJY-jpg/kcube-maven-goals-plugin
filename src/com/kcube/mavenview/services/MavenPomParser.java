package com.kcube.mavenview.services;

import java.io.File;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.kcube.mavenview.model.MavenGoal;

/**
 * pom.xml 파일 하나를 읽어 {@link MavenGoal} 트리(PROJECT → Lifecycle/Plugins → 하위 goal)로 변환한다.
 * <p>
 * 트리의 부모-자식 관계는 {@link MavenGoal} 자체가 아니라 이 클래스가 들고 있는 {@link #CHILDREN} 맵에 별도로 저장된다. 뷰의 {@code ITreeContentProvider}는
 * {@link #children(MavenGoal)}을 통해 이 맵을 조회한다.
 */
public final class MavenPomParser
{
	/** 유틸리티 클래스이므로 인스턴스를 만들 수 없다. */
	private MavenPomParser()
	{
	}

	/** Maven 표준 lifecycle의 phase를 실행 순서대로 나열한 목록. "Lifecycle" 노드의 자식으로 그대로 사용된다. */
	private static final String[] LIFECYCLE_PHASES = {
		"clean",
		"validate",
		"initialize",
		"generate-sources",
		"process-sources",
		"generate-resources",
		"process-resources",
		"compile",
		"process-classes",
		"generate-test-sources",
		"process-test-sources",
		"generate-test-resources",
		"process-test-resources",
		"test-compile",
		"test",
		"prepare-package",
		"package",
		"pre-integration-test",
		"integration-test",
		"post-integration-test",
		"verify",
		"install",
		"deploy"};

	/** pom.xml을 파싱해 Lifecycle/Plugins를 자식으로 가진 PROJECT 루트 노드를 만든다. */
	public static MavenGoal parseProject(File pom) throws Exception
	{
		return parseProject(pom, null, new java.util.HashSet<>());
	}

	/**
	 * pom.xml을 파싱한다. {@code <modules>}가 있으면 하위 모듈 pom도 재귀적으로 파싱해 "Modules" 노드 아래에 PROJECT로 매단다.
	 *
	 * @param parent 상위 프로젝트 노드(최상위면 null)
	 * @param visited 이미 파싱한 pom의 정규 경로. 순환 참조로 무한 재귀하는 것을 막는다.
	 */
	private static MavenGoal parseProject(File pom, MavenGoal parent, java.util.Set<String> visited) throws Exception
		{
		visited.add(pom.getCanonicalPath());
		Document d = newSecureDocumentBuilder().parse(pom);
		Element project = d.getDocumentElement();

		String name = text(project, "name");
		if (name == null || name.isBlank())
			name = text(project, "artifactId");
		if (name == null || name.isBlank())
			name = pom.getParentFile().getName();

		MavenGoal projectRoot = parent == null ? new MavenGoal(name, pom) : new MavenGoal(name, pom, parent);

		MavenGoal lifecycle = new MavenGoal("Lifecycle", null, MavenGoal.Type.LIFECYCLE, projectRoot);
		addChild(projectRoot, lifecycle);
		for (String phase : LIFECYCLE_PHASES)
		{
			addChild(lifecycle, new MavenGoal(phase, phase, MavenGoal.Type.GOAL, lifecycle));
		}

		MavenGoal plugins = new MavenGoal("Plugins", null, MavenGoal.Type.PLUGIN, projectRoot);
		addChild(projectRoot, plugins);
		parsePlugins(plugins, project.getElementsByTagName("plugin"));

		parseModules(projectRoot, pom, project, visited);

		return projectRoot;
	}

	/** {@code <modules><module>}에 선언된 하위 모듈 pom을 읽어 "Modules" 노드 아래에 추가한다. 읽을 수 없는 모듈은 건너뛴다. */
	private static void parseModules(MavenGoal projectRoot, File pom, Element project, java.util.Set<String> visited)
	{
		Element modules = directChild(project, "modules");
		if (modules == null)
			return;
		MavenGoal folder = new MavenGoal("Modules", null, MavenGoal.Type.MODULES, projectRoot);
		for (Element m : directChildren(modules, "module"))
		{
			String path = m.getTextContent() == null ? "" : m.getTextContent().trim();
			if (path.isEmpty())
				continue;
			File modulePom = new File(pom.getParentFile(), path);
			if (modulePom.isDirectory())
				modulePom = new File(modulePom, "pom.xml");
			try
			{
				if (modulePom.isFile() && !visited.contains(modulePom.getCanonicalPath()))
					addChild(folder, parseProject(modulePom, folder, visited));
			}
			catch (Exception ignored)
			{
				// 깨진 모듈 pom 하나 때문에 전체 트리를 포기하지 않는다.
			}
		}
		if (!children(folder).isEmpty())
			addChild(projectRoot, folder);
	}

	/** parent의 직접 자식 중 이름이 tag인 첫 엘리먼트를 반환한다. 없으면 null. */
	private static Element directChild(Element parent, String tag)
	{
		List<Element> found = directChildren(parent, tag);
		return found.isEmpty() ? null : found.get(0);
	}

	/** parent의 직접 자식 중 이름이 tag인 엘리먼트들을 반환한다. */
	private static List<Element> directChildren(Element parent, String tag)
	{
		List<Element> result = new java.util.ArrayList<>();
		for (org.w3c.dom.Node n = parent.getFirstChild(); n != null; n = n.getNextSibling())
		{
			if (n instanceof Element e && tag.equals(e.getTagName()))
				result.add(e);
		}
		return result;
	}

	/** pom.xml의 모든 {@code <plugin>} 선언을 읽어 "Plugins" 노드 아래에 플러그인별 하위 트리를 만든다. */
	private static void parsePlugins(MavenGoal plugins, NodeList nodes)
	{
		for (int i = 0; i < nodes.getLength(); i++)
		{
			Element p = (Element) nodes.item(i);
			String artifact = text(p, "artifactId");
			if (artifact == null)
				continue; // artifactId 없는(= 유효하지 않은) plugin 선언은 건너뜀
			String group = text(p, "groupId");
			if (group == null)
				group = "org.apache.maven.plugins"; // groupId 생략 시 Maven 기본 플러그인으로 간주

			MavenGoal plugin = new MavenGoal(
				group + ":" + artifact, null, MavenGoal.Type.PLUGIN, plugins, group, artifact);
			addChild(plugins, plugin);

			// 플러그인에 바인딩된 execution들을 실행 가능한 goal 노드로 펼친다.
			NodeList executions = p.getElementsByTagName("execution");
			for (int j = 0; j < executions.getLength(); j++)
			{
				Element e = (Element) executions.item(j);
				String id = text(e, "id");
				NodeList goals = e.getElementsByTagName("goal");
			for (int k = 0; k < goals.getLength(); k++)
				{
				String raw = goals.item(k).getTextContent();
				if (raw == null || raw.isBlank())
					continue;
				String g = raw.trim();
					// "plugin:goal@executionId"(Maven 3.3.1+)는 해당 execution 자신의
					// <configuration>으로 실행한다. 그냥 "plugin:goal"로 실행하면 플러그인의 default-cli
					// 실행이 돌아가 execution 블록은 완전히 무시된다.
					String invocation = group + ":" + artifact + ":" + g + (id == null || id.isBlank() ? "" : "@" + id);
					addChild(
						plugin,
						new MavenGoal((id == null ? "" : id + ":") + g, invocation, MavenGoal.Type.EXECUTION, plugin));
				}
			}
		}
	}

	/** 주어진 엘리먼트 바로 아래의 {@code <tag>} 자식 텍스트를 읽는다. 없으면 null. (손자 이후의 같은 이름 태그는 무시한다.) */
	private static String text(Element parent, String tag)
	{
		Element e = directChild(parent, tag);
		if (e == null)
			return null;
		String s = e.getTextContent();
		return s == null ? null : s.trim();
	}

	/** parent의 자식 목록에 child를 추가한다. 목록이 아직 없으면 새로 만든다. */
	private static void addChild(MavenGoal parent, MavenGoal child)
	{
		CHILDREN.computeIfAbsent(parent, k -> new java.util.ArrayList<>()).add(child);
	}

	/** 노드(identity 기준) → 자식 목록. 트리 구조 자체는 MavenGoal이 아니라 여기서 관리한다. */
	private static final java.util.Map<MavenGoal, java.util.List<MavenGoal>> CHILDREN = new java.util.IdentityHashMap<>();

	/** 뷰의 ITreeContentProvider가 호출하는 조회용 메서드. 자식이 없으면 빈 리스트. */
	public static List<MavenGoal> children(MavenGoal goal)
	{
		return CHILDREN.getOrDefault(goal, java.util.List.of());
	}

	/** 파싱된 트리(예: 교체되거나 제거된 프로젝트 루트)를 자식 맵에서 재귀적으로 제거한다. */
	public static void dispose(MavenGoal node)
		{
		List<MavenGoal> kids = CHILDREN.remove(node);
		if (kids != null)
			{
			for (MavenGoal k : kids)
				dispose(k);
			}
		}

 	/**
	* XXE를 방지한 문서 빌더를 만든다. DOCTYPE 선언 자체를 금지하고, 지원되는 경우 외부 엔티티 로딩을 꺼낸다.
	* 일부 파서에서 특정 feature가 unsupported이면 무시하고, 나머지 조치만 유지한다.
	*/
	public static javax.xml.parsers.DocumentBuilder newSecureDocumentBuilder() throws Exception
		{
		DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
		f.setNamespaceAware(false);
		setFeature(f, "http://apache.org/xml/features/disallow-doctype-decl", true);
		setFeature(f, "http://xml.org/sax/features/external-general-entities", false);
		setFeature(f, "http://xml.org/sax/features/external-parameter-entities", false);
		setFeature(f, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		return f.newDocumentBuilder();
		}

	/** feature가 현재 파서에서 지원되면 설정하고, 안 되면 조용히 무시한다. */
	private static void setFeature(DocumentBuilderFactory f, String feature, boolean value)
		{
		try
			{
			f.setFeature(feature, value);
			}
		catch (Exception ignored)
			{
				// unsupported feature는 무시. disallow-doctype-decl이 핵심 차단이다.
			}
		}
	}
