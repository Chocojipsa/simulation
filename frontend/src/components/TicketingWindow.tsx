import { useParams } from 'react-router-dom';

export function TicketingWindow() {
  const { eventId } = useParams<{ eventId: string }>();
  return <div>Ticketing Window Placeholder for Event: {eventId}</div>;
}

