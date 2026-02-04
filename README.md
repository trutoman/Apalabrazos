# Apalabrazos

Proyecto JavaFX para gestión de partidas, preguntas y lobby.

## Requisitos

- JDK 21
- Maven 3.8+ (recomendado 3.9+)
- Configuración de Maven Toolchains

## Maven Toolchains (obligatorio)

Este proyecto usa toolchains para asegurar el uso de Java 21 en todos los entornos. Crea o actualiza el archivo ~/.m2/toolchains.xml con un JDK 21 instalado en tu máquina.

Ejemplo:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>21</version>
      <vendor>any</vendor>
    </provides>
    <configuration>
      <jdkHome>/ruta/al/jdk-21</jdkHome>
    </configuration>
  </toolchain>
</toolchains>
```

## Inicio rápido

- Compilar: mvn clean package
- Ejecutar JavaFX: mvn javafx:run

## Estructura del proyecto

- Código Java: src/main/java
- Recursos JavaFX: src/main/resources
- Vistas FXML: src/main/resources/UE_Proyecto_Ingenieria/Apalabrazos/view

## Solución de problemas

- Si Maven no encuentra el toolchain, revisa la ruta a JDK 21 en ~/.m2/toolchains.xml.
- Si se usa un JDK diferente, verifica que el toolchain esté configurado y que Java 21 esté instalado.
