import { useState, useEffect } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Menu, Sun, Moon } from 'lucide-react';
import Sidebar from './Sidebar';
import './Layout.css';

const breadcrumbMap = {
  '/dashboard': 'Panel General',
  '/donors': 'Gestión de Donantes',
  '/reservations': 'Reservas de Órganos',
  '/logs': 'Registro de Actividad',
};

export default function Layout() {
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [theme, setTheme] = useState(localStorage.getItem('theme') || 'light');
  const location = useLocation();
  const currentPage = breadcrumbMap[location.pathname] || 'Vitalis';

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('theme', theme);
  }, [theme]);

  const toggleTheme = () => {
    setTheme(theme === 'light' ? 'dark' : 'light');
  };

  return (
    <div className={`layout ${collapsed ? 'layout--collapsed' : ''} ${mobileOpen ? 'layout--mobile-open' : ''}`}>
      <div className="layout__mobile-bar">
        <button className="layout__hamburger" onClick={() => setMobileOpen(!mobileOpen)} aria-label="Menu">
          <Menu size={24} />
        </button>
        <div className="layout__mobile-title">Vitalis</div>
        <button className="layout__mobile-notifications" onClick={toggleTheme} aria-label="Cambiar Tema">
          {theme === 'light' ? <Moon size={20} /> : <Sun size={20} />}
        </button>
      </div>

      <div className="layout__overlay" onClick={() => setMobileOpen(false)} />

      <Sidebar collapsed={collapsed} onToggle={() => setCollapsed((c) => !c)} mobileOpen={mobileOpen} onMobileClose={() => setMobileOpen(false)} />

      <main className="layout__main">
        <header className="layout__topbar">
          <div className="layout__breadcrumb">
            <h1 className="layout__breadcrumb-current">{currentPage}</h1>
          </div>
          <div className="layout__topbar-actions">
            <button className="layout__icon-btn" onClick={toggleTheme} aria-label="Cambiar Tema">
              {theme === 'light' ? <Moon size={20} /> : <Sun size={20} />}
            </button>
          </div>
        </header>

        <div className="layout__content">
          <Outlet />
        </div>
      </main>
    </div>
  );
}

