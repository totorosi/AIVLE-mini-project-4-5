#!/bin/bash
set -e

#################################
# 기본 설정
#################################
apt-get update -y
apt-get upgrade -y

#################################
# 필수 패키지
#################################
apt-get install -y curl git wget unzip

#################################
# Java 17 설치
#################################
apt-get install -y openjdk-17-jdk

java -version

#################################
# 타임존 설정
#################################
timedatectl set-timezone Asia/Seoul

#################################
# CodeDeploy Agent 설치
#################################
apt-get install -y ruby

cd /home/ubuntu
wget https://aws-codedeploy-ap-northeast-2.s3.ap-northeast-2.amazonaws.com/latest/install
chmod +x install
./install auto

systemctl enable codedeploy-agent
systemctl start codedeploy-agent

#################################
# 배포 디렉토리
#################################
mkdir -p /home/ubuntu/app
chown -R ubuntu:ubuntu /home/ubuntu/app

#################################
# 로그 디렉토리
#################################
mkdir -p /var/log/spring
chown -R ubuntu:ubuntu /var/log/spring

#################################
# CloudWatch Agent 설치
#################################
cd /tmp
wget -q https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb
dpkg -i amazon-cloudwatch-agent.deb || apt-get -f install -y

#################################
# CloudWatch Logs 설정 
#################################
mkdir -p /opt/aws/amazon-cloudwatch-agent/etc

cat <<'EOF' > /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json
{
  "logs": {
    "logs_collected": {
      "files": {
        "collect_list": [
          {
            "file_path": "/home/ubuntu/app/app.log",
            "log_group_name": "/ai1018/ec2/backend/spring/application",
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

systemctl enable amazon-cloudwatch-agent

echo "Backend EC2 User Data setup completed"
