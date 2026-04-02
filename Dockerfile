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

# Variables no sensibles (las sensibles se inyectan en runtime)
ENV COSMOS_DB_QUESTIONS_CONTAINER=Questions \
    QUESTIONS_SEED_FILE=classpath:/Apalabrazos/data/questions2.json \
    SEED_QUESTIONS_ON_START=false \
    JAVA_OPTS=""

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "set -e; if [ \"$SEED_QUESTIONS_ON_START\" = \"true\" ]; then echo 'Seeding questions into Cosmos DB...'; java -cp classes:dependency/* Apalabrazos.backend.tools.CosmosQuestionSeeder; fi; exec java $JAVA_OPTS -cp classes:dependency/* Apalabrazos.MainApp"]
