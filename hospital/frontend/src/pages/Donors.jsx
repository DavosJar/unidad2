import { useState, useMemo, useRef, useCallback, useEffect } from 'react';
import { useApi } from '../hooks/useApi';
import { donors as donorsApi } from '../api/hospital';
import { useToast } from '../context/ToastContext';
import Modal from '../components/Modal';
import ConfirmDialog from '../components/ConfirmDialog';
import { SkeletonCard } from '../components/Skeleton';
import EmptyState from '../components/EmptyState';
import { Plus, Search, X as CloseIcon, Droplets, Heart } from 'lucide-react';
import './Donors.css';

const organos = ['Corazón', 'Riñón', 'Hígado', 'Pulmón', 'Páncreas', 'Córnea'];
const tiposSangre = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];

const availabilityColors = {
  true: { bg: 'var(--success-bg)', text: 'var(--success)', label: 'Disponible', border: 'var(--success-border)' },
  false: { bg: 'var(--warning-bg)', text: 'var(--warning)', label: 'Reservado', border: 'var(--warning-border)' },
};

function DonorRow({ donor, onReserve, onRelease }) {
  const status = availabilityColors[donor.disponible];

  return (
    <div className="donor-row">
      <div className="donor-row__avatar">
        {donor.nombre ? donor.nombre.charAt(0).toUpperCase() : '?'}
      </div>
      <div className="donor-row__info">
        <span className="donor-row__name">{donor.nombre || 'Sin Nombre'}</span>
        <div className="donor-row__meta">
          {donor.organo && (
            <span className="donor-row__meta-item">
              <Heart size={14} />
              {donor.organo}
            </span>
          )}
          {donor.tipoSangre && (
            <span className="donor-row__meta-item">
              <Droplets size={14} />
              {donor.tipoSangre}
            </span>
          )}
        </div>
      </div>
      <div className="donor-row__badges">
        <span className="badge" style={{ background: status.bg, color: status.text, borderColor: status.border }}>
          {status.label}
        </span>
      </div>
      <div className="donor-row__actions">
        {donor.disponible ? (
          <button className="btn btn--small btn--primary" onClick={() => onReserve(donor)}>
            Reservar
          </button>
        ) : (
          <button className="btn btn--small btn--ghost" onClick={() => onRelease(donor)}>
            Liberar
          </button>
        )}
      </div>
    </div>
  );
}

const initialForm = { nombre: '', tipoSangre: '', organo: '' };

export default function Donors() {
  const { data: donors, loading, refetch } = useApi(() => donorsApi.list());
  const toast = useToast();

  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const searchTimer = useRef(null);

  const [filterOrgan, setFilterOrgan] = useState('');
  const [filterBlood, setFilterBlood] = useState('');
  const [filterAvail, setFilterAvail] = useState('');

  const [modalOpen, setModalOpen] = useState(false);
  const [formData, setFormData] = useState(initialForm);
  const [formErrors, setFormErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  const [confirmAction, setConfirmAction] = useState(null);

  useEffect(() => {
    return () => clearTimeout(searchTimer.current);
  }, []);

  const handleSearch = useCallback((value) => {
    setSearch(value);
    clearTimeout(searchTimer.current);
    searchTimer.current = setTimeout(() => setDebouncedSearch(value), 300);
  }, []);

  const filtered = useMemo(() => {
    if (!donors) return [];
    return donors.filter((d) => {
      if (debouncedSearch && !d.nombre?.toLowerCase().includes(debouncedSearch.toLowerCase())) return false;
      if (filterOrgan && d.organo !== filterOrgan) return false;
      if (filterBlood && d.tipoSangre !== filterBlood) return false;
      if (filterAvail === 'disponible' && !d.disponible) return false;
      if (filterAvail === 'reservado' && d.disponible) return false;
      return true;
    });
  }, [donors, debouncedSearch, filterOrgan, filterBlood, filterAvail]);

  async function handleReserve(donor) {
    try {
      await donorsApi.reserve(donor.id);
      toast(`Donante ${donor.nombre} reservado`, 'success');
      refetch();
    } catch (e) {
      toast(e.message || 'Error al reservar', 'error');
    }
  }

  async function handleRelease(donor) {
    try {
      await donorsApi.release(donor.id);
      toast(`Donante ${donor.nombre} liberado`, 'success');
      refetch();
    } catch (e) {
      toast(e.message || 'Error al liberar', 'error');
    }
  }

  function validateForm() {
    const errors = {};
    if (!formData.nombre.trim()) errors.nombre = 'Este campo es obligatorio';
    if (!formData.tipoSangre) errors.tipoSangre = 'Seleccione un tipo de sangre';
    if (!formData.organo) errors.organo = 'Seleccione un órgano';
    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleCreate(e) {
    e.preventDefault();
    if (!validateForm()) return;
    setSubmitting(true);
    try {
      await donorsApi.create(formData);
      setModalOpen(false);
      setFormData(initialForm);
      setFormErrors({});
      toast('Nuevo donante registrado exitosamente', 'success');
      refetch();
    } catch (e) {
      toast(e.message || 'Error al registrar donante', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  function openCreateModal() {
    setFormData(initialForm);
    setFormErrors({});
    setModalOpen(true);
  }

  return (
    <div className="page-enter">
      <div className="page-header">
        <div>
          <h1 className="page-title">Gestión de Donantes</h1>
          <p className="page-subtitle">
            {donors ? `Mostrando ${filtered.length} de ${donors.length} donantes registrados` : 'Cargando datos...'}
          </p>
        </div>
        <button className="btn btn--primary" onClick={openCreateModal}>
          <Plus size={18} />
          Nuevo Donante
        </button>
      </div>

      <div className="filters stagger">
        <div className="filter-group">
          <Search size={18} className="filter-group__icon" />
          <input
            type="text"
            className="filter-input"
            placeholder="Buscar por nombre..."
            value={search}
            onChange={(e) => handleSearch(e.target.value)}
          />
          {search && (
            <button className="filter-group__clear" onClick={() => { setSearch(''); setDebouncedSearch(''); }} aria-label="Limpiar búsqueda">
              <CloseIcon size={16} />
            </button>
          )}
        </div>
        <select className="filter-select" value={filterOrgan} onChange={(e) => setFilterOrgan(e.target.value)}>
          <option value="">Todos los órganos</option>
          {organos.map((o) => <option key={o} value={o}>{o}</option>)}
        </select>
        <select className="filter-select" value={filterBlood} onChange={(e) => setFilterBlood(e.target.value)}>
          <option value="">Cualquier tipo de sangre</option>
          {tiposSangre.map((t) => <option key={t} value={t}>{t}</option>)}
        </select>
        <select className="filter-select" value={filterAvail} onChange={(e) => setFilterAvail(e.target.value)}>
          <option value="">Cualquier estado</option>
          <option value="disponible">Disponibles</option>
          <option value="reservado">Reservados</option>
        </select>
      </div>

      {loading ? (
        <div className="donor-list">
          {[1, 2, 3, 4, 5].map((i) => <SkeletonCard key={i} lines={2} />)}
        </div>
      ) : filtered.length === 0 ? (
        <EmptyState
          icon={<Search size={32} />}
          title={donors ? "No se encontraron resultados" : "Base de datos vacía"}
          message={donors ? "Intenta ajustar los filtros de búsqueda." : "No hay donantes registrados en el sistema."}
          actionLabel={donors ? null : "Registrar Primer Donante"}
          onAction={openCreateModal}
        />
      ) : (
        <div className="donor-list stagger">
          {filtered.map((donor) => (
            <DonorRow
              key={donor.id}
              donor={donor}
              onReserve={(d) => setConfirmAction({ type: 'reserve', donor: d })}
              onRelease={(d) => setConfirmAction({ type: 'release', donor: d })}
            />
          ))}
        </div>
      )}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Registrar Donante">
        <form onSubmit={handleCreate} className="donor-form" noValidate>
          <div className="form-group">
            <label className="form-label">Nombre del Donante</label>
            <input
              type="text"
              className={`form-input ${formErrors.nombre ? 'form-input--error' : ''}`}
              placeholder="Ej. Juan Pérez"
              value={formData.nombre}
              onChange={(e) => { setFormData({ ...formData, nombre: e.target.value }); if (formErrors.nombre) setFormErrors({ ...formErrors, nombre: '' }); }}
              autoFocus
            />
            {formErrors.nombre && <span className="form-field-error">{formErrors.nombre}</span>}
          </div>
          <div className="form-group">
            <label className="form-label">Tipo de Sangre</label>
            <select
              className={`form-input ${formErrors.tipoSangre ? 'form-input--error' : ''}`}
              value={formData.tipoSangre}
              onChange={(e) => { setFormData({ ...formData, tipoSangre: e.target.value }); if (formErrors.tipoSangre) setFormErrors({ ...formErrors, tipoSangre: '' }); }}
            >
              <option value="">Seleccionar tipo</option>
              {tiposSangre.map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
            {formErrors.tipoSangre && <span className="form-field-error">{formErrors.tipoSangre}</span>}
          </div>
          <div className="form-group">
            <label className="form-label">Órgano a Donar</label>
            <select
              className={`form-input ${formErrors.organo ? 'form-input--error' : ''}`}
              value={formData.organo}
              onChange={(e) => { setFormData({ ...formData, organo: e.target.value }); if (formErrors.organo) setFormErrors({ ...formErrors, organo: '' }); }}
            >
              <option value="">Seleccionar órgano</option>
              {organos.map((o) => <option key={o} value={o}>{o}</option>)}
            </select>
            {formErrors.organo && <span className="form-field-error">{formErrors.organo}</span>}
          </div>
          <button type="submit" className="btn btn--primary btn--full" disabled={submitting}>
            {submitting ? 'Procesando...' : 'Registrar Donante'}
          </button>
        </form>
      </Modal>

      <ConfirmDialog
        open={confirmAction?.type === 'reserve'}
        onClose={() => setConfirmAction(null)}
        onConfirm={() => { handleReserve(confirmAction.donor); setConfirmAction(null); }}
        title="Reservar Donante"
        message={`¿Estás seguro que deseas reservar al donante ${confirmAction?.donor?.nombre}? Su estado pasará a reservado y no estará disponible para otros pacientes.`}
        confirmLabel="Confirmar Reserva"
        variant="primary"
      />

      <ConfirmDialog
        open={confirmAction?.type === 'release'}
        onClose={() => setConfirmAction(null)}
        onConfirm={() => { handleRelease(confirmAction.donor); setConfirmAction(null); }}
        title="Liberar Donante"
        message={`¿Deseas liberar al donante ${confirmAction?.donor?.nombre}? Volverá a estar disponible en el sistema.`}
        confirmLabel="Liberar Donante"
        variant="warning"
      />
    </div>
  );
}

