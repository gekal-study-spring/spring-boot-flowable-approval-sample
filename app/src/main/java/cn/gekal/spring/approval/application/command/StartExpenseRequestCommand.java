package cn.gekal.spring.approval.application.command;

import java.time.LocalDate;

/**
 * 経費精算申請の起票入力。
 *
 * @param applicantId 申請者ID（認証済みユーザー）
 * @param title 件名
 * @param amountYen 申請金額（円）
 * @param expenseDate 支出日
 * @param category 費目
 * @param remarks 備考
 */
public record StartExpenseRequestCommand(
    String applicantId,
    String title,
    long amountYen,
    LocalDate expenseDate,
    String category,
    String remarks) {}
