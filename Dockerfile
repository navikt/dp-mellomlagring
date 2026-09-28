FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-27@sha256:be9eff4be1654b9db1436fe9ae333b91f00de107ea966c4650f2f9b28813793f

ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb_NO.UTF-8' TZ="Europe/Oslo"

COPY build/libs/dp-mellomlagring-all.jar /app.jar

ENTRYPOINT ["java", "-jar", "/app.jar"]
