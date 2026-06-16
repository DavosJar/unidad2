package com.gruposiete.hospital.infrastructure;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// peers debe incluir idPropio para que inicializar() calcule correctamente
// coordinadorActual, siguienteEnAnillo y tieneToken

@Component
public class EstadoCluster {

    public enum EstadoNodo {
        NORMAL, EN_ELECCION, COORDINADOR
    }

    private int idPropio;
    private final Map<Integer, String> peers = new ConcurrentHashMap<>();
    private final Map<Integer, String> todosLosPeers = new ConcurrentHashMap<>();
    private volatile int coordinadorActual;
    private volatile EstadoNodo estado = EstadoNodo.NORMAL;
    private volatile int siguienteEnAnillo = -1;
    private volatile boolean tieneToken = false;
    private volatile Long offsetReloj = null;
    private volatile Integer nodoCongeladoReportante = null;
    private volatile boolean listenerListo = false;
    private volatile boolean inicializado = false;

    // No requiere synchronized: se ejecuta una sola vez antes de iniciar los hilos del cluster
    public void inicializar(int idPropio, Map<Integer, String> peers) {
        this.idPropio = idPropio;
        this.peers.clear();
        this.peers.putAll(peers);
        this.todosLosPeers.clear();
        this.todosLosPeers.putAll(peers);
        this.coordinadorActual = peers.keySet().stream().mapToInt(Integer::intValue).max().orElse(idPropio);
        this.estado = (idPropio == coordinadorActual) ? EstadoNodo.COORDINADOR : EstadoNodo.NORMAL;
        List<Integer> ordenados = new ArrayList<>(peers.keySet());
        Collections.sort(ordenados);
        for (int i = 0; i < ordenados.size(); i++) {
            if (ordenados.get(i) == idPropio) {
                this.siguienteEnAnillo = ordenados.get((i + 1) % ordenados.size());
                break;
            }
        }
        this.tieneToken = (idPropio == ordenados.get(0));
        this.nodoCongeladoReportante = null;
        this.inicializado = true;
    }

    public int getIdPropio() { return idPropio; }
    public Map<Integer, String> getPeers() { return Collections.unmodifiableMap(peers); }
    public int getCoordinadorActual() { return coordinadorActual; }
    public EstadoNodo getEstado() { return estado; }
    public int getSiguienteEnAnillo() { return siguienteEnAnillo; }
    public Long getOffsetReloj() { return offsetReloj; }

    public synchronized void marcarCoordinador(int id) {
        this.coordinadorActual = id;
        this.estado = (id == idPropio) ? EstadoNodo.COORDINADOR : EstadoNodo.NORMAL;
    }

    public synchronized void setEstado(EstadoNodo estado) { this.estado = estado; }

    public synchronized void configurarAnillo(List<Integer> orden) {
        if (orden == null || orden.isEmpty()) return;
        for (int i = 0; i < orden.size(); i++) {
            if (orden.get(i) == idPropio) {
                this.siguienteEnAnillo = orden.get((i + 1) % orden.size());
                break;
            }
        }
    }

    public synchronized boolean tieneToken() { return tieneToken; }

    public synchronized void darToken() {
        this.tieneToken = true;
        this.nodoCongeladoReportante = null;
    }

    public synchronized void quitarToken() { this.tieneToken = false; }

    public synchronized void congelarToken() { this.tieneToken = false; }

    public synchronized void ajustarOffsetReloj(long offset) { this.offsetReloj = offset; }

    public synchronized List<Integer> nodosConIdMayor(int id) {
        List<Integer> mayores = new ArrayList<>();
        for (Integer peerId : peers.keySet()) {
            if (peerId > id) {
                mayores.add(peerId);
            }
        }
        Collections.sort(mayores);
        return mayores;
    }

    public synchronized void agregarNodo(int id) {
        String host = todosLosPeers.get(id);
        if (host != null && !peers.containsKey(id)) {
            peers.put(id, host);
        }
    }

    public synchronized void removerNodo(int id) {
        peers.remove(id);
        if (siguienteEnAnillo == id) {
            List<Integer> vivos = new ArrayList<>(peers.keySet());
            Collections.sort(vivos);
            for (int i = 0; i < vivos.size(); i++) {
                if (vivos.get(i) == idPropio) {
                    this.siguienteEnAnillo = vivos.get((i + 1) % vivos.size());
                    break;
                }
            }
        }
    }

    public synchronized boolean estaVivo(int id) { return peers.containsKey(id); }

    public synchronized void setNodoCongeladoReportante(Integer id) { this.nodoCongeladoReportante = id; }
    public synchronized Integer getNodoCongeladoReportante() { return nodoCongeladoReportante; }
    public synchronized boolean estaCongelado() { return nodoCongeladoReportante != null; }
    public synchronized void limpiarNodoCongeladoReportante() {
        this.nodoCongeladoReportante = null;
    }
    public synchronized void setSiguienteEnAnillo(int id) { this.siguienteEnAnillo = id; }

    public void marcarListenerListo() { this.listenerListo = true; }
    public boolean isListenerListo() { return listenerListo; }

    public boolean estaInicializado() { return inicializado; }
}
