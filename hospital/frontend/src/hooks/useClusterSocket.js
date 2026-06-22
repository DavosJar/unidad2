import { useEffect, useRef, useState, useCallback } from 'react';
import { Client } from '@stomp/stompjs';

/**
 * Hook para conectarse al WebSocket STOMP del backend y
 * recibir actualizaciones del cluster en tiempo real.
 *
 * Devuelve:
 *  - clusterState : objeto con { nodeIds, allNodeIds, coordinador, tokenEn, tokenEnUso, yo, estado, siguienteEnAnillo, ts }
 *  - connected    : boolean
 *  - error        : string | null
 *  - tokenHistory : array de { from, to, ts } con los últimos movimientos del token
 */
export function useClusterSocket() {
  const [clusterState, setClusterState] = useState(null);
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState(null);
  const [tokenHistory, setTokenHistory] = useState([]);
  const clientRef = useRef(null);
  const prevTokenRef = useRef(null);

  const connect = useCallback(() => {
    if (clientRef.current) {
      try { clientRef.current.deactivate(); } catch (_) {}
    }

      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const brokerURL = `${protocol}//${window.location.host}/ws`;

      const client = new Client({
        brokerURL: brokerURL,
      reconnectDelay: 3000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,

      onConnect: () => {
        setConnected(true);
        setError(null);

        client.subscribe('/topic/cluster', (msg) => {
          try {
            const data = JSON.parse(msg.body);
            setClusterState(data);

            // Rastrear movimiento de token
            const nuevoToken = data.tokenEn;
            if (
              nuevoToken !== -1 &&
              prevTokenRef.current !== null &&
              prevTokenRef.current !== nuevoToken
            ) {
              setTokenHistory((prev) => [
                { from: prevTokenRef.current, to: nuevoToken, ts: data.ts },
                ...prev.slice(0, 9), // últimos 10 movimientos
              ]);
            }
            prevTokenRef.current = nuevoToken !== -1 ? nuevoToken : prevTokenRef.current;
          } catch (e) {
            console.warn('Error parseando mensaje cluster:', e);
          }
        });
      },

      onDisconnect: () => {
        setConnected(false);
        setClusterState(null);
      },

      onStompError: (frame) => {
        setError('Error STOMP: ' + (frame.headers?.message || 'desconocido'));
        setConnected(false);
        setClusterState(null);
      },

      onWebSocketError: (evt) => {
        setError('WebSocket no disponible — ¿el backend está corriendo?');
        setConnected(false);
        setClusterState(null);
      },
    });

    client.activate();
    clientRef.current = client;
  }, []);

  useEffect(() => {
    connect();
    return () => {
      if (clientRef.current) {
        clientRef.current.deactivate();
      }
    };
  }, [connect]);

  return { clusterState, connected, error, tokenHistory, reconnect: connect };
}
