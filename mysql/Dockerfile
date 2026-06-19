FROM mysql:8.0.36

# 复制MySQL配置文件
COPY my.cnf /etc/mysql/conf.d/my.cnf

# 复制初始化SQL
COPY schema.sql /docker-entrypoint-initdb.d/01-schema.sql
COPY data.sql /docker-entrypoint-initdb.d/02-data.sql
COPY mock-data.sql /docker-entrypoint-initdb.d/03-mock-data.sql
