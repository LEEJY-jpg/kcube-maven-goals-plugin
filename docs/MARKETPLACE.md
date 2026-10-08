# Eclipse Marketplace Listing

Content to enter at <https://marketplace.eclipse.org> (Add Content > Solution).

## Fields

| Field | Value |
|---|---|
| Title | KCube Maven Goals |
| Categories | Build and Deploy Tools, Maven (or Tools if unavailable) |
| Tags | maven, m2e, lifecycle, goals, build |
| Update URL | `https://leejy-jpg.github.io/kcube-maven-goals-plugin/` |
| Installable Unit | `com.kcube.mavenview.feature.feature.group` |
| License | Apache-2.0 |
| Status | Production/Stable |
| Source / Repository URL | <https://github.com/LEEJY-jpg/kcube-maven-goals-plugin> |
| Bug tracker | <https://github.com/LEEJY-jpg/kcube-maven-goals-plugin/issues> |
| Support / Website | <https://github.com/LEEJY-jpg/kcube-maven-goals-plugin> |
| Eclipse versions | 2023-09 or later |
| Java | 17+ |
| Operating systems | macOS, Windows, Linux |
| Dependency | m2e (included in most Eclipse packages) |

## Short description

Browse Maven lifecycle phases and plugin goals in an Ant View-style tree and run them with a double-click.

## Full description

KCube Maven Goals adds a **Maven Goals** view to Eclipse. It shows your Maven project's lifecycle phases and plugin goals in a tree, much like the Ant view, so you can run builds without typing commands.

**Features**
- Detects the selected Maven project or `pom.xml`
- Shows lifecycle phases, plugins and configured executions, including multi-module projects
- Double-click a phase or goal to run it with your local `mvn`
- Streams Maven output to an Eclipse Console
- Goal filter and refresh (Cmd/Ctrl+Shift+F5)

**Requirements:** Eclipse 2023-09 or later, Java 17+, m2e, and a local Maven installation.

**Usage:** Window > Show View > Other... > Maven > Maven Goals.

Open source under the Apache License 2.0.

## Screenshots

- Use screenshots that show only this repository's projects; do not include internal or private project names.
- Prefer a wide window showing the whole Eclipse workbench, and one with Maven output in the Console after running a goal.

## Checklist before submitting

- [ ] Release workflow green and the update site is reachable
- [ ] Install from the update site verified in a clean Eclipse
- [ ] GitHub Issues enabled
- [ ] Screenshots prepared (no private project names)
- [ ] Logo/icon prepared (optional)
