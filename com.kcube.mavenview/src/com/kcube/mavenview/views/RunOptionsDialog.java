package com.kcube.mavenview.views;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.kcube.mavenview.Messages;
import com.kcube.mavenview.services.RunOptions;

/** goal과 실행 옵션(-DskipTests, 프로파일, 추가 인자 등)을 고른 뒤 실행하는 "Run with Options" 대화상자. */
final class RunOptionsDialog extends Dialog
{
	private final String projectName;
	private final String initialGoals;
	private final RunOptions initialOptions;
	/** 이 pom에 선언된 프로파일 id들. 비어 있으면 선택 버튼을 비활성화한다. */
	private final List<String> availableProfiles;

	private Text goalsText;
	private Button skipTests;
	private Button offline;
	private Button updateSnapshots;
	private Text profilesText;
	private Text extraText;

	private String goals = "";
	private RunOptions options = RunOptions.NONE;

	/**
	 * @param projectName 대화상자 제목에 표시할 프로젝트 이름
	 * @param initialGoals Goals 입력란의 초기값
	 * @param initialOptions 직전에 사용한 옵션(체크박스/입력란 초기값)
	 * @param availableProfiles pom에 선언된 프로파일 id 목록(선택 대화상자에 표시)
	 */
	RunOptionsDialog(
		Shell parent,
		String projectName,
		String initialGoals,
		RunOptions initialOptions,
		List<String> availableProfiles)
	{
		super(parent);
		this.projectName = projectName;
		this.initialGoals = initialGoals;
		this.initialOptions = initialOptions;
		this.availableProfiles = availableProfiles;
	}

	/** 대화상자 제목을 설정한다. */
	@Override
	protected void configureShell(Shell shell)
	{
		super.configureShell(shell);
		shell.setText(Messages.get("dialog.title", projectName));
	}

	/** 입력 컨트롤들을 만든다. */
	@Override
	protected Control createDialogArea(Composite parent)
	{
		Composite area = (Composite) super.createDialogArea(parent);
		Composite c = new Composite(area, SWT.NONE);
		c.setLayout(new GridLayout(2, false));
		c.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		goalsText = labeledText(c, Messages.get("dialog.goals"), initialGoals);
		profilesText = profilesRow(c);
		extraText = labeledText(c, Messages.get("dialog.extra"), initialOptions.extraArgs());
		extraText.setToolTipText(Messages.get("dialog.extra.tooltip"));

		skipTests = check(c, Messages.get("dialog.skipTests"), initialOptions.skipTests());
		offline = check(c, Messages.get("dialog.offline"), initialOptions.offline());
		updateSnapshots = check(c, Messages.get("dialog.update"), initialOptions.updateSnapshots());
		return area;
	}

	/** 프로파일 입력란과, pom에 선언된 프로파일을 체크해서 고르는 "..." 버튼을 한 줄에 만든다. */
	private Text profilesRow(Composite parent)
	{
		new Label(parent, SWT.NONE).setText(Messages.get("dialog.profiles"));
		Composite row = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(2, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		row.setLayout(layout);
		row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		Text t = new Text(row, SWT.BORDER);
		GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
		gd.widthHint = 300;
		t.setLayoutData(gd);
		t.setText(initialOptions.profiles() == null ? "" : initialOptions.profiles());
		t.setToolTipText(Messages.get("dialog.profiles.tooltip"));
		Button pick = new Button(row, SWT.PUSH);
		pick.setText("...");
		pick.setToolTipText(Messages.get("dialog.profiles.pick"));
		pick.setEnabled(!availableProfiles.isEmpty());
		pick.addListener(SWT.Selection, e -> {
			Set<String> current = new LinkedHashSet<>(Arrays.asList(t.getText().replaceAll("\\s+", "").split(",")));
			ProfilePicker picker = new ProfilePicker(getShell(), availableProfiles, current);
			if (picker.open() == OK)
				t.setText(String.join(",", picker.selected()));
		});
		return t;
	}

	/** pom에 선언된 프로파일을 체크박스로 고르는 작은 대화상자. 목록에 없는 기존 입력값(예: !dev)은 유지한다. */
	private static final class ProfilePicker extends Dialog
	{
		private final List<String> available;
		private final Set<String> current;
		private final List<Button> checks = new java.util.ArrayList<>();
		private List<String> selected = List.of();

		ProfilePicker(Shell parent, List<String> available, Set<String> current)
		{
			super(parent);
			this.available = available;
			this.current = current;
		}

		@Override
		protected void configureShell(Shell shell)
		{
			super.configureShell(shell);
			shell.setText(Messages.get("dialog.profiles.pick"));
		}

		@Override
		protected Control createDialogArea(Composite parent)
		{
			Composite area = (Composite) super.createDialogArea(parent);
			for (String id : available)
			{
				Button b = new Button(area, SWT.CHECK);
				b.setText(id);
				b.setSelection(current.contains(id));
				checks.add(b);
			}
			return area;
		}

		@Override
		protected void okPressed()
		{
			Set<String> result = new LinkedHashSet<>();
			for (Button b : checks)
			{
				if (b.getSelection())
					result.add(b.getText());
			}
			for (String c : current)
			{
				if (!c.isEmpty() && !available.contains(c))
					result.add(c);
			}
			selected = List.copyOf(result);
			super.okPressed();
		}

		List<String> selected()
		{
			return selected;
		}
	}

	/** 라벨과 한 줄 입력란 한 쌍을 만든다. */
	private static Text labeledText(Composite parent, String label, String value)
	{
		new Label(parent, SWT.NONE).setText(label);
		Text t = new Text(parent, SWT.BORDER);
		GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
		gd.widthHint = 360;
		t.setLayoutData(gd);
		t.setText(value == null ? "" : value);
		return t;
	}

	/** 두 칸을 차지하는 체크박스를 만든다. */
	private static Button check(Composite parent, String label, boolean selected)
	{
		Button b = new Button(parent, SWT.CHECK);
		b.setText(label);
		b.setSelection(selected);
		b.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
		return b;
	}

	/** 확인 시 입력값을 읽어 둔다(대화상자가 닫힌 뒤에는 컨트롤에 접근할 수 없으므로). */
	@Override
	protected void okPressed()
	{
		goals = goalsText.getText().trim();
		options = new RunOptions(
			skipTests.getSelection(),
			offline.getSelection(),
			updateSnapshots.getSelection(),
			profilesText.getText().trim(),
			extraText.getText().trim());
		super.okPressed();
	}

	/** 사용자가 입력한 옵션을 반환한다. */
	RunOptions getOptions()
	{
		return options;
	}

	/** 옵션까지 합쳐진 최종 명령행(goals 뒤에 옵션)을 반환한다. goals가 비어 있으면 빈 문자열. */
	String getCommandLine()
	{
		return goals.isEmpty() ? "" : options.toCommandLine(goals);
	}
}
