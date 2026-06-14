import { useApi } from '../hooks/useApi';
import { donors as donorsApi, reservations as reservationsApi, logs as logsApi } from '../api/hospital';
import { formatTimestamp } from '../utils/formatDate';
import { SkeletonStatCard } from '../components/Skeleton';
import EmptyState from '../components/EmptyState';
import { Users, UserCheck, Calendar, Activity } from 'lucide-react';
import './Dashboard.css';

const organColors = {
  Corazón: 'var(--accent-primary)',
  Riñón: 'var(--info)',
  Hígado: 'var(--warning)',
  Pulmón: 'var(--success)',
  Páncreas: 'var(--accent-primary-hover)',
  Córnea: 'var(--text-secondary)',
};

function StatCard({ label, value, sub, icon: Icon, color }) {
  return (
    <div className="stat-card">
      <div className="stat-card__icon-wrapper" style={{ backgroundColor: `${color}15`, color }}>
        <Icon size={24} />
      </div>
      <div className="stat-card__info">
        <span className="stat-card__value">{value}</span>
        <span className="stat-card__label">{label}</span>
      </div>
      {sub && <div className="stat-card__sub">{sub}</div>}
    </div>
  );
}

function MiniBar({ data, color = 'var(--accent-primary)' }) {
  if (!data || data.length === 0) return null;
  const max = Math.max(...data.map(d => d.value), 1);
  return (
    <div className="mini-bar">
      {data.map((d, i) => (
        <div key={i} className="mini-bar__item">
          <span className="mini-bar__label">{d.label}</span>
          <div className="mini-bar__track">
            <div
              className="mini-bar__fill"
              style={{
                width: `${(d.value / max) * 100}%`,
                background: d.color || color,
              }}
            />
          </div>
          <span className="mini-bar__value">{d.value}</span>
        </div>
      ))}
    </div>
  );
}

function ActivityFeed({ logs }) {
  if (!logs || logs.length === 0) return null;
  return (
    <div className="activity-feed">
      {logs.slice(0, 8).map((log, i) => (
        <div key={log.id || i} className="activity-feed__item">
          <div className="activity-feed__dot" />
          <div className="activity-feed__content">
            <p className="activity-feed__action">{log.accion}</p>
            <div className="activity-feed__meta">
              <span className="activity-feed__node">Nodo {log.nodoId}</span>
              <span className="activity-feed__time">{formatTimestamp(log.timestamp)}</span>
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

export default function Dashboard() {
  const { data: stats, loading: statsLoading } = useApi(() => donorsApi.stats());
  const { data: allDonors, loading: donorsLoading } = useApi(() => donorsApi.list());
  const { data: reservations, loading: reservationsLoading } = useApi(() => reservationsApi.list());
  const { data: allLogs, loading: logsLoading } = useApi(() => logsApi.list());

  const total = stats?.total ?? 0;
  const disponibles = stats?.disponibles ?? 0;
  const reservados = stats?.reservados ?? 0;

  const organData = allDonors
    ? Object.entries(
        allDonors.reduce((acc, d) => {
          if (d.organo) acc[d.organo] = (acc[d.organo] || 0) + 1;
          return acc;
        }, {})
      )
        .map(([label, value]) => ({ label, value, color: organColors[label] || 'var(--accent-primary)' }))
        .sort((a, b) => b.value - a.value)
    : [];

  const bloodData = allDonors
    ? Object.entries(
        allDonors.reduce((acc, d) => {
          if (d.tipoSangre) acc[d.tipoSangre] = (acc[d.tipoSangre] || 0) + 1;
          return acc;
        }, {})
      )
        .map(([label, value]) => ({ label, value }))
        .sort((a, b) => b.value - a.value)
    : [];

  return (
    <div className="page-enter">
      <div className="page-header">
        <div>
          <h1 className="page-title">Resumen General</h1>
          <p className="page-subtitle">Monitoreo en tiempo real del sistema de donación y trasplante.</p>
        </div>
      </div>

      <div className="stats-grid stagger">
        {statsLoading ? (
          <>
            <SkeletonStatCard />
            <SkeletonStatCard />
            <SkeletonStatCard />
            <SkeletonStatCard />
          </>
        ) : (
          <>
            <StatCard
              label="Total Donantes"
              value={total}
              icon={Users}
              color="var(--accent-primary)"
            />
            <StatCard
              label="Órganos Disponibles"
              value={disponibles}
              sub={total ? <span style={{color: 'var(--success)'}}>+{Math.round((disponibles / total) * 100)}% del total</span> : 'Sin datos'}
              icon={UserCheck}
              color="var(--success)"
            />
            <StatCard
              label="Órganos Reservados"
              value={reservados}
              sub={total ? <span style={{color: 'var(--warning)'}}>{Math.round((reservados / total) * 100)}% en proceso</span> : 'Sin reservas'}
              icon={Calendar}
              color="var(--warning)"
            />
            <StatCard
              label="Operaciones Recientes"
              value={reservations?.length ?? 0}
              icon={Activity}
              color="var(--info)"
            />
          </>
        )}
      </div>

      <div className="dashboard-grid">
        <div className="dashboard-card">
          <h3 className="dashboard-card__title">Distribución por Órgano</h3>
          {donorsLoading ? (
            <div className="mini-bar">
              {[1, 2, 3].map((i) => (
                <div key={i} className="mini-bar__item">
                  <div className="skeleton skeleton--text skeleton--w-20" />
                  <div className="mini-bar__track">
                    <div className="mini-bar__fill skeleton" style={{ width: `${40 + i * 15}%`, opacity: 0.3 }} />
                  </div>
                </div>
              ))}
            </div>
          ) : organData.length > 0 ? (
            <MiniBar data={organData} />
          ) : (
            <EmptyState
              title="Sin datos de órganos"
              message="Registre nuevos donantes para visualizar la distribución."
            />
          )}
        </div>

        <div className="dashboard-card">
          <h3 className="dashboard-card__title">Grupos Sanguíneos</h3>
          {donorsLoading ? (
            <div className="mini-bar">
              {[1, 2, 3, 4].map((i) => (
                <div key={i} className="mini-bar__item">
                  <div className="skeleton skeleton--text skeleton--w-15" />
                  <div className="mini-bar__track">
                    <div className="mini-bar__fill skeleton" style={{ width: `${30 + i * 12}%`, opacity: 0.3 }} />
                  </div>
                </div>
              ))}
            </div>
          ) : bloodData.length > 0 ? (
            <MiniBar data={bloodData} color="var(--info)" />
          ) : (
            <EmptyState
              title="Sin datos de sangre"
              message="Registre nuevos donantes para visualizar los grupos sanguíneos."
            />
          )}
        </div>

        <div className="dashboard-card dashboard-card--wide">
          <div className="dashboard-card__header">
            <h3 className="dashboard-card__title">Registro de Actividad</h3>
          </div>
          {logsLoading ? (
            <div className="activity-feed">
              {[1, 2, 3, 4].map((i) => (
                <div key={i} className="activity-feed__item" style={{ opacity: 0.3 }}>
                  <div className="skeleton skeleton--circle" style={{ width: 10, height: 10 }} />
                  <div className="activity-feed__content" style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                    <div className="skeleton skeleton--text skeleton--w-60" />
                    <div className="skeleton skeleton--text skeleton--w-30" />
                  </div>
                </div>
              ))}
            </div>
          ) : allLogs && allLogs.length > 0 ? (
            <ActivityFeed logs={allLogs} />
          ) : (
            <EmptyState
              title="Sin actividad reciente"
              message="El sistema está a la espera de nuevas operaciones."
            />
          )}
        </div>
      </div>
    </div>
  );
}
