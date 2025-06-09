# =========================================================================
# === Etapa 1: Construcción (Build Stage) ===
# Usamos una imagen oficial de Maven con Java 21 sobre Alpine Linux.
# Esta imagen ya tiene Maven instalado, por lo que no necesitamos instalarlo.
# =========================================================================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build

# Establecemos el directorio de trabajo dentro del contenedor
WORKDIR /app

# Copiamos primero el pom.xml para aprovechar el cache de Docker.
COPY pom.xml .
# Optimizacion: Descargamos las dependencias para acelerar futuras construcciones
RUN mvn dependency:go-offline

# Copiamos el resto del código fuente del proyecto
COPY src ./src

# Compilamos y empaquetamos la aplicación, omitiendo las pruebas
RUN mvn clean package -DskipTests


# =========================================================================
# === Etapa 2: Ejecución (Final Stage) ===
# Partimos de una imagen base de Java 21 muy ligera (solo JRE, no el JDK completo).
# =========================================================================
FROM eclipse-temurin:21-jre-alpine

# Establecemos el directorio de trabajo
WORKDIR /app

# Copiamos únicamente el archivo .jar que se generó en la etapa anterior.
COPY --from=build /app/target/*.jar app.jar

# Exponemos el puerto 8080
EXPOSE 8080

# Comando para ejecutar la aplicación cuando el contenedor se inicie.
ENTRYPOINT ["java", "-jar", "app.jar"]