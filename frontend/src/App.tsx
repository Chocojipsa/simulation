import { Routes, Route, Navigate } from 'react-router-dom';
import Dashboard from './Dashboard';
import { TicketingWindow } from './components/TicketingWindow';
import { MonitoringConsole } from './components/MonitoringConsole';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Dashboard />} />
      <Route path="/monitoring" element={<MonitoringConsole />} />
      <Route path="/ticketing/:eventId" element={<TicketingWindow />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

