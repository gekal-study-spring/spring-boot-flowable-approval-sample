package cn.gekal.spring.approval.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.gekal.spring.approval.application.command.StartExpenseRequestCommand;
import cn.gekal.spring.approval.application.service.ApprovalTaskService;
import cn.gekal.spring.approval.application.service.ExpenseRequestService;
import cn.gekal.spring.approval.application.service.ProcessDefinitionService;
import cn.gekal.spring.approval.domain.model.ApprovalTask;
import cn.gekal.spring.approval.domain.model.ApproverRole;
import cn.gekal.spring.approval.domain.model.ExpenseRequest;
import cn.gekal.spring.approval.domain.model.InvalidProcessDefinitionException;
import cn.gekal.spring.approval.domain.model.ProcessDefinitionVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * フロー定義を実行時に差し替えられることの検証。
 *
 * <p>アプリを再起動せずに BPMN を配備し直すと、**新規の起票だけが新しい版で始まり、走行中の申請は起票時の版のまま**であることを確かめる。この2点が成り立つから、
 * フロー定義をアプリの外で運用できる。
 *
 * <p>他のテストと同じ H2 を共有すると版を増やす操作が干渉するため、専用のインメモリ DB を使う。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "flowable.async-executor-activate=false",
      "spring.datasource.url=jdbc:h2:mem:expense-approval-definition;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    })
class ProcessDefinitionDeploymentTest {

  private static final String RESOURCE_NAME = "expense-approval.bpmn20.xml";

  @Autowired private ProcessDefinitionService processDefinitionService;
  @Autowired private ExpenseRequestService expenseRequestService;
  @Autowired private ApprovalTaskService approvalTaskService;

  /**
   * 各テストの前に同梱の BPMN を最新版として置き直す。
   *
   * <p>版は消せず積み上がる一方なので、前のテストが配備した版が残っていると「最新＝同梱の内容」が崩れる。基準を揃えてからでないと、差し替えの前後を比べられない。
   */
  @BeforeEach
  void deployBaseline() {
    processDefinitionService.deploy(RESOURCE_NAME, packagedBpmn(), "admin");
  }

  @Test
  @DisplayName("配備し直すと新規の起票は新しい版で始まり、走行中の申請は起票時の版のまま走る")
  void redeployAffectsOnlyNewInstances() {
    // 差し替え前に1件起票し、走行中にしておく
    ExpenseRequest before = start("差し替え前の申請");
    assertThat(taskNameOf(before.processInstanceId())).isEqualTo("課長承認");

    int versionBefore = latestVersion();

    // 課長承認タスクの表示名だけを変えた BPMN を配備する（アプリの再起動はしない）
    ProcessDefinitionVersion deployed =
        processDefinitionService.deploy(RESOURCE_NAME, packagedBpmnWith("課長承認", "課長確認"), "admin");

    assertThat(deployed.version()).isEqualTo(versionBefore + 1);
    assertThat(deployed.latest()).isTrue();

    // 新規の起票は新しい版で始まる
    ExpenseRequest after = start("差し替え後の申請");
    assertThat(taskNameOf(after.processInstanceId())).isEqualTo("課長確認");

    // すでに走っている申請は起票時の版のまま
    assertThat(taskNameOf(before.processInstanceId())).isEqualTo("課長承認");
  }

  @Test
  @DisplayName("版の一覧には走行中の件数が付き、切り戻すと前の内容が新しい版として入る")
  void rollbackDeploysPreviousContentAsNewVersion() {
    ProcessDefinitionVersion original = latest();
    processDefinitionService.deploy(RESOURCE_NAME, packagedBpmnWith("課長承認", "課長点検"), "admin");
    ExpenseRequest running = start("切り戻し前の申請");
    assertThat(taskNameOf(running.processInstanceId())).isEqualTo("課長点検");

    ProcessDefinitionVersion rolledBack =
        processDefinitionService.rollbackTo(original.processDefinitionId(), "admin");

    // 古い版を消すのではなく、その内容で新しい版を作る
    assertThat(rolledBack.version()).isGreaterThan(original.version());
    assertThat(taskNameOf(start("切り戻し後の申請").processInstanceId())).isEqualTo("課長承認");

    List<ProcessDefinitionVersion> versions = processDefinitionService.findVersions();
    assertThat(versions).hasSizeGreaterThanOrEqualTo(3);
    assertThat(versions.getFirst().version()).isEqualTo(rolledBack.version());
    assertThat(versions)
        .filteredOn(version -> version.processDefinitionId().equals(original.processDefinitionId()))
        .singleElement()
        .satisfies(version -> assertThat(version.latest()).isFalse());
  }

  @Test
  @DisplayName("壊れた BPMN は配備の時点で弾かれ、既存の版は影響を受けない")
  void brokenBpmnIsRejectedAtDeployTime() {
    int versionBefore = latestVersion();

    assertThatThrownBy(
            () ->
                processDefinitionService.deploy(
                    RESOURCE_NAME,
                    "<definitions>閉じられていない".getBytes(StandardCharsets.UTF_8),
                    "admin"))
        .isInstanceOf(InvalidProcessDefinitionException.class);

    assertThat(latestVersion()).isEqualTo(versionBefore);
    assertThat(taskNameOf(start("配備失敗後の申請").processInstanceId())).isEqualTo("課長承認");
  }

  @Test
  @DisplayName("拡張子が BPMN でないファイルは配備できない")
  void resourceNameMustLookLikeBpmn() {
    assertThatThrownBy(
            () -> processDefinitionService.deploy("expense-approval.txt", packagedBpmn(), "admin"))
        .isInstanceOf(InvalidProcessDefinitionException.class)
        .hasMessageContaining(".bpmn20.xml");
  }

  @Test
  @DisplayName("空のファイルは配備できない")
  void emptyFileIsRejected() {
    assertThatThrownBy(() -> processDefinitionService.deploy(RESOURCE_NAME, new byte[0], "admin"))
        .isInstanceOf(InvalidProcessDefinitionException.class);
  }

  private ExpenseRequest start(String title) {
    return expenseRequestService.start(
        new StartExpenseRequestCommand(
            "yamada", title, 50_000L, LocalDate.now().minusDays(1), "旅費交通費", null));
  }

  private String taskNameOf(String processInstanceId) {
    return approvalTaskService.findMyTasks("sato", List.of(ApproverRole.MANAGER.groupId())).stream()
        .filter(task -> task.processInstanceId().equals(processInstanceId))
        .findFirst()
        .map(ApprovalTask::name)
        .orElseThrow(() -> new AssertionError("承認タスクが見つかりません: " + processInstanceId));
  }

  private ProcessDefinitionVersion latest() {
    return processDefinitionService.findVersions().getFirst();
  }

  private int latestVersion() {
    return latest().version();
  }

  /** 同梱の BPMN の一部を置き換えたものを作る。差し替えが効いたことをタスク名で確かめるため。 */
  private static byte[] packagedBpmnWith(String target, String replacement) {
    return new String(packagedBpmn(), StandardCharsets.UTF_8)
        .replace(target, replacement)
        .getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] packagedBpmn() {
    try (InputStream stream =
        new ClassPathResource("processes/expense-approval.bpmn20.xml").getInputStream()) {
      return stream.readAllBytes();
    } catch (IOException e) {
      throw new AssertionError("同梱の BPMN を読み込めません", e);
    }
  }
}
