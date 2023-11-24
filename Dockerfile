FROM ghcr.io/navikt/baseimages/temurin:21

COPY build/libs/*-all.jar app.jar
