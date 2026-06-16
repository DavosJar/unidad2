import { useState } from 'react';
import { useApi } from '../hooks/useApi';
import { reservations as reservationsApi, donors as donorsApi } from '../api/hospital';
import { useToast } from '../context/ToastContext';
import Modal from '../components/Modal';
import { SkeletonCard } from '../components/Skeleton';
import EmptyState from '../components/EmptyState';
import { formatTimestamp } from '../utils/formatDate';
import { CalendarPlus, CalendarX, ChevronDown, ChevronUp, User } from 'lucide-react';
import './Reservations.css';

const initialForm = { idDonante: '', paciente: '' };

export default function Reservations() {
  const { data: reservations, loading, refetch } = useApi(() => reservationsApi.list());
  const { data: donors, loading: donorsLoading } = useApi(() => donorsApi.available());
  const toast = useToast();

  const [modalOpen, setModalOpen] = useState(false);
  const [formData, setFormData] = useState(initialForm);
  const [formErrors, setFormErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [expanded, setExpanded] = useState(null);

  function validateForm() {
    const errors = {};
    if (!formData.idDonante) errors.idDonante = 'Debe seleccionar un donante';
    if (!formData.paciente.trim()) errors.paciente = 'Ingrese el nombre del paciente';
    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleCreate(e) {
    e.preventDefault();
    if (!validateForm()) return;
    setSubmitting(true);
    try {
      await reservationsApi.create({
        idDonante: Number(formData.idDonante),
        paciente: formData.paciente.trim(),
      });
      setModalOpen(false);
      setFormData(initialForm);
      setFormErrors({});
      toast('Reserva creada exitosamente', 'success');
      refetch();
    } catch (err) {
      const msgs = {
        423: 'El sistema no tiene el token de exclusión mutua en este momento. Espere unos segundos y vuelva a intentar.',
        409: 'El donante seleccionado ya fue reservado por otro nodo.',
        404: 'Donante no encontrado. Puede que haya sido eliminado del sistema.',
      };
      toast(msgs[err.status] || err.message || 'Error al crear la reserva', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  function openModal() {
    setFormData(initialForm);
    setFormErrors({});
    setModalOpen(true);
  }

  function getDonorName(idDonante) {
    if (!donors) return `ID ${idDonante}`;
    const found = donors.find((d) => d.id === idDonante);
    return found ? found.nombre : `Donante ${idDonante}`;
  }

  return (
    <div className="page-enter">
      <div className="page-header">
        <div>
          <h1 className="page-title">Sistema de Reservas</h1>
          <p className="page-subtitle">
            {reservations ? `Total de reservas activas: ${reservations.length}` : 'Cargando datos...'}
          </p>
        </div>
        <button className="btn btn--primary" onClick={openModal}>
          <CalendarPlus size={18} />
          Nueva Reserva
        </button>
      </div>

      {loading ? (
        <div className="reservations-grid">
          {[1, 2, 3].map((i) => <SkeletonCard key={i} lines={3} />)}
        </div>
      ) : !reservations || reservations.length === 0 ? (
        <EmptyState
          icon={<CalendarX size={32} />}
          title="Sin reservas activas"
          message="No se han encontrado reservas en el sistema."
          actionLabel="Crear Reserva"
          onAction={openModal}
        />
      ) : (
        <div className="reservations-grid stagger">
          {reservations.map((r, i) => (
            <div
              key={r.id || i}
              className={`reservation-card ${expanded === r.id ? 'reservation-card--expanded' : ''}`}
            >
              <div className="reservation-card__header" onClick={() => setExpanded(expanded === r.id ? null : r.id)}>
                <div className="reservation-card__avatar">
                  {r.nombrePaciente ? r.nombrePaciente.charAt(0).toUpperCase() : '?'}
                </div>
                <div className="reservation-card__info">
                  <span className="reservation-card__patient">{r.nombrePaciente || 'Paciente'}</span>
                  <span className="reservation-card__donor">
                    <User size={14} className="icon-inline" /> 
                    Donante: {getDonorName(r.idDonante)}
                  </span>
                </div>
                <button className="reservation-card__arrow" aria-label="Expandir">
                  {expanded === r.id ? <ChevronUp size={20} /> : <ChevronDown size={20} />}
                </button>
              </div>
              {expanded === r.id && (
                <div className="reservation-card__body">
                  <div className="reservation-card__detail">
                    <span className="reservation-card__detail-label">Donante Asignado</span>
                    <span className="reservation-card__detail-value">{getDonorName(r.idDonante)}</span>
                  </div>
                  <div className="reservation-card__detail">
                    <span className="reservation-card__detail-label">Fecha de Creación</span>
                    <span className="reservation-card__detail-value">
                      {formatTimestamp(r.timestamp) || '—'}
                    </span>
                  </div>
                  <div className="reservation-card__detail">
                    <span className="reservation-card__detail-label">Paciente Receptor</span>
                    <span className="reservation-card__detail-value">{r.nombrePaciente}</span>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Crear Nueva Reserva">
        <form onSubmit={handleCreate} className="reservation-form" noValidate>
          <div className="form-group">
            <label className="form-label">Seleccionar Donante Disponible</label>
            <select
              className={`form-input ${formErrors.idDonante ? 'form-input--error' : ''}`}
              value={formData.idDonante}
              onChange={(e) => { setFormData({ ...formData, idDonante: e.target.value }); if (formErrors.idDonante) setFormErrors({ ...formErrors, idDonante: '' }); }}
            >
              <option value="">Seleccione un donante</option>
              {(donors || []).map((d) => (
                <option key={d.id} value={d.id}>
                  {d.nombre} • {d.organo} (Tipo: {d.tipoSangre})
                </option>
              ))}
            </select>
            {formErrors.idDonante && <span className="form-field-error">{formErrors.idDonante}</span>}
            {!donorsLoading && donors && donors.length === 0 && (
              <span className="form-field-hint">No hay donantes disponibles en este momento.</span>
            )}
          </div>
          <div className="form-group">
            <label className="form-label">Nombre del Paciente Receptor</label>
            <input
              type="text"
              className={`form-input ${formErrors.paciente ? 'form-input--error' : ''}`}
              placeholder="Ej. María Gómez"
              value={formData.paciente}
              onChange={(e) => { setFormData({ ...formData, paciente: e.target.value }); if (formErrors.paciente) setFormErrors({ ...formErrors, paciente: '' }); }}
              autoFocus
            />
            {formErrors.paciente && <span className="form-field-error">{formErrors.paciente}</span>}
          </div>
          <button type="submit" className="btn btn--primary btn--full" disabled={submitting || (donors && donors.length === 0)}>
            {submitting ? 'Procesando...' : 'Confirmar Reserva'}
          </button>
        </form>
      </Modal>
    </div>
  );
}
