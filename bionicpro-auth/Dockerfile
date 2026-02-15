# Stage 1: build
FROM maven:3.8.4-openjdk-17 AS build
WORKDIR /app

# Копируем POM и загружаем зависимости (кэширование)
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline -B
COPY src src
RUN mvn package -DskipTests

# Проверяем содержимое target (для отладки, можно удалить)
RUN ls -la target/
RUN ls -lh target/*.jar
# Копируем собранный JAR в известное место (переименовываем для простоты)
RUN cp target/*.jar /app/app.jar

# Stage 2: run
FROM eclipse-temurin:17-jre
WORKDIR /app

# Копируем JAR из первого этапа
COPY --from=build /app/app.jar app.jar

EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]