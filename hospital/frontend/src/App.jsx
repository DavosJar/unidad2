import { Routes, Route, Navigate } from 'react-router-dom';
import { ToastProvider } from './context/ToastContext';
import Layout from './components/Layout';
import Dashboard from './pages/Dashboard';
import Donors from './pages/Donors';
import Reservations from './pages/Reservations';
import Logs from './pages/Logs';

export default function App() {
  return (
    <ToastProvider>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/donors" element={<Donors />} />
          <Route path="/reservations" element={<Reservations />} />
          <Route path="/logs" element={<Logs />} />
        </Route>
      </Routes>
    </ToastProvider>
  );
}
