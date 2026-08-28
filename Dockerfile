FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:d428891aceba376ef365770a608090f13d505312aa8d6640e49478aba862ee12

ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb_NO.UTF-8' TZ="Europe/Oslo"

COPY build/libs/dp-mellomlagring-all.jar /app.jar

ENTRYPOINT ["java", "-jar", "/app.jar"]
