FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /workspace
COPY services/core-api/pom.xml services/core-api/pom.xml
COPY services/core-api/src services/core-api/src
RUN mvn -B -ntp -f services/core-api/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=builder /workspace/services/core-api/target/*.jar /app/core-api.jar
COPY infra/render/start-core.sh /app/start-core.sh
COPY catalog-candidates/obra-tdd-windows-claude-2.1.310-risk-reviewed /opt/agentfit/catalog
COPY catalog-candidates/skills-sh-candidates.json /opt/agentfit/discovery/skills-sh-candidates.json
EXPOSE 8080
ENTRYPOINT ["sh", "/app/start-core.sh"]
