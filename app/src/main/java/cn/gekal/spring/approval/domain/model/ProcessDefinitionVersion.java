package cn.gekal.spring.approval.domain.model;

import java.time.LocalDateTime;

/**
 * デプロイ済みのプロセス定義1版。
 *
 * <p>Flowable は定義を上書きせず版を積み上げるため、同じ {@code key} に対して複数の版が並ぶ。新規の起票は常に最新版で始まり、走行中の申請は起票時の版のまま最後まで走る。
 *
 * @param processDefinitionId 定義ID（{@code キー:版数:UUID} の複合形式）
 * @param key プロセス定義キー
 * @param name プロセス定義の表示名
 * @param version 同一キー内の版数
 * @param deploymentId デプロイID
 * @param deploymentName デプロイ名（誰がどう入れたかの手掛かりになる）
 * @param resourceName BPMN リソース名
 * @param deployedAt デプロイ日時
 * @param suspended 停止中か。停止中の版では新規に起票できない
 * @param latest 最新版か。新規の起票はこの版で始まる
 * @param runningInstanceCount この版で走っている申請の件数
 */
public record ProcessDefinitionVersion(
    String processDefinitionId,
    String key,
    String name,
    int version,
    String deploymentId,
    String deploymentName,
    String resourceName,
    LocalDateTime deployedAt,
    boolean suspended,
    boolean latest,
    long runningInstanceCount) {}
