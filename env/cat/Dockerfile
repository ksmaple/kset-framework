FROM tomcat:8.5.41-jre8-alpine

ENV TZ=Asia/Shanghai

COPY cat-home.war /usr/local/tomcat/webapps/cat.war
COPY datasources.xml /data/appdatas/cat/datasources.xml
COPY datasources.sh /usr/local/tomcat/datasources.sh

WORKDIR /usr/local/tomcat

RUN sed -i "s/port=\"8080\"/port=\"8080\" URIEncoding=\"utf-8\"/g" conf/server.xml \
    && chmod +x datasources.sh \
    && ln -s /lib /lib64 \
    && apk add --no-cache bash tini libc6-compat linux-pam krb5 krb5-libs

CMD ["/bin/sh", "-c", "ln -sf /lib/libc.musl-x86_64.so.1 /lib/ld-linux-x86-64.so.2 2>/dev/null || true; ./datasources.sh && catalina.sh run"]
