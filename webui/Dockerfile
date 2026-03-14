FROM maven:3.9.9-eclipse-temurin-17 AS java-builder

WORKDIR /build
COPY pom.xml ./
COPY src ./src

RUN mvn -q -DskipTests compile dependency:copy-dependencies -DincludeScope=runtime

FROM python:3.11-slim

RUN apt-get update && apt-get install -y --no-install-recommends openjdk-21-jre-headless && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY webui/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY webui/ ./
COPY --from=java-builder /build/target/classes /app/target/classes
COPY --from=java-builder /build/target/dependency /app/target/dependency

EXPOSE 8501

CMD ["streamlit", "run", "app.py", "--server.address=0.0.0.0", "--server.port=8501"]
