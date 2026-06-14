import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import { AlertCircle, Info, Trash2 } from 'lucide-react';
import './ConfirmDialog.css';

export default function ConfirmDialog({ open, onClose, onConfirm, title, message, confirmLabel = 'Confirmar', cancelLabel = 'Cancelar', variant = 'danger' }) {
  useEffect(() => {
    const handle = (e) => { if (e.key === 'Escape') onClose(); };
    if (open) {
      window.addEventListener('keydown', handle);
    }
    return () => {
      window.removeEventListener('keydown', handle);
    };
  }, [open, onClose]);

  if (!open) return null;

  const Icon = variant === 'danger' ? Trash2 : variant === 'warning' ? AlertCircle : Info;

  return createPortal(
    <div className="confirm__overlay" onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="confirm__dialog">
        <div className={`confirm__icon-wrapper confirm__icon-wrapper--${variant}`}>
          <Icon size={32} />
        </div>
        <h3 className="confirm__title">{title}</h3>
        <p className="confirm__message">{message}</p>
        <div className="confirm__actions">
          <button className="btn btn--ghost confirm__btn" onClick={onClose}>{cancelLabel}</button>
          <button
            className={`btn btn--${variant === 'danger' ? 'danger' : 'primary'} confirm__btn`}
            onClick={() => { onConfirm(); onClose(); }}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>,
    document.body
  );
}

