package cn.gekal.spring.approval.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.gekal.spring.approval.application.service.ProcessDefinitionService;
import cn.gekal.spring.approval.domain.model.InvalidProcessDefinitionException;
import cn.gekal.spring.approval.domain.model.ProcessDefinitionVersion;
import cn.gekal.spring.approval.presentation.api.ProcessDefinitionApi;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** フロー定義の運用 API のテスト。Spring コンテキストを起動せず standalone で検証する。 */
@ExtendWith(MockitoExtension.class)
class ProcessDefinitionApiTest {

  private static final Principal ADMIN = () -> "admin";

  @Mock private ProcessDefinitionService processDefinitionService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ProcessDefinitionApi(processDefinitionService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("BPMN をアップロードすると201で配備された版を返す")
  void deploy() throws Exception {
    when(processDefinitionService.deploy(eq("expense-approval.bpmn20.xml"), any(), eq("admin")))
        .thenReturn(version(2));

    mockMvc
        .perform(
            multipart("/api/admin/process-definitions")
                .file(bpmnFile("expense-approval.bpmn20.xml"))
                .principal(ADMIN))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.latest").value(true))
        .andExpect(jsonPath("$.runningInstanceCount").value(0));
  }

  @Test
  @DisplayName("定義が不正なら400を返し、既存の版は差し替えない")
  void deployInvalid() throws Exception {
    when(processDefinitionService.deploy(any(), any(), any()))
        .thenThrow(new InvalidProcessDefinitionException("BPMN 定義が不正です"));

    mockMvc
        .perform(
            multipart("/api/admin/process-definitions")
                .file(bpmnFile("broken.bpmn20.xml"))
                .principal(ADMIN))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("フロー定義が不正です"));
  }

  @Test
  @DisplayName("ファイル名が無いときは配備せず400を返す")
  void deployWithoutFileName() throws Exception {
    mockMvc
        .perform(
            multipart("/api/admin/process-definitions")
                .file(
                    new MockMultipartFile(
                        "file", "", null, "<definitions/>".getBytes(StandardCharsets.UTF_8)))
                .principal(ADMIN))
        .andExpect(status().isBadRequest());

    verify(processDefinitionService, never()).deploy(any(), any(), any());
  }

  private static MockMultipartFile bpmnFile(String name) {
    return new MockMultipartFile(
        "file", name, "application/xml", "<definitions/>".getBytes(StandardCharsets.UTF_8));
  }

  private static ProcessDefinitionVersion version(int version) {
    return new ProcessDefinitionVersion(
        "expenseApprovalProcess:" + version + ":abc",
        "expenseApprovalProcess",
        "経費精算承認プロセス",
        version,
        "deployment-1",
        "ApiDeployment(admin)",
        "expense-approval.bpmn20.xml",
        LocalDateTime.now(),
        false,
        true,
        0L);
  }
}
