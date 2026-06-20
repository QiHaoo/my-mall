# my-mall 开发环境搭建指南

> 本地开发中间件环境，基于 **WSL2 + Docker Engine + Docker Compose**，无需虚拟机。

---

## 一、为什么选这套方案

| 对比项 | VMware+CentOS+Docker | WSL2+Docker（推荐） |
|-------|---------------------|-------------------|
| 虚拟化层数 | 3 层（VMware+OS+Docker） | 1 层（WSL2 内核+Docker） |
| 内存占用 | 固定占用 2-4GB（OS本身） | 按需分配，空闲自动回收 |
| 性能 | 有虚拟化开销 | 接近原生 Linux |
| 运维 | 要维护整个 Linux 系统 | 只管容器 |
| 适用场景 | 模拟生产集群 | **本地开发学习** |

> CentOS 8 已于 2021 年底 EOL，不再维护。学习中间件完全不需要虚拟机这一层。

---

## 二、环境准备（一次性，按顺序执行）

> ⚠️ 以下步骤**必须按顺序执行**，每一步都是后续步骤的前置依赖。跳步会导致各类报错。

### 步骤 1：启用 WSL2

以**管理员**身份打开 PowerShell：

```powershell
wsl --install
```

这会自动启用 WSL2 并安装默认的 Ubuntu。安装完**重启电脑**。

重启后打开 Ubuntu（开始菜单），设置用户名密码（自定义）。验证：

```powershell
wsl -l -v
# 应看到 Ubuntu 状态为 Running，VERSION 为 2
```

### 步骤 2：配置 WSL2 资源（推荐）

WSL2 默认不限制内存，可能吃掉过多宿主机内存。在 Windows 用户目录创建 `C:\Users\<你的用户名>\.wslconfig`：

```ini
[wsl2]
memory=8GB
processors=4
swap=2GB
```

修改后 PowerShell 执行 `wsl --shutdown` 再重新进入生效。

### 步骤 3：启用 WSL2 systemd（关键前置）

> Docker 的 `systemctl` 命令依赖 systemd，而 **WSL2 默认未开启 systemd**。不做这步，后面执行 `sudo systemctl start docker` 会报 `System has not been booted with systemd as init system`。

进入 WSL2，写入配置：

```bash
sudo tee /etc/wsl.conf <<-'EOF'
[boot]
systemd=true
EOF
```

然后切到 **Windows PowerShell**（不是 WSL2 内）关闭 WSL2 让配置生效：

```powershell
wsl --shutdown
```

重新进入 WSL2（开始菜单打开 Ubuntu），验证 systemd 已生效：

```bash
ps -p 1 -o comm=
# 应输出 systemd（若输出 init 或其他，说明没生效，检查 /etc/wsl.conf 内容）
```

### 步骤 4：安装 Docker Engine（用阿里云源）

> ⚠️ **国内网络注意**：Docker 官方源 `download.docker.com` 在国内无法访问（curl 会报 `Connection reset by peer`）。**必须使用国内镜像源**，下方用阿里云源。

在 WSL2 中执行：

```bash
# 1. 清理可能残留的失败文件
sudo rm -f /etc/apt/keyrings/docker.gpg

# 2. 更新包并安装依赖
sudo apt update && sudo apt upgrade -y
sudo apt install -y ca-certificates curl gnupg lsb-release

# 3. 添加阿里云 Docker 镜像源 GPG key
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# 4. 添加阿里云 Docker apt 仓库
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://mirrors.aliyun.com/docker-ce/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 5. 安装 Docker Engine + Compose 插件
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 6. 当前用户加入 docker 组（免 sudo）
sudo usermod -aG docker $USER
# 执行 newgrp docker 让组权限立即生效（或重新进入 WSL）
newgrp docker
```

<details>
<summary>备用镜像源（阿里云不通时换这几个，任选其一）</summary>

| 镜像源 | 替换上面第 3、4 步的域名部分 |
|--------|---------------------------|
| 清华大学 | `mirrors.tuna.tsinghua.edu.cn/docker-ce` |
| 中科大 | `mirrors.ustc.edu.cn/docker-ce` |

例如用清华源，第 3、4 步改为：
```bash
curl -fsSL https://mirrors.tuna.tsinghua.edu.cn/docker-ce/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://mirrors.tuna.tsinghua.edu.cn/docker-ce/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
```
</details>

启动 Docker（步骤 3 的 systemd 已启用，这里不会再报错）：

```bash
sudo systemctl enable docker
sudo systemctl start docker
```

验证 Docker 服务运行：

```bash
docker --version
docker compose version
```

### 步骤 5：配置 Docker Hub 镜像加速（必做，否则拉镜像超时）

> Docker 装好后，`docker pull` 拉镜像默认走 Docker Hub（`registry-1.docker.io`），国内**直连必然超时**（报 `i/o timeout`）。**必须配置镜像加速器**。

> ⚠️ **写完 `daemon.json` 必须重启 Docker 才生效**。不重启的话 `docker info` 看不到加速器配置，拉镜像仍会超时。

**方案 A：阿里云专属加速器（强烈推荐，最稳定）**

公共加速地址时好时坏，**阿里云专属加速器是个人独享的，最稳定**。免费注册 1 分钟：

1. 浏览器打开 https://cr.console.aliyun.com/
2. 支付宝/淘宝扫码登录（个人账号即可，免费）
3. 左侧菜单「镜像工具」→「镜像加速器」
4. 复制你的专属地址（形如 `https://xxxxxxx.mirror.aliyuncs.com`）
5. 写入配置（替换为你自己的地址）：

```bash
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json <<-'EOF'
{
  "registry-mirrors": [
    "https://你的专属地址.mirror.aliyuncs.com"
  ]
}
EOF
```

**方案 B：公共加速地址（不想注册时用，可能不稳定）**

```bash
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json <<-'EOF'
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://docker.1ms.run",
    "https://docker.xuanyuan.me",
    "https://dockerproxy.com"
  ]
}
EOF
```

**重启 Docker 让配置生效（无论用方案 A 还是 B，都必须执行）：**

```bash
sudo systemctl daemon-reload
sudo systemctl restart docker
```

**验证加速器已加载（必须看到镜像地址列表才算成功）：**

```bash
docker info | grep -A 8 "Registry Mirrors"
```

如果输出为空，说明配置没生效——检查 `/etc/docker/daemon.json` 内容是否正确，确认执行了上面的 restart 命令。

### 步骤 6：验证 Docker 完全可用

```bash
docker run hello-world
```

看到 `Hello from Docker!` 欢迎信息即说明 Docker 完全正常。

> 如果这步仍报 `i/o timeout`，说明加速器没配通。回步骤 5 检查：①daemon.json 内容正确 ②执行了 restart ③`docker info` 能看到加速器列表。实在不行用阿里云专属加速器（方案 A）。

### 步骤 7：安装 JDK 21

> IDEA 内置 JDK 下载功能，无需去官网手动下载。

IDEA → File → Project Structure → SDK → **+** → Add JDK → 选 **Eclipse Temurin (Adoptium)** → 版本选 **21 (LTS)** → Download。

IDEA 会自动下载安装，完成后 Project Settings → SDK 选 21。系统默认 JDK 17 不受影响，每个项目的 SDK 独立。

---

## 三、启动中间件

> ⚠️ 以下命令**全部在 WSL2 终端**中执行（不是 Windows PowerShell），因为 Docker Engine 装在 WSL2 里。

首先进入项目目录（Windows 的 `D:\WorkSpace\my-mall` 在 WSL2 里对应 `/mnt/d/WorkSpace/my-mall`）：

```bash
cd /mnt/d/WorkSpace/my-mall
```

> 小技巧：在 WSL2 里执行 `echo 'alias mall="cd /mnt/d/WorkSpace/my-mall"' >> ~/.bashrc && source ~/.bashrc`，以后直接 `mall` 就进项目目录。

> 首次启动各服务会拉取镜像，需联网（走步骤 5 配的加速器）。建议按阶段渐进启动，避免一次占满内存。

### 按阶段渐进启动（推荐）

```bash
# 阶段一二：核心（Nacos + MySQL + Redis + Seata + Sentinel）
docker compose --profile core up -d

# 阶段三：消息队列
docker compose --profile mq up -d

# 阶段四：搜索 + 对象存储
docker compose --profile search --profile storage up -d

# 阶段五：监控
docker compose --profile monitor up -d
```

### 常用命令

```bash
# 查看运行状态
docker compose ps

# 查看某服务日志（排查启动问题用）
docker compose logs -f nacos
docker compose logs -f mysql

# 停止全部
docker compose down

# 停止并删除数据（慎用，会清空数据库）
docker compose down -v

# 重启某服务
docker compose restart redis
```

---

## 四、各服务访问信息

| 服务 | 地址 | 账号 / 密码 |
|------|------|------------|
| Nacos 控制台 | http://localhost:8848/nacos | nacos / nacos |
| MySQL | localhost:3306 | root / root123 |
| Redis | localhost:6379 | 密码 redis123 |
| RocketMQ Dashboard | http://localhost:8180 | 无 |
| OpenSearch | http://localhost:9200 | 无（已禁用安全） |
| OpenSearch Dashboards | http://localhost:5601 | 无 |
| MinIO 控制台 | http://localhost:9001 | minioadmin / minioadmin123 |
| Seata 控制台 | http://localhost:7091 | seata / seata |
| Sentinel 控制台 | http://localhost:8858 | sentinel / sentinel |
| Prometheus | http://localhost:9090 | 无 |
| Grafana | http://localhost:3000 | admin / admin123 |

---

## 五、IDEA 连接说明

### MySQL
IDEA → Database → MySQL → Host: localhost, Port: 3306, User: root, Password: root123

### Redis
IDEA 安装 Redis 插件，或用 Another Redis Desktop Manager，连接 localhost:6379，密码 redis123

### WSL2 文件
项目代码放 Windows 侧（`D:\WorkSpace\my-mall`），IDEA 直接打开。
中间件在 WSL2 内运行，通过 localhost 端口访问，无需特殊配置。

---

## 六、常见问题

**Q: 安装 Docker 时 curl 报 `Connection reset by peer` / `no valid OpenPGP data found`**
A: Docker 官方源 `download.docker.com` 在国内被墙。必须用国内镜像源安装，见「步骤 4」的阿里云源命令。阿里云不通则换清华源或中科大源（步骤 4 有折叠说明）。

**Q: 执行 `sudo systemctl start docker` 报 `System has not been booted with systemd as init system`**
A: WSL2 默认未启用 systemd。按「步骤 3」写入 `/etc/wsl.conf` 后在 PowerShell 执行 `wsl --shutdown` 重启 WSL2，用 `ps -p 1 -o comm=` 验证输出 `systemd`。

**Q: `docker pull` / `docker run hello-world` 报 `i/o timeout`（连 registry-1.docker.io 超时）**
A: Docker Hub 国内直连不通。按「步骤 5」配置镜像加速器。**三个关键检查点**：①`/etc/docker/daemon.json` 内容正确 ②执行了 `sudo systemctl restart docker` ③`docker info | grep -A 8 "Registry Mirrors"` 能看到加速器列表。公共地址不稳定时，用阿里云专属加速器（步骤 5 方案 A）。

**Q: 配了 daemon.json 但 `docker info` 看不到 Registry Mirrors**
A: 写完 daemon.json 后**忘了重启 Docker**。执行 `sudo systemctl daemon-reload && sudo systemctl restart docker`，再验证。这是最常见的疏漏。

**Q: docker compose 命令报 "permission denied"**
A: 执行 `newgrp docker` 或重新进入 WSL，确保用户在 docker 组。

**Q: 端口被占用**
A: 修改 `docker-compose.yml` 中对应服务的端口映射左侧（宿主机端口）。

**Q: OpenSearch 启动失败 "max virtual memory areas"**
A: 在 WSL2 执行：`sudo sysctl -w vm.max_map_count=262144`，并写入 `/etc/sysctl.conf` 持久化。

**Q: 内存不够**
A: 按阶段启动，不要一次全开；或在 `.wslconfig` 调大 memory；或减小各服务 JVM 内存参数。

**Q: WSL2 占用内存不释放**
A: 执行 `wsl --shutdown`（PowerShell），再重新进入。配置了 `.wslconfig` 后空闲会自动回收。
