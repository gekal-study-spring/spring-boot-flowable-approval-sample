# syntax=docker/dockerfile:1

# -----------------------------------------------------------------------------
# Stage 1: 静的サイトを書き出す
# -----------------------------------------------------------------------------
FROM node:24-bookworm-slim AS builder

WORKDIR /web

# 依存だけ先に入れてレイヤキャッシュを効かせる
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

COPY . .

# out/ へ書き出す（build:app は Spring Boot 側へコピーする別用途なのでここでは使わない）
RUN npm run build

# -----------------------------------------------------------------------------
# Stage 2: nginx で配信する
#
# ローカル開発用ドメイン local.gekal.cn の証明書を同梱したイメージを土台にする。
# 公的に信頼された証明書なので、ブラウザに独自 CA を入れなくても HTTPS で開ける。
# -----------------------------------------------------------------------------
FROM gekal/nginx-local-domains:latest-gekal

# 素の nginx イメージ由来の既定サイト設定を、このコンソール用の設定で置き換える
COPY nginx-locations.conf /etc/nginx/app-locations.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /web/out /usr/share/nginx/html

EXPOSE 80 443
