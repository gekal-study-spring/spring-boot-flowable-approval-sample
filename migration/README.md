# migration

Flowable が使うデータベーススキーマを Flyway で管理するモジュール。

**アプリ起動時にはマイグレーションを流さない。** 複数インスタンスが同時起動するとマイグレーションが
競合するため、compose の `migration` サービスや CI/CD の専用ステップから明示的に実行する。

## 構成

```
config/flyway.toml                       環境別ブロック（ENV で切り替え）
src/main/resources/db/migration/
  schema/  V1_0_0__flowable_common.sql   Flowable 共通テーブル (ACT_GE_*, FLW_RU_BATCH*)
           V1_0_1__flowable_engine.sql   プロセスエンジン実行時テーブル (ACT_RE_*, ACT_RU_*)
           V1_0_2__flowable_history.sql  履歴テーブル (ACT_HI_*)
           V1_0_3__flowable_identity.sql IDM テーブル (ACT_ID_*)
           V1_0_4__flowable_eventregistry.sql イベントレジストリ (FLW_EVENT_*)
  data/dev/                              環境別データ（DML）
```

`schema/` の SQL は Flowable 8.0.0 の jar に同梱された公式の create スクリプトをそのまま取り込んだもの
（出典は各ファイル冒頭のコメント）。**既存ファイルは書き換えない。** Flowable を上げるときは、公式の
upgrade スクリプトを新しい `V` ファイルとして追加する。

## 実行

```bash
# ローカル（デフォルトの接続先は localhost:15432 / expense_approval）
./gradlew migration:flywayMigrate
./gradlew migration:flywayInfo
./gradlew migration:flywayClean     # 破壊的。ローカル専用

# 接続先を変える場合
DATASOURCE_URL=jdbc:postgresql://localhost:15432/expense_approval \
DATASOURCE_USERNAME=flowable DATASOURCE_PASSWORD=secret \
  ./gradlew migration:flywayMigrate

# コンテナから（compose の migration サービスと同じ経路）
docker compose run --rm migration
```
