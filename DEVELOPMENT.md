# KCube Maven Goals — 개발 내역

Eclipse Maven lifecycle/plugin goal 트리 뷰 플러그인. Ant View처럼 여러 `pom.xml`을 등록해두고, 각 project를 최상위 노드로 하여 lifecycle phase와 plugin goal을 트리로 보여주고 더블클릭으로 실행한다.

## 구조

Tycho 멀티 모듈 프로젝트다.

```
pom.xml                          부모 pom (Tycho, Eclipse 2023-09 p2 리포지토리)
com.kcube.mavenview/             플러그인 번들 (eclipse-plugin) — 소스, plugin.xml, icons, test
com.kcube.mavenview.feature/     Feature (eclipse-feature)
com.kcube.mavenview.update-site/ p2 업데이트 사이트 (eclipse-repository, category.xml)
build.sh                         Eclipse 설치본 기반 빠른 jar 빌드 (dropins 용)
```

플러그인 소스 레이아웃(`com.kcube.mavenview/` 기준):

```
src/com/kcube/mavenview/
  model/MavenGoal.java        트리 노드 모델 (PROJECT/LIFECYCLE/PLUGIN/GOAL/EXECUTION)
  services/MavenPomParser.java   pom.xml 파싱 → MavenGoal 트리 생성
  services/MavenExecutor.java    goal 실행 (외부 mvn 또는 내장 m2e 둘 다 지원)
  views/MavenGoalsView.java      뷰 UI, 툴바, 등록 목록 관리
  views/SelectionTracker.java, PomDropSupport.java   선택 추적, 드래그 앤 드롭
  services/PomRegistryStore.java, PomWatcher.java    등록 목록 저장, pom 변경 감지
  handlers/RefreshHandler.java   커맨드(M5+R) → 전체 새로고침
```

## 주요 기능 및 변경 이력

### 1. 기본 버그 수정
- `RefreshHandler.java`에 `HandlerUtil` import 누락 → 컴파일 불가 상태였던 것을 수정.
- `MANIFEST.MF`에 `org.eclipse.ui.console` 의존성이 빠져 있어 goal 실행 시 `NoClassDefFoundError` 발생 → Require-Bundle에 추가.

### 2. Ant View 스타일로 구조 변경
- 기존에는 Eclipse 선택(selection)에 따라 pom.xml 하나만 자동으로 읽어오는 구조였음.
- 여러 pom.xml을 **등록**하고, 각각을 트리의 최상위(PROJECT) 노드로 보여주도록 전면 리팩터링.
  - `MavenGoal`에 `pomFile` 필드와 `resolvePomFile()`(부모를 거슬러 올라가 소속 pom.xml을 찾음) 추가.
  - `MavenPomParser.parseProject(File)`가 PROJECT 루트 노드부터 Lifecycle/Plugins 하위 트리까지 한 번에 생성.
  - `MavenModel` 클래스는 더 이상 필요 없어 삭제.
  - `MavenGoalsView`가 `LinkedHashMap<pom경로, MavenGoal루트>`로 등록 목록을 관리.
- 등록 목록은 Eclipse workspace preferences(`InstanceScope`)에 저장되어 재시작해도 유지됨.

### 3. 등록 방법 다양화
- 툴바 **Add POM File...**: 파일 다이얼로그로 디스크에서 pom.xml 선택(다중 선택 가능).
- 툴바 **Add from Selection**: Project/Package Explorer 등 **다른 뷰**에서 마지막으로 선택했던 리소스의 pom.xml을 등록.
  - 최초 구현 시, 버튼을 누르는 순간 포커스가 본 뷰로 넘어가며 Eclipse 전역 선택 서비스가 "현재 활성 파트의 선택"(본 뷰 자신의 트리)을 반환하는 버그가 있었음 → 다른 파트의 선택을 별도 리스너로 계속 추적/캐시하도록 수정.
- **드래그 앤 드롭**: Finder나 Project Explorer에서 pom.xml 파일 또는 프로젝트 폴더를 뷰에 끌어다 놓으면 자동 등록(`FileTransfer` + `ResourceTransfer` 모두 지원).

### 4. 트리 UX 개선
- 최상위 프로젝트 노드는 이름순(대소문자 무시) 정렬. 하위 Lifecycle phase 순서·Plugin 선언 순서는 원본 그대로 유지.
- **Expand All / Collapse All** 버튼 추가(Eclipse 표준 아이콘 재사용). Expand All은 트리에서 노드(자식이 있는)를 선택한 상태면 그 하위만 펼치고, 선택이 없으면 전체를 펼침.
- Plugin 항목(`groupId:artifactId`)은 너무 길어 가독성이 떨어져, **artifactId만 Bold로 표시**하도록 커스텀 LabelProvider 적용.

### 5. Maven 실행 방식 — 외부 mvn ↔ Eclipse 내장 Maven 전환 가능
- 처음에는 `ProcessBuilder`로 외부 `mvn`을 직접 호출했으나, GUI로 띄운 Eclipse(`open` 명령 실행)는 로그인 셸의 PATH(`.zshrc` 등, Homebrew 설치 경로 등)를 상속받지 못해 `mvn`을 못 찾는 문제 발생.
  - 해결: 최초 실행 시 사용자의 로그인 셸(`$SHELL -ilc`)을 한 번 띄워 PATH를 읽어와 캐시, 이후 모든 외부 mvn 실행에 재사용.
- Eclipse에 이미 내장된 m2e Maven 런타임을 그대로 쓰는 방법도 추가:
  - m2e의 Maven 런치 설정(`org.eclipse.m2e.Maven2LaunchConfigurationType`)을 프로그래밍적으로 생성/실행 — "Run As > Maven Build"와 동일한 경로. Eclipse가 Console도 자동으로 관리해줌.
  - (참고용 검토: `MavenCli`를 직접 임베딩해 별도 JVM으로 실행하는 방법도 테스트했으나 commons-cli/slf4j/guava 등 흩어진 의존성을 일일이 모아야 해서 이식성이 떨어져 채택하지 않음.)
- 툴바 맨 왼쪽에 **네이티브 체크박스(`☐ mvn`)**를 추가해 두 방식을 토글 가능하게 함. 체크 시 외부 mvn, 해제 시 Eclipse 내장 Maven. 선택 상태는 workspace preferences에 저장.

### 5-1. 실행 안정성 버그 수정
- **execution 바인딩 버그**: `<execution id="js"><goals><goal>run</goal></goals></execution>` 처럼 특정 execution에 바인딩된 goal(`js:run` 등)을 더블클릭하면, 해당 execution의 `<configuration>`이 아니라 플러그인의 **기본(default-cli) 실행**이 돌아가는 문제가 있었음(예: antrun의 경우 "No Ant target defined - SKIPPED"). 원인은 goal 문자열을 `group:artifact:goal` 형태로만 만들어 어떤 execution인지 지정하지 않았기 때문.
  - 수정: Maven 3.3.1+가 지원하는 `group:artifact:goal@executionId` 문법을 사용해 특정 execution의 설정 그대로 실행되도록 변경 (`MavenPomParser.parsePlugins`).
- **mvn 탐색 견고화**: Dock/Finder로 Eclipse를 띄우는 경우처럼 로그인 셸 PATH 추정이 실패하거나 다르게 동작하는 환경을 대비해, `findMaven()`이 로그인 셸 PATH 안의 `mvn` 파일 존재/실행 가능 여부를 직접 확인하고, 실패 시 Homebrew(`/opt/homebrew/bin/mvn`, `/usr/local/bin/mvn`)·SDKMAN 경로를 순서대로 추가 확인하도록 보강.

### 6. Maven Update 버튼
- 툴바 맨 앞쪽에 **Update Maven Project** 버튼 추가(m2e의 update_dependencies 아이콘을 jar에 직접 포함 — m2e 버전별 png/svg 차이에 영향받지 않음).
- 선택한 프로젝트 노드의 pom.xml이 **워크스페이스에 임포트된 프로젝트와 매칭되면** `UpdateMavenProjectJob`으로 pom.xml 재로딩·의존성 재해석을 수행. 매칭되는 프로젝트가 없으면 Error Log에 경고만 남기고 종료.

### 6-1. Run 버튼
- 툴바에 초록 ▶ **Run** 버튼 추가. 트리에서 실행 가능한 goal(Lifecycle phase, plugin execution goal)을 선택한 뒤 누르면 더블클릭과 동일하게 실행됨. 실행 불가능한 노드(폴더성 노드 등)가 선택돼 있으면 아무 동작도 하지 않음.
- 더블클릭과 버튼이 `runSelectedGoal()` 하나를 공유하므로 외부 mvn/내장 Maven 모드 선택이 그대로 적용됨.
- 아이콘(`icons/run.png`)은 다른 번들(`org.eclipse.debug.ui`)에서 빌려오지 않고 이 플러그인 jar에 직접 포함시킴. 외부 번들 가용성에 의존하지 않고, `Require-Bundle`도 늘리지 않기 위함.
- (시도했다가 되돌린 것) Lifecycle/Plugins 노드를 접히지 않게 고정하는 기능은 사용성 이슈로 제거하고 원래대로 자유롭게 접고 펼 수 있게 복구함.

### 7. 아이콘/배포
- 모든 텍스트 버튼을 Eclipse 표준 공용 아이콘으로 교체(각각 적절한 아이콘을 Eclipse 자체 플러그인에서 재사용: Add, Delete, Refresh, Expand/Collapse All, External Tools, Update Dependencies 등). 마우스 오버 시 툴팁으로 기능명 표시.
- 배포용 단일 jar(`dist/com.kcube.mavenview_<버전>.jar`, Git 추적 제외) 산출 — 외부 3rd-party 의존성 없이 Eclipse 표준 번들만 사용하므로 다른 PC의 Eclipse `dropins`에 파일 하나만 복사하면 설치 가능.

### 8. Java 17 호환 빌드 (구버전 Eclipse 지원)
- 증상: Java 21용(`JavaSE-21`, class major 65)으로 빌드한 jar를 Java 17로 도는 Eclipse 2022-03에 넣으면 번들이 resolve되지 않아 **뷰가 조용히 안 나타남**.
- 조치: `Bundle-RequiredExecutionEnvironment`를 `JavaSE-17`로 낮추고 `javac --release 17`로 컴파일. 이제 Java 17 이상의 모든 Eclipse에서 로드됨(Eclipse 2022-03, 2024-09, 최신 Spring Tools 확인 대상).
- 17에서 컴파일되지 않던 코드 수정:
  - `MavenExecutor`: `Thread.startVirtualThread`(Java 21) → 데몬 `Thread`로 교체.
  - `MavenGoalsView`: m2e 버전별로 `UpdateMavenProjectJob` 생성자가 다름(2022=`IProject[]`, 2024 이후=`Collection<IProject>`) → `scheduleUpdate()`에서 리플렉션으로 둘 다 지원.
- Update Maven Project 아이콘 문제: m2e의 `icons/update_dependencies.svg`를 참조했으나 m2e 2022/2024 버전에는 `.png`만 있어 아이콘이 비고 텍스트로 표시됨 → `update_dependencies.png`(+`@2x`)를 이 플러그인 `icons/`에 포함하고 `com.kcube.mavenview` 번들에서 로드하도록 변경.

### 9. Goal 검색(필터)
- 트리 위에 검색 입력창(`SWT.SEARCH`, X 버튼 포함) 추가. 키워드를 입력하면 일치하는 goal만 남기고, 일치하는 goal이 없는 프로젝트는 숨김. 대소문자 구분 없음. Esc 또는 X 버튼으로 해제.
- `ViewerFilter`로 구현. 노드의 표시 이름·실행 문자열(`group:artifact:goal@id` 등)·플러그인 groupId/artifactId 중 하나라도 키워드를 포함하면 일치. 일치 노드의 조상(경로)은 함께 표시하고, 플러그인 노드가 일치하면 그 하위 execution도 모두 표시.
- **PROJECT(최상위) 노드의 이름은 매칭 대상에서 제외**해, 프로젝트명이 키워드와 같다는 이유로 하위 전체가 나오지 않도록 함.
- 필터 중에는 결과를 자동으로 모두 펼치고, 해제하면 기본 펼침 깊이(프로젝트 → Lifecycle/Plugins)로 복원.
- **성능 개선**: 노드가 수천 개일 때 입력이 느려지는 문제를 다음으로 완화.
  - 키 입력마다 바로 갱신하지 않고 입력이 200ms 멈춘 뒤 한 번만 적용(디바운스).
  - `ViewerFilter`가 노드마다 조상/후손을 다시 훑던 방식을 버리고, 검색어가 바뀔 때 트리를 **한 번 순회해 보일 노드 집합(identity set)을 계산**, 필터는 집합 조회만 수행. 노드별 소문자 검색 키는 캐시(`WeakHashMap`). pom 재파싱/제거 시 집합을 무효화.
  - 갱신 중 `setRedraw(false)`로 중간 그리기를 막음.
  - 결과가 1,000개를 넘으면 전부 펼치지 않고 플러그인 단계(깊이 3)까지만 펼침.
  - 참고: 12,000노드 합성 데이터에서 순수 필터 계산은 기존에도 10~20ms 수준이었고(새 방식 3~14ms, 결과 동일), 체감 지연의 주된 원인은 키 입력마다 반복되던 `refresh`/`expandAll`(SWT 항목 대량 생성)이었다.

### 10. 즐겨찾기 / 최근 실행 / 상태 저장
- 트리 우클릭 → `Add to Favorites` / `Remove from Favorites`. 즐겨찾기 노드는 `★` 접두사로 표시.
- 툴바 별(★) 버튼: 누르면 즐겨찾기/최근 실행 메뉴가 바로 열린다(메뉴 맨 위에서 선택한 goal을 즐겨찾기에 추가/해제). 선택한 goal이 즐겨찾기면 채워진 별, 아니면 빈 별 아이콘.
- 툴바 드롭다운(Favorites / Recent): 즐겨찾기와 최근 실행 10개를 보여주고, 선택하면 바로 실행. `Clear Recent`로 최근 목록 삭제.
- 목록은 workspace preference(`favoriteGoals`, `recentGoals`)에 `pom경로<TAB>goal` 형태로 저장.
- 뷰 상태(펼침 노드, 검색어)는 `saveState`/`init`의 `IMemento`로 저장·복원 (워크벤치 종료/뷰 닫기 시점).

### 11. 멀티 모듈 / 실행 옵션 / 단위 테스트
- **멀티 모듈**: pom의 `<modules>`를 따라 하위 pom을 재귀 파싱해 `Modules` 폴더 아래 PROJECT 노드로 표시한다. 없는 모듈은 건너뛰고, 순환 참조는 정규 경로로 막는다. 모듈 노드에서 실행하면 해당 모듈의 pom으로 실행된다. (`Remove`는 최상위 프로젝트에만 적용)
- **Run with Options...**: 노드 우클릭. Goals(편집 가능; PROJECT 노드는 `clean install` 제안), Profiles(`-P`), `-DskipTests`, `-o`, `-U`, 추가 인자. 마지막에 쓴 옵션은 preference에 저장되고, 실행 결과는 최근 목록에도 들어간다. goal 문자열 자체가 옵션을 포함한 명령행이라 외부 mvn은 따옴표를 고려해 인자별로 분리해 실행한다.
- **POM 파싱 개선**: `<name>`/`<artifactId>`/`<id>` 등을 직접 자식에서만 읽는다(의존성 안의 같은 이름 태그에 속지 않음).
- **단위 테스트**: `test/` (JUnit 5). `./build.sh --test` 로 실행. 대상은 `MavenPomParser`, `GoalFilter`(검색 로직, 뷰에서 분리), `RunOptions`. Eclipse에서는 `.classpath`의 `test` 소스 폴더로 Run As > JUnit Test 가능.

### 12. 다국어(영어 기본 / 한국어·일본어·중국어)
- 기본은 영어, Eclipse 로케일이 한국어/일본어/중국어(`-nl ko|ja|zh`, 또는 OS 언어)면 해당 언어로 표시한다. 중국어는 간체 기준이며 번체(zh_TW) 환경에서도 같은 파일을 쓴다. 지원하지 않는 언어는 영어.
- 뷰/대화상자/툴바/콘솔 문자열: `src/com/kcube/mavenview/messages.properties`(영어), `messages_ko/ja/zh.properties`(UTF-8). `Messages.get(키)` 로 조회하며 한국어에 없는 키는 영어로 대체된다.
- `plugin.xml` 의 뷰 이름/명령 이름: `plugin.properties`, `plugin_ko/ja/zh.properties`(ISO-8859-1, `\uXXXX` 이스케이프) + MANIFEST 의 `Bundle-Localization: plugin`.
- Error Log 메시지와 트리 노드 이름(Lifecycle/Plugins/Modules, goal 이름)은 번역하지 않는다.
- 언어를 추가하려면 `messages_<언어코드>.properties` 와 `plugin_<언어코드>.properties` 를 추가한다(`MessagesTest`가 영어와 키가 일치하는지 검사).

### 13. 2단계 노드 고정 (해제됨)
- 한때 Lifecycle / Plugins / Modules 노드를 접히지 않게 고정했으나, 접을 수 없어 불편해 제거했다. 이제 2단계 노드도 클릭/더블클릭/키보드로 자유롭게 접고 펼 수 있다.
- `Collapse All`은 프로젝트와 2단계 노드는 펼친 채 3단계 이하만 접는다(`collapseToLevel2`). 저장된 펼침 상태 복원 시에도 접어 둔 2단계 노드는 접힌 채로 유지된다.

### 14. Add from Selection 자동 인식 / 삭제된 pom 정리
- 아이콘을 홈 대신 Maven 프로젝트 아이콘(`icons/add_project.png`)으로 변경.
- 선택 추적: `IResource`뿐 아니라 어댑터(`Adapters.adapt`)로 `IJavaProject` 등도 프로젝트로 변환(복수 선택). 변환 불가한 선택(콘솔 등)은 무시하고 이전 값을 유지.
- `PomScanner`: 루트 `pom.xml`이 있으면 그것만, 없으면 하위 폴더를 4단계까지 탐색(`target`, `bin`, `build`, `out`, `node_modules`, `src`, `WEB-INF`, 숨김 폴더 제외). 다른 후보의 `<modules>`에 포함되는 pom은 중복이므로 제외.
- 후보가 1개면 바로 등록, 2개 이상이면 체크 목록(`ListSelectionDialog`, 기본 전체 선택). 이미 등록된 pom은 후보에서 제외, 선택이 없으면 pom이 있는 워크스페이스 프로젝트를 고르게 함. 결과가 없으면 안내 대화상자.
- 삭제된 pom 정리(`pruneMissingPoms`): 뷰 활성화/새로고침/실행 목록 열 때/목록 항목 실행 시, 파일이 없어진 pom의 등록 프로젝트(트리)와 즐겨찾기·최근 실행 항목을 모두 제거.
- 즐겨찾기/최근 실행 로직을 `GoalHistory`로 분리. 최근 실행은 항상 최대 10개(저장된 값이 더 많아도 읽을 때 잘라냄).

### 15. 외부 실행 안정화 / 빌드 qualifier
- 외부 mvn 실행: pom 위치에서 상위로 거슬러 올라가며 `mvnw`(Windows는 `mvnw.cmd`)를 찾아 있으면 우선 사용. 없으면 기존처럼 preference/PATH의 mvn(Windows는 `mvn.cmd`).
- 툴바 **Stop** 버튼: 실행 중인 외부 mvn 프로세스(자식 포함)를 중단. 같은 pom+goal이 이미 실행 중이면 새로 시작하지 않고 안내만 출력. 같은 이름의 콘솔은 비우고 재사용.
- 로그인 셸 PATH 조회에 5초 제한을 두고 실패/타임아웃은 Error Log에 경고로 기록. Windows는 조회하지 않음.
- `build.sh`: 빌드마다 `Bundle-Version`의 qualifier를 타임스탬프로 치환(`QUALIFIER` 환경변수로 지정 가능). `--install`은 `dropins`의 기존 `com.kcube.mavenview*`를 먼저 삭제해 중복 dropin 문제를 방지.

### 16. pom 변경 자동 반영
- `PomWatcher`: 파싱 시점의 pom 지문(경로+수정 시각+크기, 하위 모듈 pom 포함)을 기록하고, 현재 파일 상태와 달라진 프로젝트를 알려준다.
- 뷰가 보이는 동안 2초마다 확인해 바뀐 프로젝트만 다시 파싱한다. 갱신 전후로 펼침 상태를 유지한다(`reparseKeepingExpansion`). Refresh 버튼도 같은 경로를 쓰므로 펼침 상태가 유지된다.

### 17. MavenGoalsView 분리
- `SelectionTracker`(views): 다른 파트의 선택 추적(Add from Selection용).
- `PomDropSupport`(views): Finder/Project Explorer 드래그 앤 드롭 설정.
- `PomRegistryStore`(services): 등록 pom 목록의 preference 저장/복원.
- `ViewPreferences`(services): 실행 방식, 즐겨찾기/최근 실행, 마지막 실행 옵션의 preference 저장/복원. `PluginLog`(services): Error Log 기록.
- `ViewToolbar`(views): 툴바 버튼/체크박스 정의(동작은 `Handler`로 뷰에 위임). `MavenProjectUpdater`: Update Maven Project. `PomAdder`: Add POM File / Add from Selection 대화상자 흐름.
- 결과적으로 `MavenGoalsView`는 약 1,040줄(트리 구성, 필터, 실행, 즐겨찾기 메뉴 중심).

## 현재 Require-Bundle 의존성

```
org.eclipse.ui
org.eclipse.core.runtime
org.eclipse.core.resources
org.eclipse.jface
org.eclipse.ui.ide
org.eclipse.ui.console
org.eclipse.debug.core
org.eclipse.m2e.launching   (내장 Maven 런치, m2e.actions 패키지는 m2e 내부 API 표시(x-internal)라 향후 버전 변경 시 깨질 수 있음)
org.eclipse.m2e.core.ui     (Update Maven Project)
```

즉, **m2e(Maven Integration for Eclipse)가 설치된 Eclipse**가 전제 조건이다(외부 mvn 모드만 쓰더라도 번들 의존성 자체는 걸려 있음).

## 배포 시 주의 (중복 dropin 문제)
- 번들 버전이 항상 `1.0.0.qualifier`라서, `dropins/` 아래에 같은 번들이 **두 군데**(예: `dropins/com.kcube.mavenview_1.0.0.jar` 와 `dropins/com.kcube.mavenview/eclipse/plugins/...jar`) 있으면 Eclipse는 둘 중 하나(여기서는 오래된 쪽)만 로드하고 새로 배포한 jar는 무시한다.
- 실제 사례: Run 버튼이 몇 차례 재빌드/재시작에도 나타나지 않았는데, 코드·아이콘 문제가 아니라 `bundles.info`가 가리키는 12:17 빌드의 옛 jar가 계속 로드되고 있었음. 옛 폴더를 `dropins` 밖으로 치우자 즉시 해결됨.
- 확인 방법: `configuration/org.eclipse.equinox.simpleconfigurator/bundles.info`에서 `com.kcube.mavenview` 경로를 확인하고, 해당 jar 안의 클래스에 방금 추가한 문자열이 있는지 `grep -a`로 확인. 항상 **jar 파일 하나만** `dropins/` 바로 아래에 둘 것.

## 알려진 제한사항 / 참고
- `org.eclipse.m2e.actions.MavenLaunchConstants`는 m2e 내부(`x-internal`) API라, m2e 버전이 바뀌면 호환이 깨질 가능성이 있다.
- "Add from Selection"은 워크스페이스 리소스(Project Explorer 등) 선택만 추적하며, Finder 같은 외부 앱의 선택은 추적하지 않는다(드래그 앤 드롭으로 보완).
- Update Maven Project는 워크스페이스에 이미 임포트된 프로젝트에만 동작하며, 파일 다이얼로그/드래그로 등록한 워크스페이스 밖의 pom.xml에는 적용되지 않는다.

## 빌드 방법 (Tycho / p2 업데이트 사이트)

```bash
mvn clean verify
```

Maven 3.9+ 와 JDK 17+ 가 필요하다. 결과물은 `com.kcube.mavenview.update-site/target/` 에 생성된다.
- `repository/` — p2 리포지토리 (웹에 그대로 호스팅하면 Marketplace 등록용 업데이트 사이트 URL이 된다)
- `com.kcube.mavenview.update-site-<버전>.zip` — Help > Install New Software > Add > Archive 로 설치 테스트 가능

## 빌드 방법 (스크립트)

```bash
./build.sh             # dist/com.kcube.mavenview_<버전>.jar 생성
./build.sh --install   # 빌드 후 ECLIPSE_HOME 의 dropins 에 설치
./build.sh --test      # 단위 테스트 실행
```

`ECLIPSE_HOME`(…/Eclipse.app/Contents/Eclipse)과 `JAVA_HOME`(JDK 17+)은 생략 시 자동 탐지한다.

## 빌드 방법 (수동)
Eclipse 대상 버전의 plugins jar를 클래스패스로 지정해 Java 17로 컴파일한 뒤 jar로 묶는다.

```bash
P=/Applications/<Eclipse>.app/Contents/Eclipse/plugins
CP=$(find $P -name "*.jar" -size +0 | tr '\n' ':')
cd com.kcube.mavenview
javac --release 17 -encoding UTF-8 -cp "$CP" -d out $(find src -name "*.java")
cp -r META-INF plugin.xml icons out/
jar --create --file ../dist/com.kcube.mavenview_<버전>.jar --manifest META-INF/MANIFEST.MF -C out com -C out icons -C out plugin.xml
```
