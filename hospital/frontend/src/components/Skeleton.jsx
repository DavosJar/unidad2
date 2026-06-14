import './Skeleton.css';

export function SkeletonCard({ lines = 2 }) {
  return (
    <div className="skeleton-card">
      <div className="skeleton skeleton--circle" />
      <div className="skeleton-card__lines">
        <div className="skeleton skeleton--text skeleton--w-60" />
        <div className="skeleton skeleton--text skeleton--w-80" />
        {lines > 2 && <div className="skeleton skeleton--text skeleton--w-40" />}
      </div>
    </div>
  );
}

export function SkeletonRow() {
  return (
    <div className="skeleton-row">
      <div className="skeleton skeleton--circle skeleton--sm" />
      <div className="skeleton skeleton--text skeleton--w-40" />
      <div className="skeleton skeleton--text skeleton--w-25" />
      <div className="skeleton skeleton--text skeleton--w-15" />
    </div>
  );
}

export function SkeletonTable({ rows = 5 }) {
  return (
    <div className="skeleton-table">
      <div className="skeleton skeleton--text skeleton--w-100 skeleton--header" />
      {Array.from({ length: rows }).map((_, i) => (
        <SkeletonRow key={i} />
      ))}
    </div>
  );
}

export function SkeletonStatCard() {
  return (
    <div className="skeleton-stat">
      <div className="skeleton skeleton--circle skeleton--lg" />
      <div className="skeleton skeleton--text skeleton--w-30" />
      <div className="skeleton skeleton--text skeleton--w-60" />
    </div>
  );
}
