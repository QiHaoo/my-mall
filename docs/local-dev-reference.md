# 本地开发环境手册

> 中间件运行在 **WSL2 + Docker Engine** 中，所有服务通过 `localhost` 访问。

---

## 一、服务连接信息

### 核心中间件（profile: core）

| 服务 | 地址 | 账号 / 密码 | 容器名 |
|------|------|------------|--------|
| Nacos 控制台 | http://localhost:8848/nacos | nacos / nacos | mall-nacos |
| Nacos gRPC | localhost:9848 | — | — |
| MySQL | localhost:3306 | root / root123 | mall-mysql |
| Redis | localhost:6379 | 密码: redis123 | mall-redis |
| Seata Server | localhost:8091 | — | mall-seata |
| Seata 控制台 | http://localhost:7091 | seata / seata | mall-seata |
| Sentinel 控制台 | http://localhost:8858 | sentinel / sentinel | mall-sentinel |

### 消息队列（profile: mq）

| 服务 | 地址 | 容器名 |
|------|------|--------|
| RocketMQ NameServer | localhost:9876 | mall-rmqnamesrv |
| RocketMQ Broker | localhost:10911 | mall-rmqbroker |
| RocketMQ Dashboard | http://localhost:8180 | mall-rmqdashboard |

### 搜索 + 存储（profile: search / storage）

| 服务 | 地址 | 账号 / 密码 | 容器名 |
|------|------|------------|--------|
| OpenSearch | localhost:9200 | 无（已禁用安全） | mall-opensearch |
| OpenSearch Dashboards | http://localhost:5601 | 无 | mall-opensearch-dashboards |
| MinIO S3 API | localhost:9000 | minioadmin / minioadmin123 | mall-minio |
| MinIO 控制台 | http://localhost:9001 | 同上 | mall-minio |

### 监控（profile: monitor）

| 服务 | 地址 | 账号 / 密码 | 容器名 |
|------|------|------------|--------|
| Prometheus | http://localhost:9090 | 无 | mall-prometheus |
| Grafana | http://localhost:3000 | admin / admin123 | mall-grafana |

---

## 二、启停命令

> 以下命令均在 **WSL2 终端** 中执行，项目目录：`/mnt/d/WorkSpace/my-mall`

### 按阶段启动

```bash
# 核心（Nacos + MySQL + Redis + Seata + Sentinel）
docker compose --profile core up -d

# 消息队列（RocketMQ）
docker compose --profile mq up -d

# 搜索 + 对象存储（OpenSearch + MinIO）
docker compose --profile search --profile storage up -d

# 监控（Prometheus + Grafana）
docker compose --profile monitor up -d

# 全部启动
docker compose --profile core --profile mq --profile search --profile storage --profile monitor up -d
```

### 停止

```bash
# 停止全部
docker compose down

# 停止全部并清除数据（慎用，会清空所有数据库/缓存）
docker compose down -v

# 停止单个服务
docker compose stop mysql
docker compose stop redis
```

### 日常操作

```bash
# 查看运行状态
docker compose ps

# 查看日志（实时跟踪）
docker compose logs -f nacos
docker compose logs -f mysql
docker compose logs --tail=50 redis

# 重启单个服务
docker compose restart redis
docker compose restart nacos

# 重新拉取镜像并启动
docker compose pull nacos
docker compose --profile core up -d
```

---

## 三、IDEA 连接配置

### MySQL
```
Host:     localhost
Port:     3306
User:     root
Password: root123
```
IDEA → 右侧 Database → + → MySQL → 填入上述信息 → Test Connection

### Redis
```
Host:     localhost
Port:     6379
Password: redis123
```
IDEA 安装 Redis 插件，或使用 Another Redis Desktop Manager 连接

### Spring Boot 应用配置（bootstrap.yml / application.yml）
```yaml
spring:
  cloud:
    nacos:
      server-addr: localhost:8848
      username: nacos
      password: nacos
  data:
    redis:
      host: localhost
      port: 6379
      password: redis123
  datasource:
    url: jdbc:mysql://localhost:3306/mall_product?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: root123
rocketmq:
  name-server: localhost:9876
  producer:
    group: mall-product-group
seata:
  registry:
    type: nacos
    nacos:
      server-addr: localhost:8848
  tx-service-group: mall-product-group
```

---

## 四、数据库

### 各服务对应库名

| 库名 | 所属服务 |
|------|---------|
| `mall_auth` | mall-auth |
| `mall_member` | mall-member |
| `mall_product` | mall-product |
| `mall_order` | mall-order |
| `mall_ware` | mall-ware |
| `mall_coupon` | mall-coupon |
| `mall_seckill` | mall-seckill |

### 初始化建库

在 `init/mysql/` 目录放置 `.sql` 文件，MySQL 容器首次启动会自动执行。例如：

```sql
-- init/mysql/01-create-databases.sql
CREATE DATABASE IF NOT EXISTS mall_auth DEFAULT CHARSET utf8mb4;
CREATE DATABASE IF NOT EXISTS mall_member DEFAULT CHARSET utf8mb4;
CREATE DATABASE IF NOT EXISTS mall_product DEFAULT CHARSET utf8mb4;
CREATE DATABASE IF NOT EXISTS mall_order DEFAULT CHARSET utf8mb4;
CREATE DATABASE IF NOT EXISTS mall_ware DEFAULT CHARSET utf8mb4;
CREATE DATABASE IF NOT EXISTS mall_coupon DEFAULT CHARSET utf8mb4;
CREATE DATABASE IF NOT EXISTS mall_seckill DEFAULT CHARSET utf8mb4;
```

---

## 五、WSL2 常用命令

> 在 **Windows PowerShell** 中执行

```powershell
# 进入 WSL2
wsl

# 关闭 WSL2（释放内存）
wsl --shutdown

# 查看 WSL2 状态
wsl -l -v

# 查看 WSL2 IP（一般不需要，用 localhost 即可）
wsl hostname -I
```

---

## 六、常见问题

| 问题 | 解决 |
|------|------|
| `docker pull` 超时 `i/o timeout` | 检查镜像加速器：`docker info \| grep "Registry Mirrors"` |
| `systemctl` 报 `not been booted with systemd` | WSL2 未启用 systemd，写入 `/etc/wsl.conf` 后 `wsl --shutdown` 重启 |
| 端口被占用 | 修改 `docker-compose.yml` 端口映射左侧（宿主机端口）；如 Windows 有 MySQL 服务需先停掉 `net stop MySQL80` |
| MySQL 启动失败 `unknown variable 'default-authentication-plugin'` | MySQL 8.4 已移除该参数，docker-compose.yml 中已删除，删掉旧数据卷重建：`docker volume rm mall-dev_mysql-data` |
| RocketMQ Broker 反复重启 NPE | docker-compose.yml 中不要挂载 `/home/rocketmq/store` 的 volume，会让 broker 初始化失败；已从 compose 中移除该挂载 |
| OpenSearch 启动失败 `max virtual memory` | `sudo sysctl -w vm.max_map_count=262144` |
| 内存不足 | 按阶段启动，或在 `.wslconfig` 调大 `memory` |
| WSL2 占用内存不释放 | `wsl --shutdown` 后重新进入 |
| Nacos 连接失败 | 确认 9848 端口（gRPC）也映射了，2.x 客户端必须 |
