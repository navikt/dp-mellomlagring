FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-24@sha256:902f4bb3c34aef336fad9c417fcbce476bd05aab120d1f585c94db4a99046659

ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb_NO.UTF-8' TZ="Europe/Oslo"

COPY build/libs/dp-mellomlagring-all.jar /app.jar

ENTRYPOINT ["java", "-jar", "/app.jar"]
