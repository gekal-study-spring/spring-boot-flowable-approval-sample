package cn.gekal.spring.approval.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.gekal.spring.approval.application.service.ExpenseRequestService;
import cn.gekal.spring.approval.domain.model.ExpenseRequest;
import cn.gekal.spring.approval.domain.model.ExpenseRequestState;
import cn.gekal.spring.approval.domain.model.ExpenseRequestStatus;
import cn.gekal.spring.approval.presentation.api.ExpenseRequestApi;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 申請 API のテスト。Spring コンテキストを起動せず standalone で検証する。 */
@ExtendWith(MockitoExtension.class)
class ExpenseRequestApiTest {

  @Mock private ExpenseRequestService expenseRequestService;

  private MockMvc mockMvc;

  private static final Principal APPLICANT = () -> "yamada";

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ExpenseRequestApi(expenseRequestService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("正しい入力なら201で申請状態を返す")
  void create() throws Exception {
    ExpenseRequest request =
        ExpenseRequest.create("yamada", "出張旅費", 50_000L, LocalDate.now(), "旅費交通費", null)
            .withProcessInstanceId("proc-1");
    when(expenseRequestService.start(any())).thenReturn(request);
    when(expenseRequestService.findState("proc-1"))
        .thenReturn(
            new ExpenseRequestState(
                request,
                ExpenseRequestStatus.IN_PROGRESS,
                "課長承認",
                null,
                null,
                null,
                0,
                LocalDateTime.now(),
                null));

    mockMvc
        .perform(
            post("/api/expense-requests")
                .principal(APPLICANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title":"出張旅費","amount":50000,"expenseDate":"%s","category":"旅費交通費"}
                    """
                        .formatted(LocalDate.now())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.processInstanceId").value("proc-1"))
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.currentTaskName").value("課長承認"));
  }

  @Test
  @DisplayName("金額が0以下なら400を返し、ワークフローは開始しない")
  void validationError() throws Exception {
    mockMvc
        .perform(
            post("/api/expense-requests")
                .principal(APPLICANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title":"出張旅費","amount":0,"expenseDate":"%s","category":"旅費交通費"}
                    """
                        .formatted(LocalDate.now())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));

    verify(expenseRequestService, never()).start(any());
  }
}
