package cn.gekal.spring.approval.infrastructure.workflow;

import cn.gekal.spring.approval.domain.model.ProcessDefinitionVersion;
import cn.gekal.spring.approval.domain.repository.ProcessDefinitionRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 同梱の BPMN を「まだ1版も無いときだけ」配備する。
 *
 * <p>Flowable の起動時オートデプロイ（{@code flowable.check-process-definitions}）は切ってある。あれは起動のたびに jar
 * 内の定義を配備し直すため、管理APIから入れた新しい版があっても、再起動でそれを追い越す古い版が入って**フローが巻き戻る**。
 * 重複判定はデプロイ名単位で行われるので、名前の違う配備とは照合されず素通りしてしまう。
 *
 * <p>そのかわりに、ここで「1版も無ければ配備する」だけを行う。空の DB（初回起動・テスト）では従来どおり動き、すでに運用中の環境では何もしない。
 */
@Component
public class PackagedProcessDefinitionBootstrap {

  private static final Logger log =
      LoggerFactory.getLogger(PackagedProcessDefinitionBootstrap.class);

  /**
   * jar に同梱している BPMN。管理APIから配備するときの初期値でもある。
   *
   * <p>プロセス定義キーを併記しているのは、配備済みかどうかをキーで判定するため。XML を読んでキーを取り出すこともできるが、
   * 起動のたびに全ファイルを解析することになるので、ここに明示する。
   */
  private static final List<PackagedProcess> PACKAGED_PROCESSES =
      List.of(
          new PackagedProcess(
              ProcessVariables.PROCESS_DEFINITION_KEY, "processes/expense-approval.bpmn20.xml"),
          new PackagedProcess(
              LoanProcessVariables.PROCESS_DEFINITION_KEY, "processes/loan-screening.bpmn20.xml"));

  static final String DEPLOYMENT_NAME = "PackagedBootstrap";

  /** 同梱プロセス1件。 */
  private record PackagedProcess(String key, String resource) {

    /** Flowable はリソース名の接尾辞でプロセス定義かどうかを判定するため、ディレクトリ名は落として渡す。 */
    String fileName() {
      return resource.substring(resource.lastIndexOf('/') + 1);
    }
  }

  private final ProcessDefinitionRepository processDefinitionRepository;

  public PackagedProcessDefinitionBootstrap(
      ProcessDefinitionRepository processDefinitionRepository) {
    this.processDefinitionRepository = processDefinitionRepository;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void deployIfAbsent() {
    PACKAGED_PROCESSES.forEach(this::deployIfAbsent);
  }

  private void deployIfAbsent(PackagedProcess process) {
    if (processDefinitionRepository.exists(process.key())) {
      log.info("プロセス定義 {} は配備済みのため、同梱の BPMN は配備しない（更新は管理APIから行う）", process.key());
      return;
    }
    ProcessDefinitionVersion deployed =
        processDefinitionRepository.deploy(
            process.fileName(), readPackagedBpmn(process.resource()), DEPLOYMENT_NAME);
    log.info("同梱の BPMN を配備した: {} v{}", deployed.key(), deployed.version());
  }

  private static byte[] readPackagedBpmn(String resource) {
    try (InputStream stream = new ClassPathResource(resource).getInputStream()) {
      return stream.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException("同梱の BPMN を読み込めません: " + resource, e);
    }
  }
}
