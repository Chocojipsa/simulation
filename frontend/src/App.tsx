import { Routes, Route } from 'react-router-dom';
import Dashboard from './Dashboard';
import { TicketingWindow } from './components/TicketingWindow';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Dashboard />} />
      <Route path="/ticketing/:eventId" element={<TicketingWindow />} />
    </Routes>
  );
}
