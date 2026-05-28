FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY .mvn .mvn
COPY mvnw mvnw
COPY mvnw.cmd mvnw.cmd
COPY pom.xml pom.xml
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
ENV PORT=10000

EXPOSE 10000

ENTRYPOINT ["java","-Xms256m","-Xmx768m","-jar","/app/app.jar"]
