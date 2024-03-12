FROM maven:3.8.5-openjdk-11 AS build
COPY src /home/app/src
COPY pom.xml /home/app
RUN mvn -f /home/app/pom.xml clean package

FROM eclipse-temurin:11-jre-jammy
COPY --from=build /home/app/target/warehouse-1.0-SNAPSHOT-jar-with-dependencies.jar /app/warehouse.jar
USER 15000
CMD ["java","-jar", "/app/warehouse.jar"]