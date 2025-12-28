#!/bin/bash
set -e

################################
# 기본 설정
################################
timedatectl set-timezone Asia/Seoul

apt-get update -y
apt-get upgrade -y
apt-get install -y curl git wget unzip ruby nginx

################################
# Node.js 20 + PM2
################################
curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
apt-get install -y nodejs

npm install -g pm2

################################
# CodeDeploy Agent
################################
cd /home/ubuntu
wget https://aws-codedeploy-ap-northeast-2.s3.ap-northeast-2.amazonaws.com/latest/install
chmod +x install
./install auto

systemctl enable codedeploy-agent
systemctl start codedeploy-agent

################################
# Nginx 설정 (ALB 기준으로 수정할 것!!!!!!!)
################################
rm -f /etc/nginx/sites-enabled/*

cat <<'EOF' > /etc/nginx/sites-available/default
server {
    listen 80 default_server;
    listen [::]:80 default_server;

    server_name _;

    location /health {
        return 200 "OK";
    }

    location /_next/ {
        proxy_pass http://localhost:3000/_next/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location ^~ /api/ {
        proxy_pass http://internal-ai1018-backend-internal-alb-985356161.ap-southeast-1.elb.amazonaws.com/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location / {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
    }
}
EOF

ln -s /etc/nginx/sites-available/default /etc/nginx/sites-enabled/default

nginx -t
systemctl restart nginx
systemctl enable nginx

################################
# CloudWatch Logs 설정
################################
wget -q https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb
dpkg -i amazon-cloudwatch-agent.deb || apt-get -f install -y


cat <<'EOF' > /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json
{
  "logs": {
    "logs_collected": {
      "files": {
        "collect_list": [
          {
            "file_path": "/var/log/nginx/access.log",
            "log_group_name": "/ai1018/ec2/frontend/nginx/access",
            "log_stream_name": "{instance_id}",
            "timezone": "Local"
          },
          {
            "file_path": "/var/log/nginx/error.log",
            "log_group_name": "/ai1018/ec2/frontend/nginx/error",
            "log_stream_name": "{instance_id}",
            "timezone": "Local"
          },
          {
            "file_path": "/home/ubuntu/.pm2/logs/*-out.log",
            "log_group_name": "/ai1018/ec2/frontend/node/stdout",
            "log_stream_name": "{instance_id}",
            "timezone": "Local"
          },
          {
            "file_path": "/home/ubuntu/.pm2/logs/*-error.log",
            "log_group_name": "/ai1018/ec2/frontend/node/stderr",
            "log_stream_name": "{instance_id}",
            "timezone": "Local"
          }
        ]
      }
    }
  }
}
EOF

/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -a fetch-config \
  -m ec2 \
  -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json \
  -s

################################
# App 디렉토리 & PM2
################################
mkdir -p /home/ubuntu/app
chown -R ubuntu:ubuntu /home/ubuntu/app

pm2 startup systemd -u ubuntu --hp /home/ubuntu

echo "User Data setup completed successfully"
