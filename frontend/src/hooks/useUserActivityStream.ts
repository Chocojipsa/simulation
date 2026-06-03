import { useEffect, useState } from 'react';

export interface UserActivity {
  label: string;
  message: string;
  timestamp: string;
}

export function useUserActivityStream(apiBaseUrl: string, eventId: string | undefined, userId: string | null) {
  const [activities, setActivities] = useState<UserActivity[]>([]);
  const [sseActive, setSseActive] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (error) {
      const timer = window.setTimeout(() => setError(null), 4000);
      return () => window.clearTimeout(timer);
    }
    return undefined;
  }, [error]);

  useEffect(() => {
    if (!eventId || !userId) {
      setActivities([]);
      setSseActive(false);
      return undefined;
    }

    if (typeof EventSource === 'undefined') {
      setSseActive(false);
      return undefined;
    }

    const baseUrl = apiBaseUrl || (typeof window !== 'undefined' ? window.location.origin : 'http://localhost');
    const streamUrl = `${baseUrl}/api/events/${eventId}/participants/${userId}/stream`;
    const eventSource = new EventSource(streamUrl);

    eventSource.onopen = () => {
      setSseActive(true);
      setError(null);
    };

    eventSource.onerror = () => {
      setSseActive(false);
      setError('활동 스트림 연결에 실패했습니다. (SSE)');
    };

    eventSource.addEventListener('activity', (event) => {
      try {
        const activity = JSON.parse(event.data) as UserActivity;
        setActivities((prev) => [activity, ...prev].slice(0, 20));
        setSseActive(true);
      } catch (err) {
        console.error('Failed to parse user activity event', err);
      }
    });

    return () => {
      eventSource.close();
    };
  }, [apiBaseUrl, eventId, userId]);

  return { activities, sseActive, error };
}

