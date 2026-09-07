package com.escuelaing.edu.app;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class HttpServer {

    private static final int DEFAULT_PORT = 35000;
    private static final String WEB_ROOT = "public";

    public static void main(String[] args) {
        int port = getPort();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Servidor HTTP listo en el puerto: " + port);

            while (true) {
                try (Socket clientSocket = serverSocket.accept();
                     InputStream inStream = clientSocket.getInputStream();
                     OutputStream outStream = new BufferedOutputStream(clientSocket.getOutputStream());
                     BufferedReader in = new BufferedReader(new InputStreamReader(inStream, StandardCharsets.UTF_8))) {

                    String requestLine = in.readLine();
                    if (requestLine == null || requestLine.isEmpty()) {
                        continue;
                    }

                    // Consumir el resto de los encabezados HTTP
                    String headerLine;
                    while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                        // Consumir headers
                    }

                    handleRequest(requestLine, outStream);

                } catch (IOException e) {
                    System.err.println("Error procesando conexión cliente: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("No se pudo iniciar el servidor en el puerto " + port + ": " + e.getMessage());
        }
    }

    private static void handleRequest(String requestLine, OutputStream out) throws IOException {
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) {
            sendErrorResponse(out, "400 Bad Request", "Petición malformada.");
            return;
        }

        String method = parts[0];
        String rawPath = parts[1];

        if (!method.equals("GET")) {
            sendErrorResponse(out, "405 Method Not Allowed", "Método no soportado en esta versión.");
            return;
        }

        URI uri;
        try {
            uri = new URI(rawPath);
        } catch (URISyntaxException e) {
            sendErrorResponse(out, "400 Bad Request", "URI inválida.");
            return;
        }

        String path = uri.getPath();
        Map<String, String> queryParams = parseQueryParams(uri.getQuery());

        // --- ENRUTAMIENTO EXPLÍCITO / HARDCODED DE SERVICIOS ---
        if (path.equals("/greeting")) {
            handleGreetingService(out, queryParams);
            return;
        } else if (path.equals("/square")) {
            handleSquareService(out, queryParams);
            return;
        } else if (path.equals("/servertime")) {
            handleServerTimeService(out);
            return;
        } else if (path.equals("/health")) {
            handleHealthService(out);
            return;
        }

        // --- MANEJO DE RECURSOS ESTÁTICOS ---
        if (path == null || path.equals("/")) {
            path = "/index.html";
        }

        if (path.contains("..")) {
            sendErrorResponse(out, "403 Forbidden", "Acceso denegado: intento de path traversal.");
            return;
        }

        String resourcePath = WEB_ROOT + path;
        byte[] fileData = readResourceAsBytes(resourcePath);

        if (fileData == null) {
            sendErrorResponse(out, "404 Not Found", "Recurso no encontrado: " + path);
            return;
        }

        String contentType = getContentType(path);
        sendOkResponse(out, contentType, fileData);
    }

    // --- LÓGICA DE SERVICIOS DINÁMICOS ---

    private static void handleGreetingService(OutputStream out, Map<String, String> queryParams) throws IOException {
        String name = queryParams.get("name");
        if (name == null || name.trim().isEmpty()) {
            sendErrorResponse(out, "400 Bad Request", "El parámetro 'name' es obligatorio.");
            return;
        }
        String safeName = escapeJson(name);
        String jsonResponse = "{\"greeting\":\"Hello, " + safeName + "!\"}";
        sendJsonResponse(out, "200 OK", jsonResponse);
    }

    private static void handleSquareService(OutputStream out, Map<String, String> queryParams) throws IOException {
        String valueStr = queryParams.get("value");
        if (valueStr == null || valueStr.trim().isEmpty()) {
            sendErrorResponse(out, "400 Bad Request", "El parámetro 'value' es obligatorio.");
            return;
        }

        try {
            double value = Double.parseDouble(valueStr);
            double square = value * value;
            String jsonResponse = "{\"value\":" + value + ",\"square\":" + square + "}";
            sendJsonResponse(out, "200 OK", jsonResponse);
        } catch (NumberFormatException e) {
            sendErrorResponse(out, "400 Bad Request", "El parámetro 'value' debe ser un número válido.");
        }
    }

    private static void handleServerTimeService(OutputStream out) throws IOException {
        String jsonResponse = "{\"time\":\"" + Instant.now().toString() + "\"}";
        sendJsonResponse(out, "200 OK", jsonResponse);
    }

    private static void handleHealthService(OutputStream out) throws IOException {
        String jsonResponse = "{\"status\":\"UP\"}";
        sendJsonResponse(out, "200 OK", jsonResponse);
    }

    // --- MÉTODOS DE SOPORTE Y RESPUESTAS ---

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                params.put(entry[0], entry[1]);
            } else if (entry.length == 1) {
                params.put(entry[0], "");
            }
        }
        return params;
    }

    private static String escapeJson(String input) {
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static void sendJsonResponse(OutputStream out, String status, String jsonBody) throws IOException {
        byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + status + "\r\n"
                + "Content-Type: application/json; charset=UTF-8\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Connection: close\r\n\r\n";

        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(bodyBytes);
        out.flush();
    }

    private static byte[] readResourceAsBytes(String resourcePath) {
        String cleanPath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        InputStream stream = HttpServer.class.getClassLoader().getResourceAsStream(cleanPath);
        if (stream == null) {
            stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(cleanPath);
        }
        if (stream == null) {
            return null;
        }

        try (InputStream is = stream; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] data = new byte[4096];
            int nRead;
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private static String getContentType(String path) {
        if (path.endsWith(".html") || path.endsWith(".htm")) return "text/html; charset=UTF-8";
        if (path.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (path.endsWith(".css")) return "text/css; charset=UTF-8";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private static void sendOkResponse(OutputStream out, String contentType, byte[] body) throws IOException {
        String headers = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";

        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    private static void sendErrorResponse(OutputStream out, String status, String message) throws IOException {
        String htmlBody = "<!doctype html><html><head><meta charset=\"UTF-8\"><title>" + status + "</title></head>"
                + "<body><h1>" + status + "</h1><p>" + message + "</p></body></html>";
        byte[] bodyBytes = htmlBody.getBytes(StandardCharsets.UTF_8);

        String headers = "HTTP/1.1 " + status + "\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Connection: close\r\n\r\n";

        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(bodyBytes);
        out.flush();
    }

    private static int getPort() {
        if (System.getenv("PORT") != null) {
            return Integer.parseInt(System.getenv("PORT"));
        }
        return DEFAULT_PORT;
    }
}