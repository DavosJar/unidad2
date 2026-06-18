package com.gruposiete.hospital.infrastructure;

import com.gruposiete.hospital.model.MensajeCluster;
import com.gruposiete.hospital.service.ServicioAnilloToken;
import com.gruposiete.hospital.service.ServicioEleccionBully;
import com.gruposiete.hospital.service.ServicioCristian;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@Order(1)
public class ClusterSocketListener implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ClusterSocketListener.class);

    @Value("${cluster.port:9000}")
    private int port;

    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private ExecutorService procesadorPool;
    private volatile boolean running = false;

    private final EstadoCluster estadoCluster;
    private final ServicioEleccionBully servicioBully;
    private final ServicioAnilloToken servicioAnillo;
    private final ServicioCristian servicioTiempo;

    public ClusterSocketListener(EstadoCluster estadoCluster,
                                  ServicioEleccionBully servicioBully,
                                  ServicioAnilloToken servicioAnillo,
                                  ServicioCristian servicioTiempo) {
        this.estadoCluster = estadoCluster;
        this.servicioBully = servicioBully;
        this.servicioAnillo = servicioAnillo;
        this.servicioTiempo = servicioTiempo;
    }

    @Override
    public void run(String... args) throws Exception {
        executorService = Executors.newSingleThreadExecutor();
        procesadorPool = Executors.newFixedThreadPool(4);
        serverSocket = new ServerSocket(port);
        serverSocket.setSoTimeout(1000);
        running = true;
        log.info("ClusterSocketListener escuchando en puerto {}", port);
        executorService.submit(this::aceptarConexiones);
    }

    private void aceptarConexiones() {
        estadoCluster.marcarListenerListo();
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                procesadorPool.submit(() -> procesarMensaje(socket));
            } catch (java.net.SocketTimeoutException e) {
                // Timeout esperado, permite verificar running flag
            } catch (IOException e) {
                if (running) {
                    log.error("Error aceptando conexion: {}", e.getMessage());
                }
            }
        }
    }

    private void procesarMensaje(Socket socket) {
        try (socket; BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String linea = in.readLine();
            if (linea == null) return;
            MensajeCluster msg = MensajeCluster.parsear(linea);
            log.debug("Nodo {} recibe {} de {}", estadoCluster.getIdPropio(), msg.getTipo(), msg.getOrigen());
            estadoCluster.agregarNodo(msg.getOrigen());
            estadoCluster.notificarMensajeRecibido();
            Optional<MensajeCluster> respuesta = Optional.empty();
            switch (msg.getTipo()) {
                case HEARTBEAT, HEARTBEAT_OK, ELECTION, OK, COORDINATOR, RING_UPDATE, TOKEN_LOST:
                    servicioBully.procesarMensaje(msg);
                    break;
                case TOKEN:
                    respuesta = servicioAnillo.procesarToken(msg);
                    break;
                case TOKEN_RETRY, TOKEN_RESEND:
                    servicioAnillo.procesarMensaje(msg);
                    break;
                case TIME_REQUEST:
                    respuesta = servicioTiempo.procesarTimeRequest(msg);
                    break;
                case TIME_RESPONSE:
                    // Defensivo: no deberia llegar con flujo sincrono actual (Cristian usa enviar())
                    servicioTiempo.procesarTimeResponse(msg);
                    break;
                default:
                    log.warn("Tipo de mensaje sin manejador: {}", msg.getTipo());
                    break;
            }
            if (respuesta.isPresent()) {
                try (PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {
                    out.print(respuesta.get().aTextoPlano());
                    out.flush();
                }
            }
        } catch (IOException e) {
            log.error("Error en mensaje: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void detener() {
        running = false;
        if (procesadorPool != null) {
            procesadorPool.shutdown();
            try {
                if (!procesadorPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    procesadorPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                procesadorPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                log.error("Error cerrando server socket: {}", e.getMessage());
            }
        }
        log.info("ClusterSocketListener detenido");
    }
}
