package cn.gekal.spring.approval.infrastructure.workflow;

import cn.gekal.spring.approval.domain.model.InvalidProcessDefinitionException;
import cn.gekal.spring.approval.domain.model.ProcessDefinitionNotFoundException;
import cn.gekal.spring.approval.domain.model.ProcessDefinitionVersion;
import cn.gekal.spring.approval.domain.repository.ProcessDefinitionRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.flowable.bpmn.exceptions.XMLException;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Repository;

/**
 * 承認フロー定義を Flowable のリポジトリへ配備する実装。
 *
 * <p>配備は既存の版を書き換えず、新しい版として積み上げる。アプリは {@code startProcessInstanceByKey} で起票しており、エンジンはそのたびに DB
 * から最新版を引き直すため、**配備した時点で再起動なしに新しい版へ切り替わる**。走行中の申請は起票時の版を参照し続けるので影響を受けない。
 *
 * <p>{@code deploy()} は XSD 検証と BPMN 検証を通す（無効化する API
 * が別に用意されている＝既定は有効）。壊れた定義は配備の時点で例外になり、起票時に落ちることはない。
 */
@Repository
public class FlowableProcessDefinitionDatasource implements ProcessDefinitionRepository {

  /** Flowable がプロセス定義として解釈するリソース名の接尾辞。これ以外だと単なる添付ファイルとして格納されてしまう。 */
  private static final List<String> BPMN_SUFFIXES = List.of(".bpmn20.xml", ".bpmn");

  private final RepositoryService repositoryService;
  private final RuntimeService runtimeService;

  public FlowableProcessDefinitionDatasource(
      RepositoryService repositoryService, RuntimeService runtimeService) {
    this.repositoryService = repositoryService;
    this.runtimeService = runtimeService;
  }

  @Override
  public ProcessDefinitionVersion deploy(
      String resourceName, byte[] bpmnXml, String deploymentName) {
    if (BPMN_SUFFIXES.stream().noneMatch(resourceName::endsWith)) {
      throw new InvalidProcessDefinitionException(
          "BPMN のファイル名は .bpmn20.xml か .bpmn で終わる必要があります: " + resourceName);
    }

    Deployment deployment;
    try (InputStream stream = new ByteArrayInputStream(bpmnXml)) {
      deployment =
          repositoryService
              .createDeployment()
              .name(deploymentName)
              .addInputStream(resourceName, stream)
              .deploy();
    } catch (IOException e) {
      throw new InvalidProcessDefinitionException("BPMN の読み込みに失敗しました: " + resourceName, e);
    } catch (XMLException | FlowableException e) {
      // XMLException は XML として壊れている場合、FlowableException は BPMN 検証に落ちた場合。
      // XMLException は FlowableException を継承していないため、両方を受ける必要がある
      throw new InvalidProcessDefinitionException("BPMN 定義が不正です: " + e.getMessage(), e);
    }

    ProcessDefinition deployed =
        repositoryService
            .createProcessDefinitionQuery()
            .deploymentId(deployment.getId())
            .singleResult();
    if (deployed == null) {
      // 接尾辞は満たしているのに定義が生まれない＝XML にプロセスが1つも入っていない
      throw new InvalidProcessDefinitionException("BPMN にプロセス定義が含まれていません: " + resourceName);
    }
    return toVersion(deployed, deployment.getName(), deployed.getVersion());
  }

  @Override
  public List<ProcessDefinitionVersion> findVersions(String key) {
    List<ProcessDefinition> definitions =
        repositoryService
            .createProcessDefinitionQuery()
            .processDefinitionKey(key)
            .orderByProcessDefinitionVersion()
            .desc()
            .list();
    int latestVersion =
        definitions.stream().mapToInt(ProcessDefinition::getVersion).max().orElse(0);
    return definitions.stream()
        .map(definition -> toVersion(definition, deploymentNameOf(definition), latestVersion))
        .toList();
  }

  @Override
  public String readBpmnXml(String processDefinitionId) {
    requireExisting(processDefinitionId);
    try (InputStream stream = repositoryService.getProcessModel(processDefinitionId)) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new FlowableException("BPMN 定義の読み込みに失敗しました: " + processDefinitionId, e);
    }
  }

  @Override
  public ProcessDefinitionVersion find(String processDefinitionId) {
    ProcessDefinition definition = requireExisting(processDefinitionId);
    return toVersion(
        definition, deploymentNameOf(definition), latestVersionOf(definition.getKey()));
  }

  private int latestVersionOf(String key) {
    ProcessDefinition latest =
        repositoryService
            .createProcessDefinitionQuery()
            .processDefinitionKey(key)
            .latestVersion()
            .singleResult();
    return latest == null ? 0 : latest.getVersion();
  }

  @Override
  public void suspend(String processDefinitionId) {
    ProcessDefinition definition = requireExisting(processDefinitionId);
    if (!definition.isSuspended()) {
      repositoryService.suspendProcessDefinitionById(processDefinitionId);
    }
  }

  @Override
  public void activate(String processDefinitionId) {
    ProcessDefinition definition = requireExisting(processDefinitionId);
    if (definition.isSuspended()) {
      repositoryService.activateProcessDefinitionById(processDefinitionId);
    }
  }

  @Override
  public boolean exists(String key) {
    return repositoryService.createProcessDefinitionQuery().processDefinitionKey(key).count() > 0;
  }

  private ProcessDefinition requireExisting(String processDefinitionId) {
    ProcessDefinition definition =
        repositoryService
            .createProcessDefinitionQuery()
            .processDefinitionId(processDefinitionId)
            .singleResult();
    if (definition == null) {
      throw new ProcessDefinitionNotFoundException(processDefinitionId);
    }
    return definition;
  }

  private String deploymentNameOf(ProcessDefinition definition) {
    Deployment deployment =
        repositoryService
            .createDeploymentQuery()
            .deploymentId(definition.getDeploymentId())
            .singleResult();
    return deployment == null ? null : deployment.getName();
  }

  private ProcessDefinitionVersion toVersion(
      ProcessDefinition definition, String deploymentName, int latestVersion) {
    Deployment deployment =
        repositoryService
            .createDeploymentQuery()
            .deploymentId(definition.getDeploymentId())
            .singleResult();
    long running =
        runtimeService.createProcessInstanceQuery().processDefinitionId(definition.getId()).count();
    return new ProcessDefinitionVersion(
        definition.getId(),
        definition.getKey(),
        definition.getName(),
        definition.getVersion(),
        definition.getDeploymentId(),
        deploymentName,
        definition.getResourceName(),
        deployment == null
            ? null
            : FlowableExpenseRequestDatasource.toLocalDateTime(deployment.getDeploymentTime()),
        definition.isSuspended(),
        definition.getVersion() == latestVersion,
        running);
  }
}
