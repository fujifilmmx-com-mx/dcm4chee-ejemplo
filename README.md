# dcm4chee-ejemplo
proporciona la funcionalidad de C-FIND, C-MOVE y C-STORE

# Descripción
Este proyecto es una aplicación Java, basada en Spring Boot, que expone servicios web REST para la gestión de estudios médicos en formato DICOM. Ofrece endpoints para operaciones como búsqueda (C-FIND), recuperación (C-MOVE) y almacenamiento (C-STORE) de imágenes y estudios médicos.

# Arquitectura
Spring Boot como framework principal.
Controladores REST para la exposición de servicios.
Procesamiento de solicitudes DICOM mediante clases utilitarias y modelos de respuesta.
Modelo sencillo y modular, ideal para integraciones o pruebas.
# Mermaid
flowchart TD
    Client[Cliente HTTP]
    DicomController["DicomController (REST API)"]
    DicomProcessor["DicomProcessor"]
    ResponseDicomProcessor["ResponseDicomProcessor"]
    ApiResponse["ApiResponse"]
    DataSource[(DICOM Data Source)]

    Client -- "/api/dicom/{numacc}" --> DicomController
    DicomController -- procesa solicitud --> DicomProcessor
    DicomProcessor -- resultado --> ResponseDicomProcessor
    DicomController -- respuesta --> ApiResponse
    ApiResponse -- resultado HTTP --> Client
    DicomProcessor -- consulta --> DataSource
# Instalación
Clona el repositorio:
# bash
git clone https://github.com/fujifilmmx-com-mx/dcm4chee-ejemplo.git
Entra al directorio:
bash
cd dcm4chee-ejemplo
#Compila el proyecto:
bash
mvn clean package
#Ejecuta la aplicación:
bash
mvn spring-boot:run
Uso
Endpoints REST
GET /api/hello

# Prueba de la API.
Respuesta: "Hello, World!"
GET /api/dicom/{numacc}

Procesa el número de acceso del estudio DICOM.
Parámetro: numacc = número de acceso al estudio.
Respuesta ejemplo:
JSON
{
  "status": 200,
  "message": "Success",
  "data": "/ruta/a/los/dicom"
}
# Estructura principal
src/main/java/com/test/dicom_test/Controller/DicomController.java: Define los endpoints REST.
src/main/java/com/test/dicom_test/Controller/ApiResponse.java: Modelo de respuesta genérico.
src/main/java/com/test/dicom_test/Controller/ResponseDicomProcessor.java: Modelo de respuesta para el procesamiento DICOM.
src/main/java/com/test/dicom_test/DemoApplication.java: Punto de entrada de la aplicación.
Dependencias
Spring Boot
JUnit para pruebas unitarias.
Ejemplo de petición
bash
curl http://localhost:8080/api/hello
curl http://localhost:8080/api/dicom/123456
Mejoras recomendadas

# Licencia
Este proyecto es propiedad de FUJIFILM México. Uso interno o educativo.
