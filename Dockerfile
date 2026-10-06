FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-27@sha256:cae6309e702bfc2c728178fdfed5c697d33aa18400f279f154bbfe25f3146640

ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb_NO.UTF-8' TZ="Europe/Oslo"

COPY build/libs/dp-mellomlagring-all.jar /app.jar

ENTRYPOINT ["java", "-jar", "/app.jar"]
