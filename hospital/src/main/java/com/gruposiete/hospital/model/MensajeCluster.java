package com.gruposiete.hospital.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

public class MensajeCluster {

    public enum TipoMensaje {
        HEARTBEAT, HEARTBEAT_OK, ELECTION, OK, COORDINATOR, RING_UPDATE,
        TOKEN, TOKEN_LOST, TOKEN_RETRY, TOKEN_RESEND,
        TIME_REQUEST, TIME_RESPONSE
    }

    private TipoMensaje tipo;
    private int origen;
    private int destino;
    private String payload;

    public MensajeCluster(TipoMensaje tipo, int origen, int destino, String payload) {
        this.tipo = tipo;
        this.origen = origen;
        this.destino = destino;
        this.payload = (payload != null) ? payload : "";
    }

    public TipoMensaje getTipo() { return tipo; }
    public int getOrigen() { return origen; }
    public int getDestino() { return destino; }
    public String getPayload() { return payload; }

    public String aTextoPlano() {
        String dest = (destino == -1) ? "*" : String.valueOf(destino);
        return tipo.name() + "|" + origen + "|" + dest + "|" + payload + "\n";
    }

    public static MensajeCluster parsear(String linea) {
        if (linea == null || linea.isBlank()) {
            throw new IllegalArgumentException("Línea vacía o nula");
        }
        String limpia = linea.strip();
        String[] partes = limpia.split("\\|", -1);
        if (partes.length < 4) {
            throw new IllegalArgumentException("Formato inválido: " + limpia);
        }
        TipoMensaje tipo;
        try {
            tipo = TipoMensaje.valueOf(partes[0]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de mensaje desconocido: " + partes[0]);
        }
        int origen = Integer.parseInt(partes[1]);
        int destino;
        if (partes[2].equals("*")) {
            destino = -1;
        } else {
            destino = Integer.parseInt(partes[2]);
        }
        String payload = (partes.length >= 4) ? partes[3] : "";
        return new MensajeCluster(tipo, origen, destino, payload);
    }

    public static RespuestaEnvio enviar(MensajeCluster msg, String host, int port, int timeoutMs, int reintentos) {
        int intentos = 0;
        while (intentos <= reintentos) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), timeoutMs);
                socket.setSoTimeout(timeoutMs);
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out.print(msg.aTextoPlano());
                out.flush();
                String respuesta = in.readLine();
                if (respuesta != null) {
                    MensajeCluster respuestaMsg = parsear(respuesta);
                    return new RespuestaEnvio(true, respuestaMsg, null);
                }
                return new RespuestaEnvio(true, null, null);
            } catch (IOException e) {
                intentos++;
                if (intentos > reintentos) {
                    return new RespuestaEnvio(false, null, e);
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return new RespuestaEnvio(false, null, ie);
                }
            }
        }
        return new RespuestaEnvio(false, null, new IOException("No se pudo enviar el mensaje después de " + (reintentos + 1) + " intentos"));
    }

    public static void enviarSinRespuesta(MensajeCluster msg, String host, int port, int timeoutMs) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            out.print(msg.aTextoPlano());
            out.flush();
        }
    }

    public static class RespuestaEnvio {
        private final boolean exito;
        private final MensajeCluster respuesta;
        private final Exception error;

        public RespuestaEnvio(boolean exito, MensajeCluster respuesta, Exception error) {
            this.exito = exito;
            this.respuesta = respuesta;
            this.error = error;
        }

        public boolean fueExitoso() { return exito; }
        public MensajeCluster getRespuesta() { return respuesta; }
        public Exception getError() { return error; }
    }
}
