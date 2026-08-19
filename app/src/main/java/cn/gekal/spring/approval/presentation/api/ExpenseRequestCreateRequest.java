package cn.gekal.spring.approval.presentation.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** 経費精算申請の起票リクエスト。 */
public record ExpenseRequestCreateRequest(
    @NotBlank @Size(max = 100) String title,
    @NotNull @Positive @Max(10_000_000) Long amount,
    @NotNull @PastOrPresent LocalDate expenseDate,
    @NotBlank String category,
    @Size(max = 500) String remarks) {}
