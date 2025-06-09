# =========================================================================
# === Etapa 1: Construcción (Build Stage) ===
# Usamos una imagen de Maven con Java 21 para compilar nuestro proyecto.
# Esta etapa solo existe para crear el archivo .jar y no estará en la imagen final.
# =========================================================================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build

RUN apt-get update && apt-get install -y maven

# Establecemos el directorio de trabajo dentro del contenedor
WORKDIR /app

# Copiamos primero el pom.xml para aprovechar el cache de Docker.
# Si las dependencias no cambian, Docker no las descargará de nuevo.
COPY pom.xml .
RUN mvn dependency:go-offline

# Copiamos el resto del código fuente del proyecto
COPY src ./src

# Ejecutamos el comando de Maven para compilar y empaquetar la aplicación en un .jar
# -DskipTests omite la ejecución de las pruebas para acelerar la construcción.
RUN mvn clean package -DskipTests


# =========================================================================
# === Etapa 2: Ejecución (Final Stage) ===
# Partimos de una imagen base de Java 21 muy ligera, solo con lo necesario
# para ejecutar la aplicación, no para compilarla.
# =========================================================================
FROM eclipse-temurin:21-jre-alpine

# Establecemos el directorio de trabajo
WORKDIR /app/efs

# Copiamos únicamente el archivo .jar que se generó en la etapa anterior
# desde la carpeta 'target' de la etapa 'build'.
COPY --from=build /app/target/*.jar app.jar

# Exponemos el puerto 8080, que es el que usa nuestra aplicación Spring Boot
EXPOSE 8080

# Este es el comando que se ejecutará cuando el contenedor se inicie.
# Simplemente inicia nuestra aplicación.
ENTRYPOINT ["java", "-jar", "app.jar"]