FROM ghcr.io/navikt/baseimages/temurin:17

COPY build/libs/*-all.jar app.jar
