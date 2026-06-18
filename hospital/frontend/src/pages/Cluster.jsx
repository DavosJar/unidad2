import { useState, useEffect, useRef } from 'react';
import { Server, Crown, Network, Wifi, WifiOff, Coins, RefreshCw, ArrowRight } from 'lucide-react';
import { useClusterSocket } from '../hooks/useClusterSocket';
import './Cluster.css';

/* ══════════════════════════════════════════
   Constantes — 4 nodos fijos en anillo
   ══════════════════════════════════════════ */
const FIXED_NODES = [1, 2, 3, 4]; // siempre se muestran estos 4

/* ─── Utilidad: posiciones en círculo ─── */
function nodePositions(count, cx, cy, r) {
  return Array.from({ length: count }, (_, i) => {
    const angle = (2 * Math.PI * i) / count - Math.PI / 2;
    return { x: cx + r * Math.cos(angle), y: cy + r * Math.sin(angle) };
  });
}

/* ══════════════════════════════════════════
   SVG del Anillo con token animado
   ══════════════════════════════════════════ */
function RingVisual({ coordinador, tokenEn, aliveIds, yo }) {
  const N = FIXED_NODES.length;  // 4
  const CX = 200, CY = 200, R = 130, NR = 30;
  const positions = nodePositions(N, CX, CY, R);

  // índice del nodo que tiene el token para la animación
  const tokenIdx = FIXED_NODES.indexOf(tokenEn);

  return (
    <div className="cluster-ring-visual">
      <svg width={CX * 2} height={CY * 2} viewBox={`0 0 ${CX * 2} ${CY * 2}`} overflow="visible">
        <defs>
          {/* Gradiente dorado para coordinador */}
          <radialGradient id="glow-coord" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="#eab308" stopOpacity="0.35" />
            <stop offset="100%" stopColor="#eab308" stopOpacity="0" />
          </radialGradient>
          {/* Gradiente violeta para token */}
          <radialGradient id="glow-token" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="#7c3aed" stopOpacity="0.45" />
            <stop offset="100%" stopColor="#7c3aed" stopOpacity="0" />
          </radialGradient>
          {/* Punta de flecha */}
          <marker id="arrow" markerWidth="7" markerHeight="7" refX="6" refY="3.5" orient="auto">
            <path d="M0,0 L0,7 L7,3.5 z" fill="#1e3a5f" />
          </marker>
          {/* Filtro glow para el token */}
          <filter id="token-glow" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation="4" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        {/* Track circular decorativo */}
        <circle cx={CX} cy={CY} r={R} fill="none" stroke="#1e2a3a" strokeWidth="1.5"
          strokeDasharray="5 7" />

        {/* Aristas dirigidas del anillo */}
        {FIXED_NODES.map((id, i) => {
          const from = positions[i];
          const to = positions[(i + 1) % N];
          const dx = to.x - from.x, dy = to.y - from.y;
          const len = Math.sqrt(dx * dx + dy * dy);
          const ux = dx / len, uy = dy / len;
          const x1 = from.x + ux * NR, y1 = from.y + uy * NR;
          const x2 = to.x - ux * (NR + 8), y2 = to.y - uy * (NR + 8);
          const isAlive = aliveIds.includes(id) && aliveIds.includes(FIXED_NODES[(i + 1) % N]);
          return (
            <line key={`e-${i}`}
              x1={x1} y1={y1} x2={x2} y2={y2}
              stroke={isAlive ? '#1e3a5f' : '#2d1515'}
              strokeWidth={isAlive ? 1.5 : 1}
              strokeDasharray={isAlive ? 'none' : '4 4'}
              markerEnd="url(#arrow)" />
          );
        })}

        {/* Punto del token circulando en el anillo */}
        {tokenIdx !== -1 && (
          <circle
            cx={positions[tokenIdx].x}
            cy={positions[tokenIdx].y}
            r={NR + 12}
            fill="url(#glow-token)"
            className="cluster-token-ring-pulse"
          />
        )}

        {/* Nodos */}
        {FIXED_NODES.map((nodeId, i) => {
          const pos = positions[i];
          const isCoord = nodeId === coordinador;
          const hasToken = nodeId === tokenEn;
          const isAlive = aliveIds.includes(nodeId);
          const isSelf = nodeId === yo;

          const stroke = isCoord ? '#eab308' : isSelf ? '#818cf8' : isAlive ? '#1e3a5f' : '#3b1515';
          const strokeW = isCoord || isSelf ? 2.5 : 1.5;
          const fill = isCoord ? '#1a1808' : isSelf ? '#0f0e1a' : isAlive ? '#111827' : '#150d0d';

          return (
            <g key={`n-${nodeId}`}>
              {/* Halo coordinador */}
              {isCoord && (
                <circle cx={pos.x} cy={pos.y} r={NR + 14} fill="url(#glow-coord)" />
              )}

              <circle cx={pos.x} cy={pos.y} r={NR}
                fill={fill} stroke={stroke} strokeWidth={strokeW} />

              {/* Símbolo del nodo */}
              <text x={pos.x} y={pos.y - 6} textAnchor="middle" dominantBaseline="middle"
                fontSize="13" fontWeight="800" fontFamily="Inter,sans-serif"
                fill={isCoord ? '#eab308' : isSelf ? '#818cf8' : isAlive ? '#94a3b8' : '#4a2020'}>
                {isCoord ? '♛' : '≡'}
              </text>
              <text x={pos.x} y={pos.y + 9} textAnchor="middle" dominantBaseline="middle"
                fontSize="9" fontWeight="700" fontFamily="Inter,sans-serif"
                fill={isCoord ? '#fbbf24' : isAlive ? '#64748b' : '#4a2020'}>
                N{nodeId}
              </text>

              {/* Badge de token encima del nodo */}
              {hasToken && (
                <g filter="url(#token-glow)">
                  <circle cx={pos.x + NR - 2} cy={pos.y - NR + 2} r={8}
                    fill="#7c3aed" stroke="#0d1117" strokeWidth="1.5" />
                  <text x={pos.x + NR - 2} y={pos.y - NR + 2}
                    textAnchor="middle" dominantBaseline="middle"
                    fontSize="8" fill="#fff" fontWeight="700">◆</text>
                </g>
              )}

              {/* Indicador offline */}
              {!isAlive && (
                <g>
                  <line x1={pos.x - 8} y1={pos.y - 8} x2={pos.x + 8} y2={pos.y + 8}
                    stroke="#ef4444" strokeWidth="2" />
                  <line x1={pos.x + 8} y1={pos.y - 8} x2={pos.x - 8} y2={pos.y + 8}
                    stroke="#ef4444" strokeWidth="2" />
                </g>
              )}
            </g>
          );
        })}
      </svg>
    </div>
  );
}

/* ══════════════════════════════════════════
   Tarjeta de un nodo
   ══════════════════════════════════════════ */
function NodeCard({ nodeId, coordinador, tokenEn, aliveIds, yo, estado }) {
  const isCoord = nodeId === coordinador;
  const hasToken = nodeId === tokenEn;
  const isAlive = aliveIds.includes(nodeId);
  const isSelf = nodeId === yo;
  const isEleccion = isSelf && estado === 'EN_ELECCION';

  let cardClass = 'cluster-node-card';
  if (isCoord)      cardClass += ' cluster-node-card--coordinator';
  else if (!isAlive) cardClass += ' cluster-node-card--offline';
  else if (isEleccion) cardClass += ' cluster-node-card--election';

  return (
    <div className={cardClass}>
      <div className="cluster-node-icon">
        {isCoord ? <Crown size={24} /> : <Server size={22} />}
      </div>

      <div className="cluster-node-id">P{nodeId}</div>

      <span className={`cluster-node-badge ${
        isCoord        ? 'cluster-node-badge--coordinador' :
        !isAlive       ? 'cluster-node-badge--offline' :
        isEleccion     ? 'cluster-node-badge--election' :
                         'cluster-node-badge--proceso'
      }`}>
        {isCoord    ? '[COORD] COORDINADOR' :
         !isAlive   ? 'DESCONECTADO' :
         isEleccion ? 'EN ELECCIÓN' :
                      '(*) PROCESO'}
      </span>

      <div className="cluster-node-status">
        <div className={`cluster-node-status__dot ${isAlive ? 'cluster-node-status__dot--online' : 'cluster-node-status__dot--offline'}`} />
        <span>{isAlive ? 'En línea' : 'Fuera de línea'}</span>
      </div>

      {coordinador !== -1 && (
        <div className="cluster-node-coord">
          Coord. conocido: P{coordinador}
        </div>
      )}

      {isSelf && (
        <div className="cluster-node-coord" style={{ color: '#818cf8' }}>
          ← Este nodo
        </div>
      )}

      {hasToken && (
        <span className="cluster-node-token">
          <Coins size={11} />
          ◆ ESTE NODO TIENE EL TOKEN
        </span>
      )}
    </div>
  );
}

/* ══════════════════════════════════════════
   Historial de movimientos del token
   ══════════════════════════════════════════ */
function TokenHistory({ history }) {
  if (!history || history.length === 0) return null;
  return (
    <div className="cluster-token-history">
      <div className="cluster-token-history__title">
        <Coins size={13} /> Movimientos del token (tiempo real)
      </div>
      <div className="cluster-token-history__list">
        {history.map((h, i) => (
          <div key={i} className="cluster-token-history__item" style={{ opacity: 1 - i * 0.08 }}>
            <span className="cluster-token-history__node">P{h.from}</span>
            <ArrowRight size={11} style={{ color: '#a78bfa' }} />
            <span className="cluster-token-history__node">P{h.to}</span>
            <span className="cluster-token-history__time">
              {new Date(h.ts).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

/* ══════════════════════════════════════════
   Página principal Cluster
   ══════════════════════════════════════════ */
export default function Cluster() {
  const { clusterState, connected, error, tokenHistory, reconnect } = useClusterSocket();
  const [showRing, setShowRing] = useState(true);

  const aliveIds = clusterState?.nodeIds ?? [];
  const allIds   = clusterState?.allNodeIds ?? FIXED_NODES;
  const coordId  = clusterState?.coordinador ?? -1;
  const tokenEn  = clusterState?.tokenEn ?? -1;
  const yo       = clusterState?.yo ?? -1;
  const estado   = clusterState?.estado ?? 'NORMAL';

  // Mostrar siempre los 4 nodos fijos; marcar los que no están en aliveIds como offline
  const displayNodes = FIXED_NODES;

  return (
    <div className="page-enter">
      <div className="page-header">
        <div>
          <h1 className="page-title">Cluster de Nodos</h1>
          <p className="page-subtitle">
            Topología en anillo · Bully election · Token ring mutex — tiempo real via WebSocket
          </p>
        </div>
      </div>

      <div className="cluster-panel">
        {/* Header sección */}
        <div className="cluster-section-header">
          <div className="cluster-section-icon">
            <Network size={18} />
          </div>
          <span className="cluster-section-title">Cluster de Procesos</span>
        </div>

        {/* Toolbar */}
        <div className="cluster-toolbar">
          <div className="cluster-toolbar__info">
            <div className={`cluster-toolbar__dot ${
              !connected ? 'cluster-toolbar__dot--error' :
              !clusterState ? 'cluster-toolbar__dot--loading' : ''
            }`} />
            {!connected
              ? (error || 'Desconectado — reconectando…')
              : !clusterState
              ? 'Esperando datos del cluster…'
              : `WebSocket activo · ${new Date(clusterState.ts).toLocaleTimeString('es-MX')}`}
          </div>

          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <button className="cluster-refresh-btn" onClick={() => setShowRing(v => !v)}>
              <Network size={13} />
              {showRing ? 'Ocultar anillo' : 'Ver anillo'}
            </button>
            {!connected && (
              <button className="cluster-refresh-btn" onClick={reconnect}>
                <RefreshCw size={13} />
                Reconectar
              </button>
            )}
          </div>
        </div>

        {/* Estado vacío / sin conexión */}
        {!clusterState && !connected && (
          <div className="cluster-empty">
            <WifiOff size={40} className="cluster-empty__icon" />
            <div className="cluster-empty__title">Sin conexión al backend</div>
            <div className="cluster-empty__msg">
              {error || 'El WebSocket no está disponible. Verifica que el backend esté corriendo.'}
            </div>
          </div>
        )}

        {!clusterState && connected && (
          <div className="cluster-empty">
            <Network size={40} className="cluster-empty__icon" />
            <div className="cluster-empty__title">Conectado — esperando datos…</div>
            <div className="cluster-empty__msg">El cluster aún no ha enviado su primer estado.</div>
          </div>
        )}

        {clusterState && (
          <>
            {/* Anillo SVG */}
            {showRing && (
              <>
                <div className="cluster-ring-indicator">
                  <div className="cluster-ring-line" />
                  Topología en anillo — {FIXED_NODES.length} nodos
                  <div className="cluster-ring-line cluster-ring-line--right" />
                </div>
                <RingVisual
                  coordinador={coordId}
                  tokenEn={tokenEn}
                  aliveIds={aliveIds}
                  yo={yo}
                />
              </>
            )}

            {/* Tarjetas de nodos */}
            <div className="cluster-nodes-grid">
              {displayNodes.map(nodeId => (
                <NodeCard
                  key={nodeId}
                  nodeId={nodeId}
                  coordinador={coordId}
                  tokenEn={tokenEn}
                  aliveIds={aliveIds}
                  yo={yo}
                  estado={estado}
                />
              ))}
            </div>

            {/* Historial de token */}
            <TokenHistory history={tokenHistory} />

            {/* Leyenda */}
            <div className="cluster-legend">
              <div className="cluster-legend__item">
                <div className="cluster-legend__swatch" style={{ background: '#eab308' }} />
                Coordinador (bully)
              </div>
              <div className="cluster-legend__item">
                <div className="cluster-legend__swatch" style={{ background: '#7c3aed' }} />
                Token (mutex distribuido)
              </div>
              <div className="cluster-legend__item">
                <div className="cluster-legend__swatch" style={{ background: '#ef4444' }} />
                Nodo desconectado
              </div>
              {coordId !== -1 && (
                <div className="cluster-legend__item" style={{ marginLeft: 'auto' }}>
                  <Crown size={13} style={{ color: '#eab308' }} />
                  <span>Coordinador: <strong style={{ color: '#fbbf24' }}>P{coordId}</strong></span>
                </div>
              )}
              {tokenEn !== -1 && (
                <div className="cluster-legend__item">
                  <Coins size={13} style={{ color: '#a78bfa' }} />
                  <span>Token en: <strong style={{ color: '#a78bfa' }}>P{tokenEn}</strong></span>
                </div>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
