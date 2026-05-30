FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package dependency:copy-dependencies -DincludeScope=runtime

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /app/target/classes ./classes
COPY --from=build /app/target/dependency ./dependency

RUN chown -R app:app /app
USER app

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -cp classes:dependency/* Apalabrazos.MainApp"]
