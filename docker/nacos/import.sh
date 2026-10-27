#!/bin/sh
# 把 docker/nacos/configs 下的配置发布到 Nacos。
#
# 服务启动时会从 Nacos 导入这些配置（application.yml 的 spring.config.import），
# 所以要先发布、再启动业务服务。compose 里用 nacos-init 这个一次性容器完成，
# 等它退出（docker compose ps 里显示 Exited (0)）后再在本机启动后端。
#
# 配置可重复发布（同名覆盖），每次 up 都会把本目录的版本同步过去，
# 避免"本地 Nacos 里是旧配置"这种漂移。

set -eu

NACOS_URL="${NACOS_URL:-http://nacos:8848/nacos}"

echo "等待 Nacos 就绪: ${NACOS_URL}"
until curl -fsS "${NACOS_URL}/v1/console/health/readiness" >/dev/null 2>&1; do
    sleep 2
done

for file in /configs/*.yaml; do
    [ -e "$file" ] || continue
    data_id=$(basename "$file" .yaml)
    echo "发布配置 ${data_id}"
    curl -fsS -X POST "${NACOS_URL}/v1/cs/configs" \
        --data-urlencode "dataId=${data_id}" \
        --data-urlencode "group=DEFAULT_GROUP" \
        --data-urlencode "type=yaml" \
        --data-urlencode "content@${file}" >/dev/null
done

echo "Nacos 配置发布完成"
