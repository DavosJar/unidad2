import './EmptyState.css';

export default function EmptyState({ icon, title, message, action, onAction, actionLabel }) {
  return (
    <div className="empty-state">
      {icon && <div className="empty-state__icon">{icon}</div>}
      <h3 className="empty-state__title">{title}</h3>
      <p className="empty-state__message">{message}</p>
      {actionLabel && onAction && (
        <button className="btn btn--primary empty-state__action" onClick={onAction}>
          {actionLabel}
        </button>
      )}
    </div>
  );
}

