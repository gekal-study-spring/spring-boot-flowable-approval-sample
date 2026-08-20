package cn.gekal.spring.approval.presentation.api;

import cn.gekal.spring.approval.application.service.ProcessDefinitionService;
import cn.gekal.spring.approval.domain.model.InvalidProcessDefinitionException;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 承認フロー定義の運用 API。
 *
 * <p>BPMN をアプリの外で管理し、**再起動なしに**フローを差し替えるための入口。配備した瞬間から新規の起票が新しい版で始まり、走行中の申請は起票時の版のまま完了する。
 *
 * <p>フローを差し替えると業務の流れそのものが変わるため、専用の管理者ロールにだけ開放している（{@code SecurityConfig}）。
 */
@RestController
@RequestMapping("/api/admin/process-definitions")
public class ProcessDefinitionApi {

  private final ProcessDefinitionService processDefinitionService;

  public ProcessDefinitionApi(ProcessDefinitionService processDefinitionService) {
    this.processDefinitionService = processDefinitionService;
  }

  /** 配備済みの版を新しい順に返す。 */
  @GetMapping
  public List<ProcessDefinitionVersionResponse> versions() {
    return processDefinitionService.findVersions().stream()
        .map(ProcessDefinitionVersionResponse::from)
        .toList();
  }

  /** BPMN をアップロードして新しい版として配備する。 */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ProcessDefinitionVersionResponse> deploy(
      @RequestParam("file") MultipartFile file, Principal principal) {
    String resourceName = file.getOriginalFilename();
    if (resourceName == null || resourceName.isBlank()) {
      throw new InvalidProcessDefinitionException("ファイル名が指定されていません");
    }
    byte[] bpmnXml;
    try {
      bpmnXml = file.getBytes();
    } catch (IOException e) {
      throw new InvalidProcessDefinitionException("アップロードされたファイルを読み込めません", e);
    }
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ProcessDefinitionVersionResponse.from(
                processDefinitionService.deploy(resourceName, bpmnXml, principal.getName())));
  }

  /** 指定した版の BPMN XML を返す。差し替え前の内容を控えるときに使う。 */
  @GetMapping(value = "/{processDefinitionId}/bpmn", produces = MediaType.APPLICATION_XML_VALUE)
  public String bpmn(@PathVariable String processDefinitionId) {
    return processDefinitionService.findBpmnXml(processDefinitionId);
  }

  /** 指定した版の内容で配備し直す（切り戻し）。 */
  @PostMapping("/{processDefinitionId}/rollback")
  public ResponseEntity<ProcessDefinitionVersionResponse> rollback(
      @PathVariable String processDefinitionId, Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ProcessDefinitionVersionResponse.from(
                processDefinitionService.rollbackTo(processDefinitionId, principal.getName())));
  }

  /** 指定した版で新規に起票できないようにする。 */
  @PostMapping("/{processDefinitionId}/suspend")
  public ResponseEntity<Void> suspend(@PathVariable String processDefinitionId) {
    processDefinitionService.suspend(processDefinitionId);
    return ResponseEntity.noContent().build();
  }

  /** 停止した版を再び起票できるようにする。 */
  @PostMapping("/{processDefinitionId}/activate")
  public ResponseEntity<Void> activate(@PathVariable String processDefinitionId) {
    processDefinitionService.activate(processDefinitionId);
    return ResponseEntity.noContent().build();
  }
}
