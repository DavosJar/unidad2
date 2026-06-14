import { useState, useMemo } from 'react';
import { useApi } from '../hooks/useApi';
import { logs as logsApi } from '../api/hospital';
import { formatTimestamp } from '../utils/formatDate';
import { SkeletonTable } from '../components/Skeleton';
import EmptyState from '../components/EmptyState';
import { Activity, Search, RefreshCw, X as CloseIcon } from 'lucide-react';
import './Logs.css';

export default function Logs() {
  const { data: allLogs, loading, refetch } = useApi(() => logsApi.list());
  const [filterNode, setFilterNode] = useState('');
  const [filterAction, setFilterAction] = useState('');

  const nodes = useMemo(() => {
    if (!allLogs) return [];
    return [...new Set(allLogs.map((l) => l.nodoId).filter(Boolean))].sort();
  }, [allLogs]);

  const filtered = useMemo(() => {
    if (!allLogs) return [];
    return allLogs.filter((l) => {
      if (filterNode && l.nodoId !== filterNode) return false;
      if (filterAction && !l.accion?.toLowerCase().includes(filterAction.toLowerCase())) return false;
      return true;
    });
  }, [allLogs, filterNode, filterAction]);

  return (
    <div className="page-enter">
      <div className="page-header">
        <div>
          <h1 className="page-title">Registro de Actividad</h1>
          <p className="page-subtitle">
            {allLogs ? `Mostrando ${filtered.length} de ${allLogs.length} eventos registrados` : 'Cargando datos...'}
          </p>
        </div>
        <button className="btn btn--ghost" onClick={refetch}>
          <RefreshCw size={18} />
          Actualizar
        </button>
      </div>

      <div className="filters stagger">
        <div className="filter-group">
          <Search size={18} className="filter-group__icon" />
          <input
            type="text"
            className="filter-input"
            placeholder="Buscar evento o acción..."
            value={filterAction}
            onChange={(e) => setFilterAction(e.target.value)}
          />
          {filterAction && (
            <button className="filter-group__clear" onClick={() => setFilterAction('')} aria-label="Limpiar búsqueda">
              <CloseIcon size={16} />
            </button>
          )}
        </div>
        <select className="filter-select" value={filterNode} onChange={(e) => setFilterNode(e.target.value)}>
          <option value="">Todos los nodos</option>
          {nodes.map((n) => <option key={n} value={n}>Nodo {n}</option>)}
        </select>
      </div>

      {loading ? (
        <SkeletonTable rows={6} />
      ) : filtered.length === 0 ? (
        <EmptyState
          icon={<Activity size={32} />}
          title={allLogs ? "Sin coincidencias" : "Bitácora vacía"}
          message={allLogs ? "No hay eventos que coincidan con los filtros aplicados." : "El sistema está a la espera de las primeras operaciones."}
        />
      ) : (
        <div className="logs-table-wrapper stagger">
          <table className="logs-table">
            <thead>
              <tr>
                <th>Nodo de Origen</th>
                <th>Acción Registrada</th>
                <th>Fecha y Hora</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((log, i) => (
                <tr key={log.id || i} className="logs-table__row">
                  <td>
                    <span className="logs-table__node">Nodo {log.nodoId}</span>
                  </td>
                  <td className="logs-table__action">{log.accion}</td>
                  <td className="logs-table__time">
                    {formatTimestamp(log.timestamp) || '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
