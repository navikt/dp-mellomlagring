FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:30b19e6bba040c997bbcf5ae8fa6472ee469f394804696640a954657f0cf925c

ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb_NO.UTF-8' TZ="Europe/Oslo"

COPY build/libs/dp-mellomlagring-all.jar /app.jar

ENTRYPOINT ["java", "-jar", "/app.jar"]
