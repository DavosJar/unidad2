import { NavLink } from 'react-router-dom';
import { LayoutDashboard, Users, Calendar, Activity, ChevronLeft, ChevronRight, X, HeartPulse } from 'lucide-react';
import './Sidebar.css';

const links = [
  { to: '/dashboard', label: 'Panel', icon: LayoutDashboard },
  { to: '/donors', label: 'Donantes', icon: Users },
  { to: '/reservations', label: 'Reservas', icon: Calendar },
  { to: '/logs', label: 'Actividad', icon: Activity },
];

export default function Sidebar({ collapsed, onToggle, mobileOpen, onMobileClose }) {
  return (
    <aside className={`sidebar ${collapsed ? 'sidebar--collapsed' : ''} ${mobileOpen ? 'sidebar--mobile-open' : ''}`}>
      <div className="sidebar__header">
        <div className="sidebar__logo">
          <HeartPulse className="sidebar__logo-icon" size={28} />
          {!collapsed && <span className="sidebar__logo-text">Vitalis</span>}
        </div>
        <button className="sidebar__toggle" onClick={onToggle} aria-label={collapsed ? 'Expandir' : 'Contraer'}>
          {collapsed ? <ChevronRight size={18} /> : <ChevronLeft size={18} />}
        </button>
        <button className="sidebar__close-mobile" onClick={onMobileClose} aria-label="Cerrar menú">
          <X size={24} />
        </button>
      </div>

      <nav className="sidebar__nav">
        {links.map((link) => {
          const Icon = link.icon;
          return (
            <NavLink
              key={link.to}
              to={link.to}
              end
              className={({ isActive }) =>
                `sidebar__link ${isActive ? 'sidebar__link--active' : ''}`
              }
              onClick={onMobileClose}
            >
              <Icon className="sidebar__link-icon" size={20} />
              {!collapsed && <span className="sidebar__link-label">{link.label}</span>}
            </NavLink>
          );
        })}
      </nav>

      <div className="sidebar__footer">
        <div className="sidebar__user">
          <div className="sidebar__avatar">
            <img src="https://fotografias.lasexta.com/clipping/cmsimages02/2022/10/17/4662F1D2-5009-4282-9B95-70AB974F6BA6/muere-mama-coco-109-anos-mujer-que-guardaba-gran-parecido-abuela-pelicula-pixar_160.jpg?crop=1436,808,x484,y0&width=544&height=306&optimize=low&format=webply" alt="Admin" />
          </div>
          {!collapsed && (
            <div className="sidebar__user-info">
              <span className="sidebar__user-name">Dr. Admin</span>
              <span className="sidebar__user-role">Sistema Vitalis</span>
            </div>
          )}
        </div>
      </div>
    </aside>
  );
}
